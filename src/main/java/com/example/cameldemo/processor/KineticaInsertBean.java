package com.example.cameldemo.processor;

import com.gpudb.BulkInserter;
import com.gpudb.GenericRecord;
import com.gpudb.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.Map;

/**
 * Camel bean for Route 5 (insertToKinetica).
 *
 * Each call to {@link #insert(Map)} converts one Oracle row (Map&lt;String,Object&gt;)
 * to a Kinetica {@link GenericRecord} and hands it to the shared
 * {@link BulkInserter}.  The BulkInserter buffers records internally and
 * flushes to Kinetica automatically once {@code kinetica.batchSize} records
 * have accumulated, or when {@link #scheduledFlush()} fires every 5 seconds.
 *
 * Thread-safety: {@link BulkInserter#insert} is thread-safe; all 8 concurrent
 * Camel consumers in Route 5 share the same BulkInserter instance safely.
 */
@Component
public class KineticaInsertBean {

    private static final Logger log = LoggerFactory.getLogger(KineticaInsertBean.class);

    private final BulkInserter<GenericRecord> bulkInserter;
    private final Type type;

    public KineticaInsertBean(BulkInserter<GenericRecord> bulkInserter, Type kineticaType) {
        this.bulkInserter = bulkInserter;
        this.type         = kineticaType;
    }

    // ------------------------------------------------------------------
    // Called per-record from the Camel route (one Map = one Oracle row)
    // ------------------------------------------------------------------

    /**
     * Converts an Oracle result row to a {@link GenericRecord} and queues it
     * in the BulkInserter.  Camel passes the exchange body (Map&lt;String,Object&gt;)
     * as the {@code row} parameter via bean binding.
     */
    @SuppressWarnings("unchecked")
    public void insert(Map<String, Object> row) throws BulkInserter.InsertException {
        GenericRecord record = new GenericRecord(type);

        record.put("TRANSACTION_ID",      toLong(row.get("TRANSACTION_ID")));
        record.put("ACCOUNT_NUMBER",      toStr(row.get("ACCOUNT_NUMBER")));
        record.put("BENEFICIARY_ACCOUNT", toStr(row.get("BENEFICIARY_ACCOUNT")));
        record.put("AMOUNT",              toDouble(row.get("AMOUNT")));
        record.put("CURRENCY",            toStr(row.get("CURRENCY")));
        record.put("TRANSACTION_TYPE",    toStr(row.get("TRANSACTION_TYPE")));
        record.put("STATUS",              toStr(row.get("STATUS")));
        record.put("BRANCH_CODE",         toStr(row.get("BRANCH_CODE")));
        record.put("CREATED_AT",          toEpochMs(row.get("CREATED_AT")));
        record.put("DESCRIPTION",         toStr(row.get("DESCRIPTION")));
        record.put("INGESTION_TS",        System.currentTimeMillis());
        record.put("PIPELINE_BATCH_ID",   null);

        bulkInserter.insert(record);
    }

    // ------------------------------------------------------------------
    // Periodic flush — drains partial batches between pipeline runs
    // ------------------------------------------------------------------

    /**
     * Flushes any records that haven't yet reached {@code batchSize}.
     * Fires every 5 seconds so the last partial batch reaches Kinetica
     * within that window rather than waiting for shutdown.
     */
    @Scheduled(fixedRate = 5_000)
    public void scheduledFlush() {
        try {
            bulkInserter.flush();
        } catch (BulkInserter.InsertException e) {
            log.warn("[KineticaInsertBean] Scheduled flush failed: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Graceful shutdown
    // ------------------------------------------------------------------

    @PreDestroy
    public void shutdown() {
        log.info("[KineticaInsertBean] Flushing remaining records on shutdown…");
        try {
            bulkInserter.flush();
            log.info("[KineticaInsertBean] Shutdown flush complete");
        } catch (BulkInserter.InsertException e) {
            log.error("[KineticaInsertBean] Shutdown flush failed: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // Type converters
    // ------------------------------------------------------------------

    private Long toLong(Object v) {
        return v == null ? null : ((Number) v).longValue();
    }

    private Double toDouble(Object v) {
        return v == null ? null : ((Number) v).doubleValue();
    }

    private String toStr(Object v) {
        return v == null ? null : v.toString();
    }

    /** Converts Oracle Timestamp / java.util.Date → epoch milliseconds. */
    private Long toEpochMs(Object v) {
        if (v == null)                          return null;
        if (v instanceof java.sql.Timestamp ts) return ts.getTime();
        if (v instanceof java.util.Date     d)  return d.getTime();
        return null;
    }
}
