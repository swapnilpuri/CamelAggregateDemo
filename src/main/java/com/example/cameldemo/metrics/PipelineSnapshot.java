package com.example.cameldemo.metrics;

import java.util.Map;

/**
 * Full pipeline state snapshot emitted to the SSE stream every 500 ms.
 */
public record PipelineSnapshot(
        String pipelineStatus,              // IDLE | RUNNING | COMPLETED
        long   startTimeMs,                 // epoch ms when pipeline last started, 0 if idle
        long   elapsedMs,
        long   recordsFetchedFromOracle,    // Route 2 exchanges × batch size
        long   recordsTransformed,          // Route 3 exchanges completed
        long   recordsInsertedToKinetica,   // Route 4 X-Batch-Size header sum
        Map<String, RouteMetricSnapshot>  routes,
        Map<String, QueueMetricSnapshot>  queues
) {}
