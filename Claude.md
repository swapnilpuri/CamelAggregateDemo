# Project: Camel Banking ETL Pipeline

## What this is
A Spring Boot 3.x + Apache Camel project that reads millions of banking records from Oracle DB, transforms them, and bulk-inserts into Kinetica DB. Uses Spring XML DSL for route definitions (not Java DSL).

## Architecture
6 Camel routes using range-based partitioning for scalable, resumable ETL:

- **resumeCheck**: startup timer → queries ETL_PROGRESS for PENDING/FAILED partitions → resumes them via seda:fetchPartition, or delegates to partitionController for a fresh run
- **partitionController**: direct trigger → `PartitionCalculator.calculatePartitions()` → splits `List<RangePartition>` → seda:fetchPartition
- **fetchPartition**: seda:fetchPartition(6 concurrent) → mark IN_PROGRESS → `SELECT * WHERE TRANSACTION_ID BETWEEN start AND end` → split individual rows → seda:transformQueue → mark COMPLETED (or FAILED)
- **transformRecord**: seda:transformQueue(8 concurrent) → transformation placeholder → seda:insertQueue
- **insertToKinetica**: seda:insertQueue(8 concurrent) → Resilience4j circuit breaker → `KineticaInsertBean.insert()` → `BulkInserter<GenericRecord>` (batches + flushes to Kinetica natively; no JDBC)
- **manualTrigger**: direct:triggerPipeline (UI "Run Pipeline" button) → DELETE ETL_PROGRESS → direct:partitionController

## Partitioning
- `PartitionCalculator` queries `MIN/MAX(TRANSACTION_ID)` and divides the range into 100,000-row chunks
- Each chunk is tracked as a row in `ETL_PROGRESS` (STATUS: PENDING → IN_PROGRESS → COMPLETED | FAILED)
- On restart, `resumeCheck` re-queues PENDING/FAILED partitions instead of reprocessing everything
- The partition queue (`seda:fetchPartition`) holds up to 500 partition descriptors

## Tech stack
- Spring Boot 3.x, Apache Camel 4.x, Spring XML DSL
- Oracle XE (Docker), Kinetica 7.1.9 (Docker)
- Resilience4j circuit breaker for retry/fault tolerance
- GraalVM native-image support

## Key conventions
- All Camel routes live in `src/main/resources/camel/routes.xml` (Spring XML DSL only)
- Java classes are only for beans, aggregation strategies, and config — not route definitions
- Docker Compose manages both databases
- Banking domain: `BANK_TRANSACTIONS` (source) → `BANK_TRANSACTIONS_TARGET` (sink)
- ETL progress tracking: `ETL_PROGRESS` table in Oracle

## Key Java classes
- `partition/RangePartition` — POJO: partitionId, startId, endId
- `partition/PartitionCalculator` — calculates partitions, writes ETL_PROGRESS, converts rows back to RangePartition for resume
- `aggregation/BatchAggregationStrategy` — collects individual rows into List<Map> batches
- `processor/KineticaBulkInsertProcessor` — builds multi-row INSERT SQL for Kinetica
- `config/KineticaConfig` — GPUdb bean, `Type` schema bean, `BulkInserter<GenericRecord>` bean; creates `KINETICA_BANK_TRANSACTIONS` via GPUdb API on startup
- `processor/KineticaInsertBean` — converts `Map<String,Object>` rows to `GenericRecord`, calls `BulkInserter.insert()`; scheduled 5-second flush; `@PreDestroy` flush on shutdown
- `config/KineticaSchemaInitializer` — verifies `KINETICA_BANK_TRANSACTIONS` is queryable at startup (row count log)
- `metrics/PipelineMetricsService` — in-memory metrics store, SSE broadcast

## Build & run
- `docker-compose up -d` to start databases
- `./mvnw spring-boot:run` for JVM mode
- `./mvnw -Pnative native:compile` for GraalVM native image

## Notes
- **Kinetica write path**: GPUdb `BulkInserter<GenericRecord>` (native API) — no JDBC for inserts. JDBC datasource is kept only for `KineticaSchemaInitializer` verification.
- **Multi-head ingest**: disabled by default (`kinetica.multiHeadIngest=false`). Enabling it requires Kinetica worker nodes to be reachable by IP — not possible when running Docker on localhost (workers advertise internal container IPs).
- **BulkInserter flush**: auto-flushes at `kinetica.batchSize` (10,000) records. `KineticaInsertBean.scheduledFlush()` drains partial batches every 5 s.
- **Kinetica table**: `KINETICA_BANK_TRANSACTIONS` in the `ki_home` schema, created via GPUdb API at startup.
- **JDBC WARN on each connection**: Kinetica JDBC driver probes internal Docker IP — harmless, falls back to `localhost:9191`.
- `ETL_PROGRESS` lives in Oracle (same datasource as BANK_TRANSACTIONS).
