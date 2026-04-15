-- Oracle XE initialisation script
-- Mounted into gvenzl/oracle-xe at: /container-entrypoint-initdb.d/01-init.sql
-- Runs automatically as APP_USER (bankuser) on first container start.
-- Safe to re-run: drops the table first if it exists.

-- ============================================================
-- Drop existing objects (idempotent re-run support)
-- ============================================================
BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE BANK_TRANSACTIONS CASCADE CONSTRAINTS PURGE';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'DROP TABLE ETL_PROGRESS CASCADE CONSTRAINTS PURGE';
EXCEPTION
    WHEN OTHERS THEN NULL;
END;
/

-- ============================================================
-- DDL
-- ============================================================

CREATE TABLE BANK_TRANSACTIONS (
    TRANSACTION_ID        NUMBER            GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ACCOUNT_NUMBER        VARCHAR2(20)      NOT NULL,
    BENEFICIARY_ACCOUNT   VARCHAR2(20),
    AMOUNT                NUMBER(15, 2)     NOT NULL,
    CURRENCY              VARCHAR2(3)       DEFAULT 'USD' NOT NULL,
    TRANSACTION_TYPE      VARCHAR2(20)      NOT NULL,
    STATUS                VARCHAR2(15)      DEFAULT 'PENDING' NOT NULL,
    BRANCH_CODE           VARCHAR2(10),
    CREATED_AT            TIMESTAMP         DEFAULT SYSTIMESTAMP NOT NULL,
    DESCRIPTION           VARCHAR2(200)
);

-- Note: no explicit index needed on TRANSACTION_ID — the PRIMARY KEY
-- constraint already creates a unique index on that column.

-- ============================================================
-- ETL progress tracking table
-- Managed by PartitionCalculator; one row per 100 000-row partition.
-- STATUS lifecycle: PENDING → IN_PROGRESS → COMPLETED | FAILED
-- ============================================================
CREATE TABLE ETL_PROGRESS (
    PARTITION_ID    NUMBER          PRIMARY KEY,
    RANGE_START     NUMBER          NOT NULL,
    RANGE_END       NUMBER          NOT NULL,
    STATUS          VARCHAR2(20)    DEFAULT 'PENDING' NOT NULL,
    ROWS_PROCESSED  NUMBER          DEFAULT 0,
    UPDATED_AT      TIMESTAMP       DEFAULT SYSTIMESTAMP NOT NULL
);

-- ============================================================
-- Seed data — 500 rows
-- ============================================================
BEGIN
    -- Row 1
    INSERT INTO BANK_TRANSACTIONS (ACCOUNT_NUMBER, BENEFICIARY_ACCOUNT, AMOUNT, CURRENCY, TRANSACTION_TYPE, STATUS, BRANCH_CODE, CREATED_AT, DESCRIPTION)
    VALUES ('ACC0000000001', 'ACC0000000002',   1243.50, 'USD', 'TRANSFER',    'COMPLETED', 'BR001', SYSDATE - 364, 'Wire transfer to savings');
    -- Row 2
    INSERT INTO BANK_TRANSACTIONS (ACCOUNT_NUMBER, BENEFICIARY_ACCOUNT, AMOUNT, CURRENCY, TRANSACTION_TYPE, STATUS, BRANCH_CODE, CREATED_AT, DESCRIPTION)
    VALUES ('ACC0000000002', NULL,                520.00, 'EUR', 'CREDIT',      'COMPLETED', 'BR002', SYSDATE - 363, 'Salary deposit');
    -- Row 3
    INSERT INTO BANK_TRANSACTIONS (ACCOUNT_NUMBER, BENEFICIARY_ACCOUNT, AMOUNT, CURRENCY, TRANSACTION_TYPE, STATUS, BRANCH_CODE, CREATED_AT, DESCRIPTION)
    VALUES ('ACC0000000003', 'ACC0000000010',   3780.75, 'GBP', 'TRANSFER',    'PENDING',   'BR003', SYSDATE - 362, 'International wire');
    -- Row 4
    INSERT INTO BANK_TRANSACTIONS (ACCOUNT_NUMBER, BENEFICIARY_ACCOUNT, AMOUNT, CURRENCY, TRANSACTION_TYPE, STATUS, BRANCH_CODE, CREATED_AT, DESCRIPTION)
    VALUES ('ACC0000000004', NULL,                 99.99, 'USD', 'DEBIT',       'COMPLETED', 'BR001', SYSDATE - 361, 'Online purchase');
    -- Row 5
    INSERT INTO BANK_TRANSACTIONS (ACCOUNT_NUMBER, BENEFICIARY_ACCOUNT, AMOUNT, CURRENCY, TRANSACTION_TYPE, STATUS, BRANCH_CODE, CREATED_AT, DESCRIPTION)
    VALUES ('ACC0000000005', NULL,               2500.00, 'USD', 'WITHDRAWAL',  'COMPLETED', 'BR004', SYSDATE - 360, 'ATM withdrawal');
    -- Row 6
    INSERT INTO BANK_TRANSACTIONS (ACCOUNT_NUMBER, BENEFICIARY_ACCOUNT, AMOUNT, CURRENCY, TRANSACTION_TYPE, STATUS, BRANCH_CODE, CREATED_AT, DESCRIPTION)
    VALUES ('ACC0000000006', 'ACC0000000003',    450.25, 'EUR', 'PAYMENT',     'FAILED',    'BR002', SYSDATE - 359, 'Utility bill payment');
    -- Row 7
    INSERT INTO BANK_TRANSACTIONS (ACCOUNT_NUMBER, BENEFICIARY_ACCOUNT, AMOUNT, CURRENCY, TRANSACTION_TYPE, STATUS, BRANCH_CODE, CREATED_AT, DESCRIPTION)
    VALUES ('ACC0000000007', NULL,              10000.00, 'USD', 'CREDIT',      'COMPLETED', 'BR005', SYSDATE - 358, 'Investment return');
    -- Row 8
    INSERT INTO BANK_TRANSACTIONS (ACCOUNT_NUMBER, BENEFICIARY_ACCOUNT, AMOUNT, CURRENCY, TRANSACTION_TYPE, STATUS, BRANCH_CODE, CREATED_AT, DESCRIPTION)
    VALUES ('ACC0000000008', 'ACC0000000001',   6700.00, 'GBP', 'TRANSFER',    'COMPLETED', 'BR003', SYSDATE - 357, 'Property payment');
    -- Row 9
    INSERT INTO BANK_TRANSACTIONS (ACCOUNT_NUMBER, BENEFICIARY_ACCOUNT, AMOUNT, CURRENCY, TRANSACTION_TYPE, STATUS, BRANCH_CODE, CREATED_AT, DESCRIPTION)
    VALUES ('ACC0000000009', NULL,                 15.49, 'USD', 'DEBIT',       'COMPLETED', 'BR001', SYSDATE - 356, 'Coffee shop');
    -- Row 10
    INSERT INTO BANK_TRANSACTIONS (ACCOUNT_NUMBER, BENEFICIARY_ACCOUNT, AMOUNT, CURRENCY, TRANSACTION_TYPE, STATUS, BRANCH_CODE, CREATED_AT, DESCRIPTION)
    VALUES ('ACC0000000010', 'ACC0000000005',   1875.00, 'EUR', 'PAYMENT',     'PENDING',   'BR002', SYSDATE - 355, 'Rent payment');

    -- Rows 11–500: generated via loop using deterministic expressions
    FOR i IN 11..500 LOOP
        INSERT INTO BANK_TRANSACTIONS (
            ACCOUNT_NUMBER,
            BENEFICIARY_ACCOUNT,
            AMOUNT,
            CURRENCY,
            TRANSACTION_TYPE,
            STATUS,
            BRANCH_CODE,
            CREATED_AT,
            DESCRIPTION
        ) VALUES (
            'ACC' || LPAD(MOD(i, 200) + 1, 10, '0'),

            CASE WHEN MOD(i, 3) = 0
                 THEN 'ACC' || LPAD(MOD(i + 7, 200) + 1, 10, '0')
                 ELSE NULL
            END,

            ROUND(
                CASE MOD(i, 10)
                    WHEN 0 THEN 50000.00 - (i * 3.17)
                    WHEN 1 THEN 100.00   + (i * 1.05)
                    WHEN 2 THEN 9999.99  - (i * 2.33)
                    WHEN 3 THEN 250.50   + (i * 0.75)
                    WHEN 4 THEN 1500.00  + (i * 4.10)
                    WHEN 5 THEN 75.25    + (i * 0.50)
                    WHEN 6 THEN 33333.33 - (i * 5.00)
                    WHEN 7 THEN 888.88   + (i * 1.11)
                    WHEN 8 THEN 12.99    + (i * 0.01)
                    ELSE        5000.00  + (i * 2.50)
                END,
                2
            ),

            CASE MOD(i, 3)
                WHEN 0 THEN 'USD'
                WHEN 1 THEN 'EUR'
                ELSE         'GBP'
            END,

            CASE MOD(i, 5)
                WHEN 0 THEN 'CREDIT'
                WHEN 1 THEN 'DEBIT'
                WHEN 2 THEN 'TRANSFER'
                WHEN 3 THEN 'PAYMENT'
                ELSE         'WITHDRAWAL'
            END,

            CASE MOD(i, 6)
                WHEN 0 THEN 'FAILED'
                WHEN 1 THEN 'PENDING'
                ELSE         'COMPLETED'
            END,

            'BR' || LPAD(MOD(i, 10) + 1, 3, '0'),

            SYSDATE - (354 - MOD(i, 354)),

            CASE MOD(i, 5)
                WHEN 0 THEN 'Salary credit batch ' || i
                WHEN 1 THEN 'Merchant debit ref-'   || i
                WHEN 2 THEN 'Wire transfer TXN-'    || i
                WHEN 3 THEN 'Bill payment INV-'     || i
                ELSE         'ATM withdrawal loc-'  || i
            END
        );
    END LOOP;

    COMMIT;
END;
/
