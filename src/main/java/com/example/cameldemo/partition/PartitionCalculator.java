package com.example.cameldemo.partition;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Calculates ID-range partitions over BANK_TRANSACTIONS and tracks progress
 * in the ETL_PROGRESS table.
 *
 * Called from the partitionController Camel route (Spring XML DSL).
 */
@Component
public class PartitionCalculator {

    private static final Logger log = LoggerFactory.getLogger(PartitionCalculator.class);
    private static final int PARTITION_SIZE = 100_000;

    private final JdbcTemplate jdbc;

    public PartitionCalculator(@Qualifier("oracleDataSource") DataSource oracleDataSource) {
        this.jdbc = new JdbcTemplate(oracleDataSource);
    }

    // ------------------------------------------------------------------
    // Called by partitionController route
    // ------------------------------------------------------------------

    /**
     * Queries MIN/MAX TRANSACTION_ID, divides the range into partitions of
     * {@value PARTITION_SIZE} rows, writes tracking rows to ETL_PROGRESS,
     * and returns the list so Camel can split and dispatch them.
     *
     * @return list of RangePartition objects (empty if table has no rows)
     */
    public List<RangePartition> calculatePartitions() {
        Map<String, Object> bounds = jdbc.queryForMap(
                "SELECT MIN(TRANSACTION_ID) AS MIN_ID, MAX(TRANSACTION_ID) AS MAX_ID " +
                "FROM BANK_TRANSACTIONS");

        Object rawMin = bounds.get("MIN_ID");
        Object rawMax = bounds.get("MAX_ID");

        if (rawMin == null || rawMax == null) {
            log.warn("[PartitionCalculator] BANK_TRANSACTIONS is empty — no partitions created");
            return Collections.emptyList();
        }

        long minId = ((Number) rawMin).longValue();
        long maxId = ((Number) rawMax).longValue();

        List<RangePartition> partitions = new ArrayList<>();
        int partitionId = 1;
        for (long start = minId; start <= maxId; start += PARTITION_SIZE) {
            long end = Math.min(start + PARTITION_SIZE - 1, maxId);
            partitions.add(new RangePartition(partitionId++, start, end));
        }

        // Persist to ETL_PROGRESS (replaces any previous run)
        jdbc.update("DELETE FROM ETL_PROGRESS");
        for (RangePartition p : partitions) {
            jdbc.update(
                "INSERT INTO ETL_PROGRESS " +
                "(PARTITION_ID, RANGE_START, RANGE_END, STATUS, ROWS_PROCESSED, UPDATED_AT) " +
                "VALUES (?, ?, ?, 'PENDING', 0, SYSDATE)",
                p.getPartitionId(), p.getStartId(), p.getEndId());
        }

        log.info("[PartitionCalculator] {} partitions created — IDs {} to {} (~{} rows/partition)",
                partitions.size(), minId, maxId, PARTITION_SIZE);
        return partitions;
    }

    // ------------------------------------------------------------------
    // Called by resumeCheck route (converts a JDBC result row → RangePartition)
    // ------------------------------------------------------------------

    /**
     * Converts a single ETL_PROGRESS result row (Map from JDBC) to a RangePartition.
     * Camel calls this once per element during the split inside resumeCheck.
     */
    public RangePartition toRangePartition(Map<String, Object> row) {
        int  partitionId = ((Number) row.get("PARTITION_ID")).intValue();
        long startId     = ((Number) row.get("RANGE_START")).longValue();
        long endId       = ((Number) row.get("RANGE_END")).longValue();
        return new RangePartition(partitionId, startId, endId);
    }
}
