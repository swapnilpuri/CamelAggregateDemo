package com.example.cameldemo.metrics;

import java.util.List;

/**
 * Immutable per-route metrics snapshot serialised to the SSE stream.
 *
 * throughputHistory: the last 30 per-second throughput readings (0.5 s ticks),
 * newest value last.  Clients render this as a sparkline or area chart.
 */
public record RouteMetricSnapshot(
        String routeId,
        String status,                   // IDLE | RUNNING | COMPLETED | FAILED
        long   exchangesTotal,
        long   exchangesFailed,
        long   lastProcessingTimeMs,
        double meanProcessingTimeMs,
        double throughputPerSec,
        List<Double> throughputHistory   // last ~15 s of half-second readings
) {}
