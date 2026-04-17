package com.example.cameldemo.web;

import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST entry point for the ad-hoc filtered Oracle → Kinetica ETL.
 *
 *   POST /api/query/filtered?amount=&currency=&transactionType=&status=&branchCode=
 *
 * All parameters are optional.  Missing / blank parameters are omitted from the
 * Camel exchange so the SQL optional-predicate ({@code :#X IS NULL OR ...}) treats
 * them as absent.  String values are trimmed and uppercased to match Oracle data.
 *
 * Returns 202 Accepted immediately — the ETL runs asynchronously through the
 * filteredQueryTrigger → seda:filteredInsertQueue → filteredAggregateAndLoad pipeline.
 */
@RestController
@RequestMapping("/api/query")
public class FilteredQueryController {

    private static final Logger log = LoggerFactory.getLogger(FilteredQueryController.class);

    private final ProducerTemplate producerTemplate;

    public FilteredQueryController(ProducerTemplate producerTemplate) {
        this.producerTemplate = producerTemplate;
    }

    @PostMapping("/filtered")
    public ResponseEntity<Map<String, Object>> runFilteredQuery(
            @RequestParam(required = false) Double  amount,
            @RequestParam(required = false) String  currency,
            @RequestParam(required = false) String  transactionType,
            @RequestParam(required = false) String  status,
            @RequestParam(required = false) String  branchCode) {

        // Normalise strings: trim + uppercase + blank → null
        final String normCurrency = normalise(currency);
        final String normType     = normalise(transactionType);
        final String normStatus   = normalise(status);
        final String normBranch   = normalise(branchCode);

        // Build the "active filters" map used in the response summary
        Map<String, Object> activeFilters = new LinkedHashMap<>();
        if (amount != null)       activeFilters.put("amount",          amount);
        if (normCurrency != null) activeFilters.put("currency",        normCurrency);
        if (normType != null)     activeFilters.put("transactionType", normType);
        if (normStatus != null)   activeFilters.put("status",          normStatus);
        if (normBranch != null)   activeFilters.put("branchCode",      normBranch);

        // Fire-and-forget: sets headers on the exchange and dispatches to Camel
        producerTemplate.asyncSend("direct:runFilteredQuery", exchange -> {
            var msg = exchange.getMessage();
            if (amount != null)       msg.setHeader("AMOUNT",           amount);
            if (normCurrency != null) msg.setHeader("CURRENCY",         normCurrency);
            if (normType != null)     msg.setHeader("TRANSACTION_TYPE", normType);
            if (normStatus != null)   msg.setHeader("STATUS",           normStatus);
            if (normBranch != null)   msg.setHeader("BRANCH_CODE",      normBranch);
        });

        String message = activeFilters.isEmpty()
                ? "Full table export dispatched — no filters applied"
                : "Filtered query dispatched with " + activeFilters.size() + " active filter(s)";

        log.info("[FilteredQueryController] {} → {}", message, activeFilters);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status",        "dispatched");
        response.put("filterCount",   activeFilters.size());
        response.put("activeFilters", activeFilters);
        response.put("message",       message);

        return ResponseEntity.accepted().body(response);
    }

    private String normalise(String s) {
        if (s == null) return null;
        String t = s.strip().toUpperCase();
        return t.isEmpty() ? null : t;
    }
}
