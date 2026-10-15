package com.example.sensorlab.dto;

public record CacheStatsView(String name, long hits, long misses, double hitRate,
                             long evictions, long entries) {
}
