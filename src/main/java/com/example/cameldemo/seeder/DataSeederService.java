package com.example.cameldemo.seeder;

import net.datafaker.Faker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Populates BANK_TRANSACTIONS in Oracle with realistic fake data using
 * the DataFaker library.
 *
 * Usage:
 *   POST /api/seed?count=50000
 *
 * Records are inserted in batches of BATCH_SIZE using Spring's JdbcTemplate
 * batchUpdate so even millions of rows are handled without OOM.
 */
@Service
public class DataSeederService {

    private static final Logger log = LoggerFactory.getLogger(DataSeederService.class);

    private static final int BATCH_SIZE = 500;

    private static final String INSERT_SQL =
            "INSERT INTO BANK_TRANSACTIONS " +
            "(ACCOUNT_NUMBER, BENEFICIARY_ACCOUNT, AMOUNT, CURRENCY, " +
            " TRANSACTION_TYPE, STATUS, BRANCH_CODE, CREATED_AT, DESCRIPTION) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String[] CURRENCIES = {
            "USD", "USD", "USD",   // weighted — USD most common
            "EUR", "EUR",
            "GBP",
            "JPY", "CHF", "CAD", "AUD"
    };

    private static final String[] TRANSACTION_TYPES = {
            "CREDIT", "CREDIT",
            "DEBIT",  "DEBIT",
            "TRANSFER",
            "PAYMENT",
            "WITHDRAWAL"
    };

    // 70 % COMPLETED, 20 % PENDING, 10 % FAILED
    private static final String[] STATUSES = {
            "COMPLETED", "COMPLETED", "COMPLETED", "COMPLETED", "COMPLETED",
            "COMPLETED", "COMPLETED",
            "PENDING", "PENDING",
            "FAILED"
    };

    private final JdbcTemplate jdbc;
    private final Faker        faker;

    public DataSeederService(@Qualifier("oracleDataSource") DataSource oracleDataSource) {
        this.jdbc  = new JdbcTemplate(oracleDataSource);
        this.faker = new Faker(Locale.ENGLISH);
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Inserts {@code count} randomly generated rows into BANK_TRANSACTIONS.
     *
     * @param count number of records to insert (1 – 10 000 000)
     * @return SeederResult with timing and totals
     */
    public SeederResult seed(int count) {
        if (count < 1 || count > 10_000_000) {
            throw new IllegalArgumentException("count must be between 1 and 10 000 000");
        }

        log.info("Seeding {} rows into BANK_TRANSACTIONS…", count);
        long start      = System.currentTimeMillis();
        int  inserted   = 0;
        int  remaining  = count;

        while (remaining > 0) {
            int batchCount = Math.min(remaining, BATCH_SIZE);
            List<Object[]> batch = buildBatch(batchCount);
            int[] results = jdbc.batchUpdate(INSERT_SQL, batch);
            for (int r : results) inserted += (r >= 0 ? r : 1);
            remaining -= batchCount;

            if (log.isDebugEnabled()) {
                log.debug("Inserted {} / {} rows", inserted, count);
            }
        }

        long durationMs = System.currentTimeMillis() - start;
        double rps      = durationMs > 0 ? inserted * 1000.0 / durationMs : inserted;
        long total      = countRows();

        log.info("Seeded {} rows in {} ms ({} rows/s). Oracle total: {}",
                inserted, durationMs, String.format("%.0f", rps), total);

        return new SeederResult(count, inserted, durationMs, rps, total);
    }

    /** Returns the current row count of BANK_TRANSACTIONS. */
    public long countRows() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM BANK_TRANSACTIONS", Long.class);
        return n != null ? n : 0L;
    }

    // ------------------------------------------------------------------
    // Batch builder
    // ------------------------------------------------------------------

    private List<Object[]> buildBatch(int size) {
        List<Object[]> batch = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            batch.add(buildRow());
        }
        return batch;
    }

    private Object[] buildRow() {
        String txType = pick(TRANSACTION_TYPES);

        return new Object[]{
                accountNumber(),                             // ACCOUNT_NUMBER
                faker.random().nextInt(10) < 7              // BENEFICIARY_ACCOUNT (70 % present)
                        ? accountNumber() : null,
                randomAmount(),                              // AMOUNT
                pick(CURRENCIES),                            // CURRENCY
                txType,                                      // TRANSACTION_TYPE
                pick(STATUSES),                              // STATUS
                branchCode(),                                // BRANCH_CODE
                randomPastTimestamp(),                       // CREATED_AT
                buildDescription(txType),                    // DESCRIPTION
        };
    }

    // ------------------------------------------------------------------
    // Field generators
    // ------------------------------------------------------------------

    /** ACC + 10 digits, max 13 chars — fits VARCHAR2(20) */
    private String accountNumber() {
        return faker.numerify("ACC##########");
    }

    /** BR + 3 digits → 5 chars — fits VARCHAR2(10) */
    private String branchCode() {
        return faker.numerify("BR###");
    }

    /** Random amount 0.01 – 99 999.99 scaled to 2 d.p. */
    private BigDecimal randomAmount() {
        double raw = faker.random().nextDouble(0.01, 99_999.99);
        return BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP);
    }

    /** Uniform random timestamp within the last 2 years */
    private Timestamp randomPastTimestamp() {
        long twoYearsMs = 2L * 365 * 24 * 60 * 60 * 1_000;
        long offset     = (long)(faker.random().nextDouble() * twoYearsMs);
        return new Timestamp(System.currentTimeMillis() - offset);
    }

    /**
     * Builds a human-readable banking description using Faker.
     * Capped at 200 chars to match the column size.
     */
    private String buildDescription(String txType) {
        String raw = switch (txType) {
            case "CREDIT"     -> "Incoming credit from "     + faker.company().name();
            case "DEBIT"      -> "Purchase at "              + faker.company().name()
                                     + " — " + faker.commerce().productName();
            case "TRANSFER"   -> "Transfer to "              + faker.name().fullName()
                                     + " ref " + faker.numerify("TXN########");
            case "PAYMENT"    -> "Payment for "              + faker.commerce().department()
                                     + " invoice " + faker.numerify("INV-#####");
            case "WITHDRAWAL" -> "ATM withdrawal "           + faker.address().city()
                                     + " branch";
            default           -> faker.lorem().sentence(6);
        };
        return raw.length() > 200 ? raw.substring(0, 200) : raw;
    }

    /** Picks a uniformly random element from an array. */
    private <T> T pick(T[] array) {
        return array[faker.random().nextInt(array.length)];
    }
}
