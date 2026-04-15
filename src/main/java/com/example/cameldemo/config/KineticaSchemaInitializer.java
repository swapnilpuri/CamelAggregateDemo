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
 * Verifies the Kinetica target table at startup.
 *
 * KineticaConfig.bulkInserter() creates KINETICA_BANK_TRANSACTIONS via the
 * GPUdb native API before this runner fires.  This class just confirms the
 * table is queryable and logs the current row count.
 */
@Component
public class KineticaSchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(KineticaSchemaInitializer.class);

    private final JdbcTemplate kineticaJdbc;

    public KineticaSchemaInitializer(@Qualifier("kineticaDataSource") DataSource kineticaDataSource) {
        this.kineticaJdbc = new JdbcTemplate(kineticaDataSource);
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info(">>> KineticaSchemaInitializer: verifying KINETICA_BANK_TRANSACTIONS…");
        try {
            Long count = kineticaJdbc.queryForObject(
                    "SELECT COUNT(*) FROM KINETICA_BANK_TRANSACTIONS", Long.class);
            log.info(">>> KineticaSchemaInitializer: KINETICA_BANK_TRANSACTIONS ready, row count = {}", count);
        } catch (Exception e) {
            // Table may not be fully registered yet if GPUdb API is still initialising
            log.warn(">>> KineticaSchemaInitializer: could not verify table ({})", e.getMessage());
        }
    }
}
