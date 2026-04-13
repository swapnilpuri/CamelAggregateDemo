package com.example.cameldemo.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * Transforms a single BANK_TRANSACTIONS row (Map<String,Object> from Oracle JDBC)
 * into the shape expected by BANK_TRANSACTIONS_TARGET in Kinetica.
 *
 * Source columns  (Oracle):
 *   TRANSACTION_ID, ACCOUNT_NUMBER, BENEFICIARY_ACCOUNT, AMOUNT, CURRENCY,
 *   TRANSACTION_TYPE, STATUS, BRANCH_CODE, CREATED_AT, DESCRIPTION
 *
 * Target columns  (Kinetica — same names, with two ETL audit additions):
 *   ... all source columns ... + INGESTION_TS, PIPELINE_BATCH_ID
 */
@Component
public class TransactionTransformer implements Processor {

    private static final Logger log = LoggerFactory.getLogger(TransactionTransformer.class);

    @Override
    @SuppressWarnings("unchecked")
    public void process(Exchange exchange) {
        Map<String, Object> row = exchange.getIn().getBody(Map.class);
        if (row == null) {
            log.warn("Received null row — skipping");
            exchange.getIn().setBody(null);
            return;
        }

        Map<String, Object> out = new HashMap<>(14);

        // Pass-through columns (types already correct from Oracle JDBC driver)
        out.put("TRANSACTION_ID",      row.get("TRANSACTION_ID"));
        out.put("ACCOUNT_NUMBER",      row.get("ACCOUNT_NUMBER"));
        out.put("BENEFICIARY_ACCOUNT", row.get("BENEFICIARY_ACCOUNT"));
        out.put("CURRENCY",            nullSafe(row.get("CURRENCY"), "USD"));
        out.put("TRANSACTION_TYPE",    nullSafe(row.get("TRANSACTION_TYPE"), "UNKNOWN"));
        out.put("STATUS",              nullSafe(row.get("STATUS"), "PENDING"));
        out.put("BRANCH_CODE",         row.get("BRANCH_CODE"));
        out.put("CREATED_AT",          row.get("CREATED_AT"));
        out.put("DESCRIPTION",         row.get("DESCRIPTION"));

        // Normalise AMOUNT to 2 d.p. (Oracle NUMBER(15,2) can arrive as BigDecimal)
        out.put("AMOUNT", toDecimal2dp(row.get("AMOUNT")));

        // ETL audit fields
        out.put("INGESTION_TS",      System.currentTimeMillis());
        out.put("PIPELINE_BATCH_ID", exchange.getExchangeId());

        exchange.getIn().setBody(out);
    }

    private BigDecimal toDecimal2dp(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd.setScale(2, RoundingMode.HALF_UP);
        if (value instanceof Number n)      return BigDecimal.valueOf(n.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        try { return new BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP); }
        catch (NumberFormatException e)     { return BigDecimal.ZERO; }
    }

    private Object nullSafe(Object value, Object fallback) {
        return value != null ? value : fallback;
    }
}
