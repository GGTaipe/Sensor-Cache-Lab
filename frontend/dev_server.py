#!/usr/bin/env python3
"""
Servidor de desarrollo para el front (solo biblioteca estándar de Python 3).

  python dev_server.py                # estáticos en :5173  → el navegador llama a :8080 (CORS)
  python dev_server.py --proxy        # además reenvía /api/* al backend → un solo origen (sin CORS)

Con --proxy abre:  http://localhost:5173/?api=
(el parámetro vacío le dice al front que use rutas relativas: /api/...)
"""
import argparse
import http.server
import pathlib
import urllib.error
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent
HOP_BY_HOP = {"connection", "keep-alive", "transfer-encoding", "content-length", "server", "date"}


class Handler(http.server.SimpleHTTPRequestHandler):
    backend = None  # p. ej. "http://localhost:8080" cuando se usa --proxy

    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(ROOT), **kwargs)

    # ---- proxy -------------------------------------------------------
    def _forward(self):
        length = int(self.headers.get("Content-Length") or 0)
        data = self.rfile.read(length) if length else None
        req = urllib.request.Request(self.backend + self.path, data=data, method=self.command)
        # Solo se reenvían cabeceras "neutras". NO se reenvía Origin: para el backend la
        # petición es servidor-a-servidor y el navegador nunca ve un origen distinto.
        for name in ("Accept", "Content-Type"):
            if self.headers.get(name):
                req.add_header(name, self.headers[name])
        try:
            resp = urllib.request.urlopen(req, timeout=120)
        except urllib.error.HTTPError as err:  # 4xx/5xx también son respuestas válidas
            resp = err
        except urllib.error.URLError as err:
            self.send_error(502, f"Backend no disponible: {err.reason}")
            return

        body = resp.read()
        self.send_response(resp.status if hasattr(resp, "status") else resp.code)
        for key, value in resp.headers.items():
            if key.lower() not in HOP_BY_HOP:
                self.send_header(key, value)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _is_api(self):
        return self.backend is not None and self.path.startswith("/api/")

    def do_GET(self):
        self._forward() if self._is_api() else super().do_GET()

    def do_DELETE(self):
        if self._is_api():
            self._forward()
        else:
            self.send_error(405, "Método no permitido (¿falta --proxy?)")

    def end_headers(self):
        # El front es de desarrollo: evita que el navegador cachee JS/CSS/HTML antiguos.
        self.send_header("Cache-Control", "no-store")
        super().end_headers()

    def log_message(self, fmt, *args):
        tag = "proxy" if self._is_api() else "static"
        print(f"[{tag}] {self.address_string()} {fmt % args}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--port", type=int, default=5173)
    parser.add_argument("--proxy", action="store_true", help="reenviar /api/* al backend")
    parser.add_argument("--backend", default="http://localhost:8080")
    args = parser.parse_args()

    Handler.backend = args.backend.rstrip("/") if args.proxy else None
    server = http.server.ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    print(f"Front en http://localhost:{args.port}/" + ("?api=   (proxy → " + Handler.backend + ")" if args.proxy else ""))
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nBye")


if __name__ == "__main__":
    main()
