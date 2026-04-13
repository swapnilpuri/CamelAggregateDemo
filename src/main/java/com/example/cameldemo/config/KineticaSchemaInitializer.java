package com.example.cameldemo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Creates BANK_TRANSACTIONS_TARGET in Kinetica at startup if it doesn't exist.
 * Uses JdbcTemplate.execute() directly to avoid ScriptUtils comment-stripping
 * issues with Kinetica's USING TABLE PROPERTIES DDL extension.
 */
@Component
public class KineticaSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KineticaSchemaInitializer.class);

    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS BANK_TRANSACTIONS_TARGET (" +
            "  TRANSACTION_ID      BIGINT        NOT NULL, " +
            "  ACCOUNT_NUMBER      VARCHAR(20)   NOT NULL, " +
            "  BENEFICIARY_ACCOUNT VARCHAR(20), " +
            "  AMOUNT              DECIMAL(15,2) NOT NULL, " +
            "  CURRENCY            VARCHAR(3)    NOT NULL, " +
            "  TRANSACTION_TYPE    VARCHAR(20)   NOT NULL, " +
            "  STATUS              VARCHAR(15)   NOT NULL, " +
            "  BRANCH_CODE         VARCHAR(10), " +
            "  CREATED_AT          DATETIME, " +
            "  DESCRIPTION         VARCHAR(200), " +
            "  INGESTION_TS        BIGINT, " +
            "  PIPELINE_BATCH_ID   VARCHAR(36), " +
            "  PRIMARY KEY (TRANSACTION_ID) " +
            ")";

    private final JdbcTemplate kineticaJdbc;

    public KineticaSchemaInitializer(@Qualifier("kineticaDataSource") DataSource kineticaDataSource) {
        this.kineticaJdbc = new JdbcTemplate(kineticaDataSource);
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info(">>> KineticaSchemaInitializer: creating BANK_TRANSACTIONS_TARGET if absent…");
        try {
            kineticaJdbc.execute(CREATE_TABLE_SQL);
            log.info(">>> KineticaSchemaInitializer: BANK_TRANSACTIONS_TARGET is ready");

            // Verify by counting rows — proves the table is reachable
            Long count = kineticaJdbc.queryForObject(
                    "SELECT COUNT(*) FROM BANK_TRANSACTIONS_TARGET", Long.class);
            log.info(">>> KineticaSchemaInitializer: current row count = {}", count);

        } catch (Exception e) {
            log.error(">>> KineticaSchemaInitializer FAILED — inserts will fail: {}", e.getMessage(), e);
        }
    }
}
