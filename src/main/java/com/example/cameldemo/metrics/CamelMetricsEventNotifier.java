package com.example.cameldemo.metrics;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Plugs into Camel's management event bus to capture per-exchange timings
 * and route lifecycle events, forwarding them to PipelineMetricsService.
 *
 * Registered programmatically via @PostConstruct so it is guaranteed to be
 * wired before any routes process exchanges (Spring Boot creates this bean
 * before the Camel context finishes starting).
 */
@Component
public class CamelMetricsEventNotifier extends EventNotifierSupport {

    private static final Logger log = LoggerFactory.getLogger(CamelMetricsEventNotifier.class);

    private final CamelContext camelContext;
    private final PipelineMetricsService metricsService;

    public CamelMetricsEventNotifier(CamelContext camelContext,
                                     PipelineMetricsService metricsService) {
        this.camelContext    = camelContext;
        this.metricsService  = metricsService;
    }

    @PostConstruct
    public void register() {
        camelContext.getManagementStrategy().addEventNotifier(this);
        log.info("CamelMetricsEventNotifier registered");
    }

    // ---------------------------------------------------------------
    // Filter: only process event types we care about
    // ---------------------------------------------------------------

    @Override
    public boolean isEnabled(CamelEvent event) {
        return event instanceof CamelEvent.ExchangeCompletedEvent
            || event instanceof CamelEvent.ExchangeFailedEvent
            || event instanceof CamelEvent.RouteStartedEvent
            || event instanceof CamelEvent.RouteStoppedEvent;
    }

    // ---------------------------------------------------------------
    // Event dispatch
    // ---------------------------------------------------------------

    @Override
    public void notify(CamelEvent event) {
        try {
            if (event instanceof CamelEvent.ExchangeCompletedEvent e) {
                handleExchangeDone(e.getExchange(), false);

            } else if (event instanceof CamelEvent.ExchangeFailedEvent e) {
                handleExchangeDone(e.getExchange(), true);

            } else if (event instanceof CamelEvent.RouteStartedEvent e) {
                String routeId = e.getRoute().getId();
                metricsService.onRouteStarted(routeId);

            } else if (event instanceof CamelEvent.RouteStoppedEvent e) {
                String routeId = e.getRoute().getId();
                metricsService.onRouteCompleted(routeId);
            }
        } catch (Exception ex) {
            log.warn("Error in CamelMetricsEventNotifier.notify: {}", ex.getMessage());
        }
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private void handleExchangeDone(Exchange exchange, boolean failed) {
        String routeId = exchange.getFromRouteId();
        if (routeId == null) return;

        // Elapsed time in milliseconds from the Camel Clock (Camel 4.x API)
        long durationMs = exchange.getClock().elapsed();

        // For Route 4 (insertToKinetica), read the batch size set by
        // KineticaBulkInsertProcessor before it overwrites the body
        Integer batchSize = exchange.getIn().getHeader("X-Batch-Size", Integer.class);

        metricsService.onExchangeCompleted(routeId, durationMs, failed, batchSize);

        if (log.isTraceEnabled()) {
            log.trace("[{}] exchange {} in {} ms (failed={})",
                    routeId, exchange.getExchangeId(), durationMs, failed);
        }
    }
}
