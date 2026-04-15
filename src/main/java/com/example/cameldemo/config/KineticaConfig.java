package com.example.cameldemo.config;

import com.gpudb.BulkInserter;
import com.gpudb.GPUdb;
import com.gpudb.GPUdbException;
import com.gpudb.GenericRecord;
import com.gpudb.Type;
import com.gpudb.WorkerList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Configures the Kinetica GPUdb native client and a shared BulkInserter.
 *
 * The BulkInserter accumulates individual Map→GenericRecord inserts internally
 * and flushes them to Kinetica as a batch when {@code kinetica.batchSize} records
 * have been buffered, or when KineticaInsertBean.scheduledFlush() fires (every 5 s).
 *
 * Multi-head ingest ({@code kinetica.multiHeadIngest=true}) routes writes directly
 * to Kinetica's data ingest workers for maximum throughput, but requires those
 * workers to be reachable by IP.  In a Docker-on-localhost deployment the
 * internal container IPs are unreachable, so the default is {@code false}.
 */
@Configuration
public class KineticaConfig {

    private static final Logger log = LoggerFactory.getLogger(KineticaConfig.class);

    @Value("${kinetica.url:http://localhost:9191}")
    private String url;

    @Value("${kinetica.username:admin}")
    private String username;

    @Value("${kinetica.password:kntAUG30}")
    private String password;

    @Value("${kinetica.table:KINETICA_BANK_TRANSACTIONS}")
    private String tableName;

    @Value("${kinetica.batchSize:10000}")
    private int batchSize;

    /**
     * When true, WorkerList auto-discovers Kinetica data ingest worker nodes for
     * multi-head ingest.  Keep false when running Kinetica in Docker and
     * connecting from the host (workers advertise internal container IPs).
     */
    @Value("${kinetica.multiHeadIngest:false}")
    private boolean multiHeadIngest;

    // ------------------------------------------------------------------
    // GPUdb connection bean
    // ------------------------------------------------------------------

    @Bean
    public GPUdb gpudb() throws GPUdbException {
        GPUdb.Options options = new GPUdb.Options()
                .setUsername(username)
                .setPassword(password);
        log.info("[KineticaConfig] Connecting to Kinetica at {}", url);
        return new GPUdb(url, options);
    }

    // ------------------------------------------------------------------
    // Schema: column definitions shared between KineticaConfig and
    //         KineticaInsertBean (injected as "kineticaType" bean)
    // ------------------------------------------------------------------

    @Bean
    public Type kineticaType() {
        return new Type(Arrays.asList(
            new Type.Column("TRANSACTION_ID",      Long.class),
            new Type.Column("ACCOUNT_NUMBER",      String.class),
            new Type.Column("BENEFICIARY_ACCOUNT", String.class, "nullable"),
            new Type.Column("AMOUNT",              Double.class),
            new Type.Column("CURRENCY",            String.class),
            new Type.Column("TRANSACTION_TYPE",    String.class),
            new Type.Column("STATUS",              String.class),
            new Type.Column("BRANCH_CODE",         String.class, "nullable"),
            new Type.Column("CREATED_AT",          Long.class,   "timestamp", "nullable"),
            new Type.Column("DESCRIPTION",         String.class, "nullable"),
            new Type.Column("INGESTION_TS",        Long.class,   "timestamp"),
            new Type.Column("PIPELINE_BATCH_ID",   String.class, "nullable")
        ));
    }

    // ------------------------------------------------------------------
    // BulkInserter bean — creates the target table if absent
    // ------------------------------------------------------------------

    @Bean
    public BulkInserter<GenericRecord> bulkInserter(GPUdb gpudb, Type kineticaType)
            throws GPUdbException {

        // Register the type schema in Kinetica; returns the typeId string
        String typeId = kineticaType.create(gpudb);
        log.info("[KineticaConfig] Kinetica type registered: {}", typeId);

        // Create target table — no-op if it already exists
        Map<String, String> tableOptions = new HashMap<>();
        tableOptions.put("if_not_exists", "true");
        try {
            gpudb.createTable(tableName, typeId, tableOptions);
            log.info("[KineticaConfig] Table {} created/verified in Kinetica", tableName);
        } catch (GPUdbException e) {
            // Table may already exist with a different typeId; log and continue
            log.warn("[KineticaConfig] createTable note for {}: {}", tableName, e.getMessage());
        }

        log.info("[KineticaConfig] BulkInserter ready — table={}, batchSize={}, multiHead={}",
                tableName, batchSize, multiHeadIngest);

        // The bundled API has two distinct constructor signatures:
        //   single-head: BulkInserter(GPUdb, String, Type,  int, Map<String,String>)
        //   multi-head:  BulkInserter(GPUdb, String, int, Map<String,String>, WorkerList)
        // There is no constructor that accepts both Type and WorkerList.
        if (multiHeadIngest) {
            // WorkerList auto-discovers Kinetica ingest worker nodes.
            // Type is inferred from the existing table by the constructor.
            WorkerList workers = new WorkerList(gpudb);
            return new BulkInserter<GenericRecord>(gpudb, tableName, batchSize, new HashMap<>(), workers);
        } else {
            // Single-head: explicit Type schema, all writes go through the head node.
            return new BulkInserter<GenericRecord>(gpudb, tableName, kineticaType, batchSize, new HashMap<>());
        }
    }
}
