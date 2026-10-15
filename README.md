# Sensor Cache Lab

Laboratorio de **Spring Boot + caché Caffeine** que mide cuánto tarda leer un gran volumen de registros desde una base de datos frente a leerlos desde memoria.

> **Enlace al repositorio:** https://github.com/TU_USUARIO/sensor-cache-lab  *(reemplazar)*

## ¿Qué se mide?

Una base H2 en memoria con **360.000 lecturas horarias** de 12 estaciones meteorológicas (30.000 por estación). Hay dos escenarios con perfiles opuestos:

| Escenario | Trabajo de la BD | Tamaño del resultado | Qué demuestra |
|---|---|---|---|
| **Serie de una estación** | Leer ~30.000 filas | Lista grande | El coste de transferir y mapear muchas filas |
| **Reporte mensual global** | Agrupar las 360.000 filas | ~500 filas | El coste de calcular; el resultado cacheado es diminuto |

Cada consulta se puede ejecutar en modo `db` (siempre a la BD) o `cache` (pasa por `@Cacheable`). El tiempo se mide **en el servidor** con `System.nanoTime()` alrededor de la llamada al servicio, y el front añade el tiempo de ida y vuelta del navegador.

## Estructura

```
sensor-cache-lab/
├── backend/                       Java 17 · Spring Boot 3.3 · JPA · H2 · Caffeine
│   └── src/main/java/com/example/sensorlab/
│       ├── config/      CacheConfig · DevCorsConfig · DatasetSeeder
│       ├── domain/      Reading (entidad) · Station · StationCatalog
│       ├── dto/         records inmutables devueltos por la API
│       ├── repository/  ReadingRepository (JPQL con proyección a DTO)
│       ├── service/     ReadingDataService (BD) · CachedReadingService (caché) · LabService (medición)
│       └── web/         LabController
├── frontend/                      HTML + CSS + JS sin dependencias
│   ├── index.html · styles.css · app.js
│   └── dev_server.py              servidor estático con proxy opcional
└── docs/                          capturas de pantalla
```

### Decisiones de diseño

- **Caché como decorador.** `CachedReadingService` envuelve a `ReadingDataService`. Así el `@Cacheable` siempre pasa por el proxy de Spring (sin el problema de auto-invocación) y el código de acceso a datos queda sin anotaciones de caché.
- **DTO inmutables en caché.** Se guardan `record`s, no entidades JPA, para no cachear objetos gestionados por una sesión.
- **Dos cachés con políticas distintas** (`CacheConfig`): lecturas (máx. 6 entradas, 5 min) y reporte (1 entrada, 15 min); ambas con `recordStats()` para exponer aciertos/fallos reales.
- **`sync = true`** evita que varias peticiones simultáneas con la caché vacía consulten la BD a la vez.
- **H2 en memoria:** nada de archivos de datos en el repositorio; los datos se regeneran en cada arranque (~segundos).

## API

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/status` | Estado de la carga de datos y lista de estaciones |
| GET | `/api/readings/{station}?mode=db\|cache&sample=20` | Lee toda la serie de una estación |
| GET | `/api/report/monthly?mode=db\|cache&sample=20` | Agregado mensual de todas las lecturas |
| GET | `/api/lab/duel?scenario=readings\|report&station=QUITO&rounds=6` | N consultas a BD + N con caché, con todas las mediciones |
| GET | `/api/cache/stats` | Aciertos, fallos y tasa de acierto por caché |
| DELETE | `/api/cache` | Vacía las cachés |

Las respuestas incluyen `X-Data-Source` (`DATABASE`, `CACHE_MISS`, `CACHE_HIT`) y `Server-Timing`, visibles también en DevTools → Network → Timing.

## Ejecución

Requisitos: JDK 17+, Maven 3.9+ y Python 3 (solo para servir el front).

```bash
# 1) Backend → http://localhost:8080
cd backend
mvn spring-boot:run

# 2) Front → http://localhost:5173   (otra terminal)
cd frontend
python dev_server.py          # en Windows: python  /  en Linux/macOS: python3
```

Pruebas automáticas (usan 200 filas por estación): `cd backend && mvn test`.

> No abras `index.html` con doble clic (`file://`): el origen sería `null` y el backend lo rechazaría.

## CORS: el problema y las soluciones en desarrollo

**El problema.** Un *origen* es `esquema + host + puerto`. El front en `http://localhost:5173` y la API en `http://localhost:8080` son orígenes distintos, así que la *same-origin policy* impide que el JavaScript lea la respuesta. En la consola aparece:

```
Access to fetch at 'http://localhost:8080/api/status' from origin 'http://localhost:5173'
has been blocked by CORS policy: No 'Access-Control-Allow-Origin' header is present...
```

Importante: el servidor **sí procesa** la petición; es el navegador quien descarta la respuesta. Para métodos como `DELETE` el navegador envía antes un *preflight* `OPTIONS` y solo continúa si el servidor lo autoriza.

**Soluciones probadas en este proyecto**

| # | Solución | Dónde está | Cuándo conviene |
|---|---|---|---|
| 1 | **Filtro CORS en Spring, solo en el perfil `dev`** | `config/DevCorsConfig.java` + `lab.cors.origins` | Front y API separados. En `prod` el bean no existe y no hay CORS abierto |
| 2 | **Proxy del servidor de desarrollo** (un solo origen) | `frontend/dev_server.py --proxy` | Frontends con bundler; el navegador nunca ve dos orígenes |

Para probar la 2: `python dev_server.py --proxy` y abrir `http://localhost:5173/?api=`.

**Otras opciones:** `@CrossOrigin` en un controlador (casos puntuales); servir el front desde `src/main/resources/static` (mismo origen); un reverse proxy como Nginx en producción.

**Detalles que suelen olvidarse**

- `exposedHeaders`: JavaScript solo puede leer unas pocas cabeceras de una respuesta cross-origin. Sin `Access-Control-Expose-Headers`, `res.headers.get('X-Data-Source')` devuelve `null`. El panel «Diagnóstico CORS» del front lo comprueba.
- Con `allowCredentials(true)` no se puede usar `"*"`; hay que listar los orígenes.
- Nunca dejar `allowedOrigins("*")` en producción.

**Experimento sugerido (para el informe):** vacía `lab.cors.origins`, reinicia y observa el error; luego restáuralo y captura el preflight en DevTools → Network.

## Resultados

> Completa la tabla con **tus** mediciones (botón «Duelo», 6 repeticiones). Los tiempos dependen del equipo.

| Escenario | Registros leídos | BD (mediana) | Caché · 1.er acceso (fallo) | Caché (mediana de aciertos) | Aceleración |
|---|---|---|---|---|---|
| Serie de QUITO | 30.000 | ___ ms | ___ ms | ___ ms | ___ × |
| Reporte mensual global | 360.000 → ~500 | ___ ms | ___ ms | ___ ms | ___ × |

Captura de pantalla del duelo: `docs/duelo.png`

### Qué analizar

- ¿Por qué el primer acceso con caché cuesta lo mismo que la BD (o algo más)?
- ¿En qué escenario la caché da mayor aceleración y por qué? (pista: qué trabajo se evita en cada caso)
- Se usa la **mediana** y no solo el promedio: la primera consulta incluye el calentamiento de la JVM y distorsiona la media.
- H2 corre en el mismo proceso. Con PostgreSQL o MySQL remotos la latencia de red haría la diferencia mucho mayor.
- Riesgo de la caché: **datos obsoletos**. Aquí caducan por tiempo (`expireAfterWrite`) y se pueden vaciar a mano; con escrituras reales habría que invalidar con `@CacheEvict`.
- Memoria: una entrada de 30.000 DTOs ocupa varios MB, por eso el límite de entradas.

## Prompt usado para generar el front con IA

> Genera un front con HTML, CSS y JavaScript sin librerías ni CDN para mi API REST de Spring Boot en `http://localhost:8080`. Endpoints: `GET /api/status`, `GET /api/readings/{station}?mode=db|cache`, `GET /api/report/monthly?mode=db|cache`, `GET /api/lab/duel?scenario=&station=&rounds=`, `GET /api/cache/stats` y `DELETE /api/cache`. Quiero elegir escenario y estación, lanzar consultas individuales y un «duelo», ver tiempo de servidor y de ida y vuelta, un gráfico SVG en escala logarítmica con cada repetición (marcando el primer fallo de caché), la tabla de estadísticas de la caché, un historial y un panel de diagnóstico CORS que indique si front y API comparten origen y si las cabeceras `X-Data-Source` y `Server-Timing` son legibles. Si fetch falla, explica las posibles causas (backend apagado o CORS).

## Subir a GitHub

```bash
cd sensor-cache-lab
git init
git add .
git commit -m "Laboratorio de caché con Spring Boot y front de comparación"
git branch -M main
git remote add origin https://github.com/TU_USUARIO/sensor-cache-lab.git
git push -u origin main
```

(Crea antes el repositorio vacío en github.com y pega el enlace en la consigna y al inicio de este README.)
