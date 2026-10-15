package com.example.sensorlab.dto;

import java.util.List;

public record DatasetInfo(boolean ready, long totalReadings, long seedMs, List<String> stations) {
}
