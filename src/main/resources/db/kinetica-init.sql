-- Kinetica target table DDL
-- Run once against the Kinetica instance after the container is healthy.
-- Compatible with Kinetica 7.2.x SQL endpoint (REST or JDBC).
--
-- Usage via JDBC:
--   java -cp kinetica-jdbc-7.2.x.x-jar-with-dependencies.jar \
--        com.kinetica.jdbc.util.SqlRunner \
--        -url "jdbc:kinetica:URL=http://localhost:9191;" \
--        -user admin -pass kinetica123 \
--        -file kinetica-init.sql
--
-- Or via Kinetica Workbench / GAdmin UI SQL worksheet.

-- ============================================================
-- DDL — target table (mirrors Oracle source schema)
-- ============================================================

CREATE TABLE IF NOT EXISTS BANK_TRANSACTIONS_TARGET
(
    -- Identity
    TRANSACTION_ID        BIGINT        NOT NULL,   -- sourced from Oracle NUMBER PK
    ACCOUNT_NUMBER        VARCHAR(20)   NOT NULL,
    BENEFICIARY_ACCOUNT   VARCHAR(20),
    AMOUNT                DECIMAL(15,2) NOT NULL,
    CURRENCY              VARCHAR(3)    NOT NULL,
    TRANSACTION_TYPE      VARCHAR(20)   NOT NULL,
    STATUS                VARCHAR(15)   NOT NULL,
    BRANCH_CODE           VARCHAR(10),
    CREATED_AT            DATETIME,                 -- Kinetica DATETIME maps to epoch-ms internally
    DESCRIPTION           VARCHAR(200),

    -- ETL audit columns added during pipeline ingestion
    INGESTION_TS          BIGINT,                   -- System.currentTimeMillis() at insert time
    PIPELINE_BATCH_ID     VARCHAR(36),              -- optional: Camel exchange ID for traceability

    PRIMARY KEY (TRANSACTION_ID)
);

-- ============================================================
-- Optional: column-store tier hint (Kinetica cold/warm tiering)
-- ============================================================
-- ALTER TABLE BANK_TRANSACTIONS_TARGET
--     SET PROPERTIES (TIER_STRATEGY = 'distributed');

-- ============================================================
-- Verification query — uncomment and run to confirm DDL applied
-- ============================================================
-- SELECT COUNT(*) FROM BANK_TRANSACTIONS_TARGET;
