package com.example.cameldemo.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Converts an aggregated List<Map<String,Object>> into a multi-row VALUES INSERT
 * for Kinetica's JDBC component.
 *
 * Column order matches BANK_TRANSACTIONS_TARGET in kinetica-init.sql:
 *   TRANSACTION_ID, ACCOUNT_NUMBER, BENEFICIARY_ACCOUNT, AMOUNT, CURRENCY,
 *   TRANSACTION_TYPE, STATUS, BRANCH_CODE, CREATED_AT, DESCRIPTION,
 *   INGESTION_TS, PIPELINE_BATCH_ID
 *
 * NOTE: String concatenation is used here for simplicity. For production,
 * replace with a PreparedStatement batch via Camel's SQL component or a
 * custom DataSource accessor to avoid SQL injection risk on free-text columns.
 */
@Component
public class KineticaBulkInsertProcessor implements Processor {

    private static final Logger log = LoggerFactory.getLogger(KineticaBulkInsertProcessor.class);

    private static final String INSERT_PREFIX =
            "INSERT INTO BANK_TRANSACTIONS_TARGET " +
            "(TRANSACTION_ID, ACCOUNT_NUMBER, BENEFICIARY_ACCOUNT, AMOUNT, CURRENCY, " +
            " TRANSACTION_TYPE, STATUS, BRANCH_CODE, CREATED_AT, DESCRIPTION, " +
            " INGESTION_TS, PIPELINE_BATCH_ID) VALUES ";

    @Override
    @SuppressWarnings("unchecked")
    public void process(Exchange exchange) {
        List<Map<String, Object>> rows = exchange.getIn().getBody(List.class);
        if (rows == null || rows.isEmpty()) {
            log.warn("Empty batch received — skipping insert");
            exchange.setRouteStop(true);
            return;
        }

        // Header read by CamelMetricsEventNotifier to count inserted records
        exchange.getIn().setHeader("X-Batch-Size", rows.size());

        StringJoiner values = new StringJoiner(", ");
        for (Map<String, Object> row : rows) {
            values.add("(" +
                    sqlNum(row.get("TRANSACTION_ID"))        + ", " +
                    sqlStr(row.get("ACCOUNT_NUMBER"))        + ", " +
                    sqlStr(row.get("BENEFICIARY_ACCOUNT"))   + ", " +
                    sqlNum(row.get("AMOUNT"))                + ", " +
                    sqlStr(row.get("CURRENCY"))              + ", " +
                    sqlStr(row.get("TRANSACTION_TYPE"))      + ", " +
                    sqlStr(row.get("STATUS"))                + ", " +
                    sqlStr(row.get("BRANCH_CODE"))           + ", " +
                    sqlTimestamp(row.get("CREATED_AT"))      + ", " +
                    sqlStr(row.get("DESCRIPTION"))           + ", " +
                    sqlNum(row.get("INGESTION_TS"))          + ", " +
                    sqlStr(row.get("PIPELINE_BATCH_ID"))     +
                    ")");
        }

        String sql = INSERT_PREFIX + values;
        log.debug("Bulk INSERT for {} rows ({} chars)", rows.size(), sql.length());
        exchange.getIn().setBody(sql);
    }

    private String sqlStr(Object value) {
        if (value == null) return "NULL";
        return "'" + value.toString().replace("'", "''") + "'";
    }

    private String sqlNum(Object value) {
        if (value == null) return "NULL";
        return value.toString();
    }

    private String sqlTimestamp(Object value) {
        if (value == null) return "NULL";
        // Kinetica accepts ISO-8601 / Oracle Timestamp.toString() literals for DATETIME
        return "'" + value.toString().replace("'", "''") + "'";
    }
}
