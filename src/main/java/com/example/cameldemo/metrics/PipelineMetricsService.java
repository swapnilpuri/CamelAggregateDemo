package com.example.cameldemo.metrics;

import org.apache.camel.CamelContext;
import org.apache.camel.component.seda.SedaEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central in-memory store for all pipeline metrics.
 *
 * Updated from two sources:
 *   1. CamelMetricsEventNotifier  — exchange-level events (completions, failures)
 *   2. @Scheduled tick()          — SEDA queue depth polling + throughput computation
 *
 * Produces PipelineSnapshot every 500 ms for the SSE broadcaster.
 */
@Service
public class PipelineMetricsService {

    private static final Logger log = LoggerFactory.getLogger(PipelineMetricsService.class);

    // ---------------------------------------------------------------
    // Queue configuration (queueId → capacity)
    // ---------------------------------------------------------------
    private static final Map<String, Integer> QUEUE_CAPACITIES = Map.of(
            "batchQueue",     200,
            "transformQueue", 5000,
            "insertQueue",    5000
    );

    // ---------------------------------------------------------------
    // Route state
    // ---------------------------------------------------------------
    private final Map<String, RouteStats> routeStats = new ConcurrentHashMap<>();

    // ---------------------------------------------------------------
    // Queue state
    // ---------------------------------------------------------------
    private final Map<String, Integer> queueDepths       = new ConcurrentHashMap<>();
    private final Map<String, Integer> queueHighWaterMarks = new ConcurrentHashMap<>();

    // ---------------------------------------------------------------
    // Record counters
    // ---------------------------------------------------------------
    private final AtomicLong recordsFetched    = new AtomicLong();
    private final AtomicLong recordsTransformed = new AtomicLong();
    private final AtomicLong recordsInserted   = new AtomicLong();

    // ---------------------------------------------------------------
    // Pipeline-level state
    // ---------------------------------------------------------------
    private volatile String pipelineStatus = "IDLE";
    private volatile long   startTimeMs    = 0;

    // ---------------------------------------------------------------
    // Throughput history: last 30 ticks (= ~15 s at 500 ms per tick)
    // ---------------------------------------------------------------
    private static final int HISTORY_SIZE = 30;

    /** Tail of the per-route throughput (exchanges/s) time series. */
    private final Map<String, LinkedList<Double>> throughputHistories = new ConcurrentHashMap<>();

    private final CamelContext camelContext;

    public PipelineMetricsService(CamelContext camelContext) {
        this.camelContext = camelContext;
        List.of("fetchFromOracle", "processBatch", "transformRecord", "insertToKinetica",
                "manualTrigger")
            .forEach(id -> routeStats.put(id, new RouteStats()));
        QUEUE_CAPACITIES.keySet()
            .forEach(q -> throughputHistories.put(q, new LinkedList<>()));
        List.of("fetchFromOracle", "processBatch", "transformRecord", "insertToKinetica")
            .forEach(id -> throughputHistories.put(id, new LinkedList<>()));
    }

    // ---------------------------------------------------------------
    // Called by CamelMetricsEventNotifier
    // ---------------------------------------------------------------

    public void onRouteStarted(String routeId) {
        RouteStats s = routeStats.computeIfAbsent(routeId, k -> new RouteStats());
        s.status = "RUNNING";
        if (("fetchFromOracle".equals(routeId) || "manualTrigger".equals(routeId))
                && "IDLE".equals(pipelineStatus)) {
            pipelineStatus = "RUNNING";
            startTimeMs    = System.currentTimeMillis();
            // reset counters on a fresh run
            recordsFetched.set(0);
            recordsTransformed.set(0);
            recordsInserted.set(0);
            routeStats.values().forEach(RouteStats::reset);
            queueHighWaterMarks.clear();
            throughputHistories.values().forEach(LinkedList::clear);
            log.info("Pipeline run started");
        }
    }

    /** Called by MetricsController immediately after resetForNewRun() + asyncSend(). */
    public void markPipelineStarted() {
        pipelineStatus = "RUNNING";
        startTimeMs    = System.currentTimeMillis();
        log.info("Pipeline run started (manual trigger)");
    }

    public void onExchangeCompleted(String routeId, long durationMs, boolean failed,
                                    Integer batchSize) {
        RouteStats s = routeStats.computeIfAbsent(routeId, k -> new RouteStats());
        // Mark the route RUNNING on first activity (RouteStartedEvent only fires at boot)
        if ("RUNNING".equals(pipelineStatus) && "IDLE".equals(s.status)) {
            s.status = "RUNNING";
        }
        if (failed) {
            s.failedExchanges.incrementAndGet();
        } else {
            long total = s.totalExchanges.incrementAndGet();
            s.totalProcessingTimeMs.addAndGet(durationMs);
            s.lastProcessingTimeMs = durationMs;
            s.meanProcessingTimeMs = (double) s.totalProcessingTimeMs.get() / total;
        }

        // Derive record-level counters from specific routes
        switch (routeId) {
            case "processBatch"     -> {
                // each processBatch exchange carries one batch; batchSize = rows in that batch
                if (batchSize != null && batchSize > 0) recordsFetched.addAndGet(batchSize);
            }
            case "transformRecord"  -> recordsTransformed.incrementAndGet();
            case "insertToKinetica" -> {
                if (batchSize != null && batchSize > 0) {
                    recordsInserted.addAndGet(batchSize);
                }
            }
        }
    }

    public void onRouteCompleted(String routeId) {
        RouteStats s = routeStats.computeIfAbsent(routeId, k -> new RouteStats());
        s.status = "COMPLETED";
    }

    // ---------------------------------------------------------------
    // Scheduled polling: queue depths + throughput + pipeline status
    // ---------------------------------------------------------------

    @Scheduled(fixedRate = 500)
    public void tick() {
        pollQueueDepths();
        computeThroughput();
        updatePipelineCompletionStatus();
    }

    private void pollQueueDepths() {
        QUEUE_CAPACITIES.keySet().forEach(queueId -> {
            try {
                // hasEndpoint("seda:batchQueue") does an exact-URI match and misses
                // endpoints registered with parameters (e.g. seda:batchQueue?size=200...).
                // Instead, scan all endpoints and match on the queue-name prefix.
                camelContext.getEndpoints().stream()
                    .filter(ep -> ep instanceof SedaEndpoint)
                    .map(ep -> (SedaEndpoint) ep)
                    .filter(ep -> isSedaQueueName(ep.getEndpointUri(), queueId))
                    .findFirst()
                    .ifPresent(seda -> {
                        int depth = seda.getQueue().size();
                        queueDepths.put(queueId, depth);
                        queueHighWaterMarks.merge(queueId, depth, Math::max);
                    });
            } catch (Exception ignored) {
                // endpoints not yet registered during startup
            }
        });
    }

    /** Matches seda:queueName and seda://queueName (with or without query params). */
    private static boolean isSedaQueueName(String uri, String queueId) {
        return uri.equals("seda://" + queueId)
            || uri.startsWith("seda://" + queueId + "?")
            || uri.equals("seda:" + queueId)
            || uri.startsWith("seda:" + queueId + "?");
    }

    private void computeThroughput() {
        routeStats.forEach((routeId, stats) -> {
            long current  = stats.totalExchanges.get();
            long previous = stats.prevTotalExchanges.getAndSet(current);
            // ticks at 500 ms → multiply by 2 to get per-second rate
            double tps = (current - previous) * 2.0;
            stats.currentThroughputPerSec = tps;

            LinkedList<Double> hist = throughputHistories
                    .computeIfAbsent(routeId, k -> new LinkedList<>());
            hist.addLast(tps);
            if (hist.size() > HISTORY_SIZE) hist.removeFirst();
        });
    }

    private void updatePipelineCompletionStatus() {
        if (!"RUNNING".equals(pipelineStatus)) return;

        // Completed = all queues drained + Route 4 has processed at least one exchange
        RouteStats r4 = routeStats.get("insertToKinetica");
        if (r4 == null || r4.totalExchanges.get() == 0) return;

        boolean allQueuesEmpty = QUEUE_CAPACITIES.keySet().stream()
                .allMatch(q -> queueDepths.getOrDefault(q, -1) == 0);
        boolean r4Idle = r4.currentThroughputPerSec == 0.0;

        if (allQueuesEmpty && r4Idle) {
            completionCheckCount++;
            if (completionCheckCount >= 4) {   // 4 × 500 ms = 2 s of quiet
                pipelineStatus = "COMPLETED";
                routeStats.values().stream()
                        .filter(s -> "RUNNING".equals(s.status))
                        .forEach(s -> s.status = "COMPLETED");
                completionCheckCount = 0;
                log.info("Pipeline run completed — {} records inserted", recordsInserted.get());
            }
        } else {
            completionCheckCount = 0;
        }
    }

    private int completionCheckCount = 0;

    // ---------------------------------------------------------------
    // Snapshot builder
    // ---------------------------------------------------------------

    public PipelineSnapshot getSnapshot() {
        Map<String, RouteMetricSnapshot> routes = new LinkedHashMap<>();
        List.of("fetchFromOracle", "processBatch", "transformRecord", "insertToKinetica")
            .forEach(id -> {
                RouteStats s = routeStats.getOrDefault(id, new RouteStats());
                routes.put(id, new RouteMetricSnapshot(
                        id,
                        s.status,
                        s.totalExchanges.get(),
                        s.failedExchanges.get(),
                        s.lastProcessingTimeMs,
                        s.meanProcessingTimeMs,
                        s.currentThroughputPerSec,
                        List.copyOf(throughputHistories.getOrDefault(id, new LinkedList<>()))
                ));
            });

        Map<String, QueueMetricSnapshot> queues = new LinkedHashMap<>();
        QUEUE_CAPACITIES.forEach((queueId, cap) -> {
            int depth = queueDepths.getOrDefault(queueId, 0);
            int hwm   = queueHighWaterMarks.getOrDefault(queueId, 0);
            queues.put(queueId, new QueueMetricSnapshot(
                    queueId,
                    depth,
                    cap,
                    hwm,
                    cap > 0 ? (depth * 100.0 / cap) : 0.0
            ));
        });

        long elapsed = startTimeMs > 0 ? System.currentTimeMillis() - startTimeMs : 0;
        return new PipelineSnapshot(
                pipelineStatus,
                startTimeMs,
                elapsed,
                recordsFetched.get(),
                recordsTransformed.get(),
                recordsInserted.get(),
                routes,
                queues
        );
    }

    public void resetForNewRun() {
        pipelineStatus = "IDLE";
        startTimeMs    = 0;
        recordsFetched.set(0);
        recordsTransformed.set(0);
        recordsInserted.set(0);
        routeStats.values().forEach(RouteStats::reset);
        queueHighWaterMarks.clear();
        throughputHistories.values().forEach(LinkedList::clear);
        completionCheckCount = 0;
    }

    // ---------------------------------------------------------------
    // Mutable per-route state container (not exposed outside package)
    // ---------------------------------------------------------------

    static class RouteStats {
        volatile String status = "IDLE";
        final AtomicLong totalExchanges        = new AtomicLong();
        final AtomicLong failedExchanges       = new AtomicLong();
        final AtomicLong totalProcessingTimeMs = new AtomicLong();
        final AtomicLong prevTotalExchanges    = new AtomicLong();
        volatile long   lastProcessingTimeMs;
        volatile double meanProcessingTimeMs;
        volatile double currentThroughputPerSec;

        void reset() {
            status = "IDLE";
            totalExchanges.set(0);
            failedExchanges.set(0);
            totalProcessingTimeMs.set(0);
            prevTotalExchanges.set(0);
            lastProcessingTimeMs  = 0;
            meanProcessingTimeMs  = 0;
            currentThroughputPerSec = 0;
        }
    }
}
