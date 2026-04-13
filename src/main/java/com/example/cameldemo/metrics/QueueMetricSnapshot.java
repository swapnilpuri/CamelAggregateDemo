package com.example.cameldemo.metrics;

/**
 * Immutable SEDA queue depth snapshot.
 */
public record QueueMetricSnapshot(
        String queueId,
        int    currentDepth,
        int    capacity,
        int    highWaterMark,
        double percentFull      // currentDepth / capacity * 100
) {}
