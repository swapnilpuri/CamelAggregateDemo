package com.example.cameldemo.web;

import com.example.cameldemo.metrics.PipelineMetricsService;
import com.example.cameldemo.metrics.PipelineSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * REST controller exposing:
 *
 *   GET  /api/pipeline/stream   — Server-Sent Events stream of PipelineSnapshot (500 ms)
 *   GET  /api/pipeline/metrics  — Single snapshot (for initial page load / health checks)
 *   POST /api/pipeline/start    — Triggers a new pipeline run via direct:triggerPipeline
 *   POST /api/pipeline/reset    — Resets metrics to IDLE state
 */
@RestController
@RequestMapping("/api/pipeline")
public class MetricsController {

    private static final Logger log = LoggerFactory.getLogger(MetricsController.class);
    private static final long SSE_TIMEOUT_MS = 10 * 60 * 1000L; // 10 minutes

    private final PipelineMetricsService metricsService;
    private final ProducerTemplate       producerTemplate;
    private final ObjectMapper           objectMapper;

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public MetricsController(PipelineMetricsService metricsService,
                             ProducerTemplate producerTemplate,
                             ObjectMapper objectMapper) {
        this.metricsService  = metricsService;
        this.producerTemplate = producerTemplate;
        this.objectMapper    = objectMapper;
    }

    // ------------------------------------------------------------------
    // SSE stream
    // ------------------------------------------------------------------

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(()    -> { emitter.complete(); emitters.remove(emitter); });
        emitter.onError(e       -> emitters.remove(emitter));

        // Push current snapshot immediately so the UI doesn't wait 500 ms
        try {
            emitter.send(SseEmitter.event()
                    .name("metrics")
                    .data(objectMapper.writeValueAsString(metricsService.getSnapshot())));
        } catch (Exception e) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    /** Broadcast to all connected clients every 500 ms. */
    @Scheduled(fixedRate = 500)
    public void broadcast() {
        if (emitters.isEmpty()) return;
        try {
            String json = objectMapper.writeValueAsString(metricsService.getSnapshot());
            List<SseEmitter> dead = new ArrayList<>();
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name("metrics").data(json));
                } catch (Exception e) {
                    dead.add(emitter);
                }
            }
            emitters.removeAll(dead);
        } catch (Exception e) {
            log.warn("SSE broadcast error: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // One-shot snapshot (REST fallback)
    // ------------------------------------------------------------------

    @GetMapping("/metrics")
    public ResponseEntity<PipelineSnapshot> getMetrics() {
        return ResponseEntity.ok(metricsService.getSnapshot());
    }

    // ------------------------------------------------------------------
    // Pipeline trigger
    // ------------------------------------------------------------------

    @PostMapping("/start")
    public ResponseEntity<String> startPipeline(
            @RequestParam(defaultValue = "0") int limit) {
        PipelineSnapshot current = metricsService.getSnapshot();
        if ("RUNNING".equals(current.pipelineStatus())) {
            return ResponseEntity.badRequest().body("Pipeline is already running");
        }
        if (limit < 0) {
            return ResponseEntity.badRequest().body("limit must be >= 0 (0 = all records)");
        }
        metricsService.resetForNewRun();
        metricsService.markPipelineStarted();
        // Fire-and-forget: runs in Camel's thread pool via the SEDA queues.
        // Pass recordLimit as a header so the manualTrigger route can build the SQL.
        producerTemplate.asyncSend("direct:triggerPipeline", exchange -> {
            exchange.getMessage().setHeader("recordLimit", limit);
        });
        log.info("Pipeline triggered via REST (recordLimit={})", limit == 0 ? "ALL" : limit);
        return ResponseEntity.accepted().body("Pipeline started");
    }

    @PostMapping("/reset")
    public ResponseEntity<String> reset() {
        metricsService.resetForNewRun();
        return ResponseEntity.ok("Metrics reset");
    }
}
