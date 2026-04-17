package com.example.cameldemo.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Helper bean for the filteredQueryTrigger route.
 *
 * Two responsibilities:
 *   1. {@link #validateFilterHeaders}: normalises the AMOUNT header — if present but
 *      not numeric, logs a WARN and removes it so the SQL optional-filter predicate
 *      ({@code :#AMOUNT IS NULL OR AMOUNT >= :#AMOUNT}) treats it as absent.
 *
 *   2. {@link #logProgress}: logs streaming progress at INFO level every
 *      {@code etl.filtered.pageSize} rows.  Called as a bean step inside the
 *      streaming split; returns void so the row body passes through unchanged.
 */
@Component("filteredQueryHelperBean")
public class FilteredQueryHelperBean {

    private static final Logger log = LoggerFactory.getLogger(FilteredQueryHelperBean.class);

    @Value("${etl.filtered.pageSize:1000}")
    private int pageSize;

    // ------------------------------------------------------------------
    // Header validation (called once at route entry)
    // ------------------------------------------------------------------

    /**
     * Validates the AMOUNT exchange header.
     * <ul>
     *   <li>Missing or already a {@link Number} → no-op.</li>
     *   <li>Present as a non-numeric String → attempts Double.parseDouble;
     *       on failure logs WARN and removes the header so the SQL filter
     *       is skipped for this field.</li>
     * </ul>
     */
    public void validateFilterHeaders(Exchange exchange) {
        Message msg = exchange.getMessage();
        Object amountHeader = msg.getHeader("AMOUNT");
        if (amountHeader == null || amountHeader instanceof Number) {
            return;
        }
        try {
            double val = Double.parseDouble(amountHeader.toString().trim());
            msg.setHeader("AMOUNT", val);
        } catch (NumberFormatException e) {
            log.warn("[filteredQueryTrigger] AMOUNT header '{}' is not numeric — amount filter will be skipped",
                     amountHeader);
            msg.removeHeader("AMOUNT");
        }
    }

    // ------------------------------------------------------------------
    // Progress logging (called per-row inside the streaming split)
    // ------------------------------------------------------------------

    /**
     * Logs an INFO message every {@code etl.filtered.pageSize} rows.
     * The "page" number is derived from the Camel split index so that the log
     * reads "[filteredQueryTrigger] Fetched page N, rows so far = X".
     *
     * Returns void — exchange body (the row Map) is left unchanged by Camel.
     */
    public void logProgress(Exchange exchange) {
        long index = exchange.getProperty(Exchange.SPLIT_INDEX, 0L, Long.class);
        if (index > 0 && index % pageSize == 0) {
            long page = index / pageSize;
            log.info("[filteredQueryTrigger] Fetched page {}, rows so far = {}", page, index);
        }
    }
}
