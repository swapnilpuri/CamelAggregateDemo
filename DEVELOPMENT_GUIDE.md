# Camel Banking ETL Pipeline — Development Guide

A Spring Boot 3.x + Apache Camel 4.x pipeline that reads banking records from **Oracle XE**,
transforms them through four SEDA-connected routes, and bulk-inserts into **Kinetica**.
Includes a live React dashboard, a REST-driven data seeder, and Resilience4j fault tolerance.

---

## Table of Contents

1. [Architecture](#architecture)
2. [Tech Stack](#tech-stack)
3. [Prerequisites](#prerequisites)
4. [Quick Start](#quick-start)
5. [Oracle XE — Access Guide](#oracle-xe--access-guide)
6. [Kinetica — Access Guide](#kinetica--access-guide)
7. [REST API Reference](#rest-api-reference)
8. [React Frontend](#react-frontend)
9. [Data Seeder](#data-seeder)
10. [Project Structure](#project-structure)

---

## Architecture

```
┌─────────────┐     Route 1          Route 2          Route 3          Route 4
│  Oracle XE  │──► fetchFromOracle ──► processBatch ──► transformRecord ──► insertToKinetica ──► Kinetica
│BANK_TRANS.. │    JDBC cursor        split batch       placeholder         aggregate 10 000
└─────────────┘    → batchQueue       → transformQ      → insertQueue       circuit breaker
                      (SEDA, 200)       (SEDA, 5000)     (SEDA, 5000)       JDBC bulk INSERT
```

**Four routes — all defined in Spring XML DSL (`src/main/resources/camel/routes.xml`):**

| Route | From | Concurrency | Role |
|---|---|---|---|
| `fetchFromOracle` | `timer` (once) | — | JDBC StreamList → 500-row batches → seda:batchQueue |
| `processBatch` | `seda:batchQueue` | 4 | Split batch → individual rows → seda:transformQueue |
| `transformRecord` | `seda:transformQueue` | 8 | Per-row transform → seda:insertQueue |
| `insertToKinetica` | `seda:insertQueue` | 6 | Aggregate 10 000 rows → Resilience4j CB → Kinetica JDBC |

A fifth route (`manualTrigger`) accepts `direct:triggerPipeline` calls from the REST API for UI-driven re-runs.

---

## Tech Stack

| Layer | Technology | Version |
|---|---|---|
| Runtime | Java | 21 |
| Framework | Spring Boot | 3.3.5 |
| Integration | Apache Camel | 4.8.2 |
| Route DSL | Spring XML DSL | — |
| Fault tolerance | Resilience4j (via Camel) | — |
| Source DB | Oracle XE | 21c (Docker) |
| Target DB | Kinetica | 7.1.9 (Docker) |
| Fake data | DataFaker | 2.3.1 |
| Frontend build | Vite + React | 5 / 18 |
| Pipeline diagram | React Flow | 11 |
| Charts | Recharts | 2 |
| Styling | Tailwind CSS | 3 |
| Live updates | Server-Sent Events (SSE) | — |

---

## Prerequisites

| Tool | Required version | Notes |
|---|---|---|
| JDK | 21+ | GraalVM CE or Oracle GraalVM for native-image |
| Maven | 3.9+ | Or use the included `./mvnw` wrapper |
| Docker Desktop | 4.x | Needs ≥ 8 GB RAM allocated |
| Node.js | 18+ | For the React frontend |
| npm | 9+ | Bundled with Node |

---

## Quick Start

### 1 — Start the databases

```bash
docker compose up -d
```

Starts `oracle-xe` (port 1521) and `kinetica` (ports 8080, 9191).  
Oracle runs the seed DDL (`src/main/resources/db/oracle-init.sql`) on **first boot only** — wait ~60 s for the health-check to pass.

> **If `BANK_TRANSACTIONS` is missing (ORA-00942):** the named Docker volume `oracle-data`
> already exists from a previous run, so Oracle skipped the init script. Fix with:
> ```bash
> docker compose down -v   # removes oracle-data and kinetica-data volumes
> docker compose up -d     # re-initialises both databases from scratch
> ```
> After recreating volumes, re-run the Kinetica DDL (see [Kinetica — Access Guide](#kinetica--access-guide)).

### 2 — Start Spring Boot

```bash
./mvnw spring-boot:run
```

Spring Boot starts on **http://localhost:8081**.  
`spring-boot-docker-compose` will also start the containers automatically if they are not already running.

### 3 — Start the React frontend

```bash
cd frontend
npm install        # first time only
npm run dev
```

Open **http://localhost:5173**.

### 4 — Seed data (optional)

If you want more rows than the 500 seeded by `oracle-init.sql`:

```bash
curl -X POST "http://localhost:8081/api/seed?count=50000"
```

### 5 — Run the pipeline

```bash
curl -X POST http://localhost:8081/api/pipeline/start
```

Or click **Run Pipeline** in the React UI.

---

## Oracle XE — Access Guide

### Connection details

| Property | Value |
|---|---|
| Host | `localhost` |
| Port | `1521` |
| Service name | `XEPDB1` |
| App username | `bankuser` |
| App password | `bankpass` |
| SYS password | `oracle` |
| JDBC URL | `jdbc:oracle:thin:@localhost:1521/XEPDB1` |

---

### Web UI — Oracle APEX / SQL Developer Web

> **Important:** The `gvenzl/oracle-xe:21-slim` image used in `docker-compose.yml` does **not**
> include APEX or ORDS. To get a browser-based SQL worksheet, switch the image to the full variant.

**Step 1 — change the image in `docker-compose.yml`:**

```yaml
oracle:
  image: gvenzl/oracle-xe:21-full   # was: 21-slim
  ports:
    - "1521:1521"
    - "8181:8080"    # 8080 is taken by Kinetica — map APEX to 8181
```

**Step 2 — restart:**

```bash
docker compose up -d --force-recreate oracle
```

**Step 3 — access in your browser:**

| Interface | URL | Credentials |
|---|---|---|
| Oracle APEX | http://localhost:8181/apex | Workspace: `INTERNAL` · User: `ADMIN` · Password: `oracle` |
| SQL Developer Web | http://localhost:8181/ords/sql-developer | Same as APEX admin |

> First boot of the full image takes 5–10 minutes while APEX is configured.

---

### VS Code — Oracle Developer Tools (official extension)

This is Oracle's own extension. It provides a full SQL worksheet, schema browser, PL/SQL
debugger, and Table Data Editor directly inside VS Code.

**Install:**

1. Open the Extensions panel (`Ctrl+Shift+X`)
2. Search for **Oracle Developer Tools for VS Code**  
   Extension ID: `Oracle.oraclevs`
3. Click **Install**

> The extension requires **Oracle Instant Client** (version 19+).  
> Download from: https://www.oracle.com/database/technologies/instant-client/downloads.html  
> Choose the *Basic* or *Basic Light* package for your OS and follow Oracle's setup instructions.

**Create a connection:**

1. Open the **Oracle** panel in the Activity Bar (database cylinder icon)
2. Click **+** → **Create Connection**
3. Fill in:

```
Connection Type : Basic
Host            : localhost
Port            : 1521
Service Name    : XEPDB1
Username        : bankuser
Password        : bankpass
```

4. Click **Create Connection** — the `BANK_TRANSACTIONS` table appears under **Tables**

---

### VS Code — SQLTools (community alternative, no Instant Client needed)

SQLTools uses a Node.js-based Oracle driver that connects over TCP — no client library required.

**Install:**

1. Install **SQLTools** (`mtxr.sqltools`)
2. Install **SQLTools Oracle Driver** (`Oracle.sqltools-oracle-driver`)  
   *(This is the official Oracle-published SQLTools driver, released 2024)*

**Add a connection** (`Ctrl+Shift+P` → *SQLTools: Add New Connection*):

```
Driver     : Oracle
Name       : Oracle XE (local)
Host       : localhost
Port       : 1521
Database   : XEPDB1
Username   : bankuser
Password   : bankpass
```

**Useful queries to run immediately:**

```sql
-- Row count
SELECT COUNT(*) FROM BANK_TRANSACTIONS;

-- Sample rows
SELECT * FROM BANK_TRANSACTIONS FETCH FIRST 20 ROWS ONLY;

-- Distribution by transaction type
SELECT TRANSACTION_TYPE, STATUS, COUNT(*) AS CNT
FROM   BANK_TRANSACTIONS
GROUP  BY TRANSACTION_TYPE, STATUS
ORDER  BY CNT DESC;
```

---

### DBeaver (desktop GUI alternative)

DBeaver Community Edition bundles Oracle JDBC — no separate Instant Client needed.

1. Download from https://dbeaver.io/download/
2. **New Connection** → select **Oracle**
3. Fill in host `localhost`, port `1521`, database `XEPDB1`, user `bankuser`, password `bankpass`
4. Click **Test Connection** — DBeaver auto-downloads the `ojdbc` driver on first use

---

### SQL*Plus inside the container (no extra tools)

```bash
# Connect as the app user
docker exec -it oracle-xe sqlplus bankuser/bankpass@XEPDB1

# Connect as SYS
docker exec -it oracle-xe sqlplus sys/oracle@XEPDB1 as sysdba
```

---

## Kinetica — Access Guide

### Connection details

| Property | Value |
|---|---|
| Host | `localhost` |
| REST API port | `9191` |
| Admin UI port | `8080` |
| Username | `admin` |
| Password | `kntAUG30` |
| JDBC URL | `jdbc:kinetica:URL=http://localhost:9191;Username=admin;Password=kntAUG30;` |
| JDBC driver class | `com.kinetica.jdbc.Driver` |

---

### Web UI — GAdmin

GAdmin is Kinetica's built-in administration and SQL console. It starts with the container and
requires no additional setup.

**URL:** http://localhost:8080

| Login field | Value |
|---|---|
| Username | `admin` |
| Password | `kntAUG30` |

**What you can do in GAdmin:**

- **SQL worksheet** — run DDL and DML against the target database
- **Table explorer** — browse `BANK_TRANSACTIONS_TARGET` schema and row counts
- **System status** — memory, CPU, storage, and cluster health
- **Query history** — inspect recently executed statements

**Run the Kinetica DDL** (first-time setup, after the container is healthy):

1. Open GAdmin → **SQL** tab
2. Paste the contents of `src/main/resources/db/kinetica-init.sql`
3. Click **Execute**

Or via `docker exec`:

```bash
docker exec kinetica bash -c \
  "gpudb_execute_sql -u admin -p kntAUG30 < /opt/gpudb/kinetica-init.sql"
```

---

### VS Code — SQLTools with Kinetica JDBC

There is no dedicated Kinetica VS Code extension. The most practical VS Code route is
**SQLTools** with a JDBC bridge.

**Step 1 — Install extensions:**

1. **SQLTools** (`mtxr.sqltools`)
2. **SQLTools JDBC Driver** (`SQLTools.sqltools-driver-jdbc`)

**Step 2 — Place the Kinetica JDBC jar** somewhere on your machine, e.g.:

```
C:\tools\kinetica-jdbc-7.1.9.9-jar-with-dependencies.jar
```

Download from: https://github.com/kineticadb/kinetica-api-java/releases/tag/v7.1.9.9

**Step 3 — Add a connection** (`.vscode/settings.json` or via the SQLTools UI):

```json
{
  "sqltools.connections": [
    {
      "name": "Kinetica (local)",
      "driver": "JDBC",
      "jdbcDriver": "com.kinetica.jdbc.Driver",
      "jdbcURL": "jdbc:kinetica:URL=http://localhost:9191;Username=admin;Password=kntAUG30;",
      "driverPath": ["C:\\tools\\kinetica-jdbc-7.1.9.9-jar-with-dependencies.jar"]
    }
  ]
}
```

**Useful queries:**

```sql
-- Confirm target table exists
SELECT COUNT(*) FROM BANK_TRANSACTIONS_TARGET;

-- Check latest inserts
SELECT TRANSACTION_ID, ACCOUNT_NUMBER, AMOUNT, CURRENCY, INGESTION_TS
FROM   BANK_TRANSACTIONS_TARGET
ORDER  BY INGESTION_TS DESC
LIMIT  20;

-- Match source vs target
-- (run Oracle query in a separate connection for comparison)
SELECT COUNT(*) FROM BANK_TRANSACTIONS_TARGET;
```

---

### DBeaver — Kinetica via JDBC

1. **New Connection** → **Other** → search for **Kinetica** (DBeaver has a native Kinetica driver entry in newer versions)
2. If not listed: **New Connection** → **Generic JDBC**
3. Set:
   - **JDBC URL:** `jdbc:kinetica:URL=http://localhost:9191;Username=admin;Password=kntAUG30;`
   - **Driver JAR:** browse to `kinetica-jdbc-7.1.9.9-jar-with-dependencies.jar`
   - **Driver class:** `com.kinetica.jdbc.Driver`

---

## REST API Reference

Base URL: `http://localhost:8081`

### Pipeline control

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/pipeline/stream` | SSE stream — emits `PipelineSnapshot` JSON every 500 ms |
| `GET` | `/api/pipeline/metrics` | Single snapshot (polling fallback) |
| `POST` | `/api/pipeline/start` | Trigger a new pipeline run (`direct:triggerPipeline`) |
| `POST` | `/api/pipeline/reset` | Reset all metrics to `IDLE` state |

**`PipelineSnapshot` response shape:**

```json
{
  "pipelineStatus": "RUNNING",
  "startTimeMs": 1712920000000,
  "elapsedMs": 4320,
  "recordsFetchedFromOracle": 0,
  "recordsTransformed": 12400,
  "recordsInsertedToKinetica": 10000,
  "routes": {
    "fetchFromOracle":  { "status": "COMPLETED", "exchangesTotal": 1,     "throughputPerSec": 0.0,  "meanProcessingTimeMs": 2341.0, "throughputHistory": [...] },
    "processBatch":     { "status": "RUNNING",   "exchangesTotal": 100,   "throughputPerSec": 18.0, "meanProcessingTimeMs": 54.2,   "throughputHistory": [...] },
    "transformRecord":  { "status": "RUNNING",   "exchangesTotal": 12400, "throughputPerSec": 420,  "meanProcessingTimeMs": 0.8,    "throughputHistory": [...] },
    "insertToKinetica": { "status": "RUNNING",   "exchangesTotal": 1,     "throughputPerSec": 0.0,  "meanProcessingTimeMs": 890.0,  "throughputHistory": [...] }
  },
  "queues": {
    "batchQueue":     { "currentDepth": 45,  "capacity": 200,  "highWaterMark": 112, "percentFull": 22.5 },
    "transformQueue": { "currentDepth": 832, "capacity": 5000, "highWaterMark": 1240,"percentFull": 16.6 },
    "insertQueue":    { "currentDepth": 210, "capacity": 5000, "highWaterMark": 980, "percentFull": 4.2  }
  }
}
```

---

### Data seeder

| Method | Endpoint | Params | Description |
|---|---|---|---|
| `POST` | `/api/seed` | `?count=N` | Insert N fake rows into `BANK_TRANSACTIONS` (1 – 10 000 000) |
| `GET` | `/api/seed/count` | — | Current row count in Oracle (no writes) |

**Seed request example:**

```bash
curl -X POST "http://localhost:8081/api/seed?count=100000"
```

**Seed response:**

```json
{
  "requestedCount": 100000,
  "insertedCount":  100000,
  "durationMs":     18340,
  "recordsPerSecond": 5453.4,
  "oracleTotalRows":  100500
}
```

**Typical seeder performance on `oracle-xe:21-slim`:**

| Records | Approx. time |
|---|---|
| 1 000 | < 1 s |
| 10 000 | ~2–4 s |
| 100 000 | ~15–30 s |
| 1 000 000 | ~3–6 min |

---

## React Frontend

**URL:** http://localhost:5173 (Vite dev server, proxied to Spring Boot on 8081)

### Layout

```
┌─────────────────────────────────────────────────────────────┐
│  Header: title + connection status badge                    │
├─────────────────────────────────────────────────────────────┤
│  StatsBar: status pill │ elapsed │ fetched/transformed/     │
│             inserted counters │ 🔁 Reset │ ▶ Run Pipeline   │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  React Flow Pipeline Diagram                                │
│  [Oracle] → [Route1] → [batchQ] → [Route2] → [transformQ]  │
│            → [Route3] → [insertQ] → [Route4] → [Kinetica]  │
│            (edges animate blue when route is RUNNING)       │
│                                                             │
├────────────────────────────┬────────────────────────────────┤
│  Throughput Area Chart     │  SEDA Queue Depth bars         │
│  (4 routes × last 15 s)    │  (colour-coded fill level)     │
├─────────────────────────────────────────────────────────────┤
│  Route Metric Cards × 4                                     │
│  exchanges │ failed │ last ms │ mean ms │ rec/s             │
├─────────────────────────────────────────────────────────────┤
│  Seed Panel                                                 │
│  [100] [1k] [10k] [100k] [custom input] [Seed N rows]       │
│  → result: inserted / duration / rows per second            │
└─────────────────────────────────────────────────────────────┘
```

### Visual cues

| Element | Meaning |
|---|---|
| Blue pulsing dot + edge | Route is actively processing |
| Green dot + edge | Route completed successfully |
| Red dot | Route has failures |
| Queue bar green | < 20 % full |
| Queue bar amber | 50–80 % full |
| Queue bar red | > 80 % full — back-pressure risk |
| Circuit breaker (Route 4) falls back | SSE logs WARN in browser DevTools |

### Running in production mode

To serve the React app from Spring Boot (single JAR):

```bash
cd frontend && npm run build        # outputs to frontend/dist/
cp -r frontend/dist/* src/main/resources/static/
./mvnw package
java -jar target/camel-aggregate-demo-0.0.1-SNAPSHOT.jar
```

---

## Data Seeder — `DataSeederService`

Uses **DataFaker 2.3.1** to generate realistic banking records.

**Generated fields:**

| Column | Generator |
|---|---|
| `ACCOUNT_NUMBER` | `faker.numerify("ACC##########")` — e.g. `ACC0048271936` |
| `BENEFICIARY_ACCOUNT` | 70 % same format, 30 % `NULL` |
| `AMOUNT` | Random `0.01 – 99 999.99`, scaled to 2 d.p. |
| `CURRENCY` | Weighted: USD 30 %, EUR 20 %, GBP 10 %, JPY/CHF/CAD/AUD 10 % each |
| `TRANSACTION_TYPE` | CREDIT 29 %, DEBIT 29 %, TRANSFER 14 %, PAYMENT 14 %, WITHDRAWAL 14 % |
| `STATUS` | COMPLETED 70 %, PENDING 20 %, FAILED 10 % |
| `BRANCH_CODE` | `faker.numerify("BR###")` — e.g. `BR042` |
| `CREATED_AT` | Uniform random within the last 2 years |
| `DESCRIPTION` | Compound string: `"Purchase at Acme Corp — Shoes"` / `"Transfer to John Smith ref TXN00481924"` etc. |

Inserts are batched in groups of 500 using `JdbcTemplate.batchUpdate` for efficiency.

---

## Project Structure

```
CamelAggregateDemo/
├── docker-compose.yml                     Oracle XE + Kinetica containers
├── pom.xml                                Maven — Spring Boot 3.3.5 + Camel 4.8.2
│
├── src/main/
│   ├── java/com/example/cameldemo/
│   │   ├── CamelAggregateDemoApplication.java
│   │   │
│   │   ├── config/
│   │   │   └── DataSourceConfig.java      @Primary oracleDataSource + kineticaDataSource
│   │   │
│   │   ├── splitter/
│   │   │   └── RowBatchSplitter.java      Groups JDBC StreamList into List<Map> batches of 500
│   │   │
│   │   ├── aggregation/
│   │   │   └── BatchAggregationStrategy.java  Collects rows into List for bulk insert
│   │   │
│   │   ├── processor/
│   │   │   ├── TransactionTransformer.java     Per-row Oracle→Kinetica transform
│   │   │   └── KineticaBulkInsertProcessor.java  Builds multi-row VALUES INSERT SQL
│   │   │
│   │   ├── seeder/
│   │   │   ├── DataSeederService.java     DataFaker-based Oracle seed (batched JDBC)
│   │   │   └── SeederResult.java          Response record: count / duration / rows/s
│   │   │
│   │   ├── metrics/
│   │   │   ├── PipelineMetricsService.java    In-memory store; @Scheduled queue polling
│   │   │   ├── CamelMetricsEventNotifier.java Camel EventNotifier → PipelineMetricsService
│   │   │   ├── PipelineSnapshot.java          Top-level SSE payload record
│   │   │   ├── RouteMetricSnapshot.java       Per-route metrics record
│   │   │   └── QueueMetricSnapshot.java       Per-queue depth record
│   │   │
│   │   └── web/
│   │       ├── MetricsController.java     SSE stream + pipeline start/reset endpoints
│   │       ├── SeederController.java      POST /api/seed + GET /api/seed/count
│   │       └── WebConfig.java             CORS (localhost:5173) + @EnableScheduling
│   │
│   └── resources/
│       ├── camel/
│       │   └── routes.xml                 All 5 Camel routes (Spring XML DSL)
│       ├── db/
│       │   ├── oracle-init.sql            DDL + 500 seed rows (auto-run by Oracle container)
│       │   └── kinetica-init.sql          Target table DDL (run manually via GAdmin)
│       └── application.yml               Datasources, Camel config, Resilience4j
│
└── frontend/                              Vite + React 18 dashboard
    ├── package.json
    ├── vite.config.js                     Dev server with /api proxy to :8081
    ├── tailwind.config.js
    └── src/
        ├── App.jsx                        Root layout
        ├── index.css                      Tailwind + React Flow overrides
        ├── hooks/
        │   └── usePipelineMetrics.js      SSE EventSource hook + fetch helpers
        └── components/
            ├── StatsBar.jsx               Status, counters, Run/Reset buttons
            ├── PipelineFlow.jsx           React Flow canvas (animated edges)
            ├── ThroughputChart.jsx        Recharts area chart (4 routes × 15 s)
            ├── RouteMetricsCards.jsx      4 detail cards (exchanges, timing, rec/s)
            ├── SeedPanel.jsx              Oracle seeder UI (presets + custom count)
            └── nodes/
                ├── DbNode.jsx             Oracle / Kinetica cylinder node
                ├── RouteNode.jsx          Camel route node (colour-coded by status)
                └── QueueNode.jsx          SEDA queue node with depth gauge bar
```

---

## Camel Route Summary

| Route ID | Trigger | Concurrency | onException |
|---|---|---|---|
| `fetchFromOracle` | `timer:fetchTrigger?repeatCount=1` | — | `SQLException`, `ConnectException` max 3 retries / 2 s |
| `processBatch` | `seda:batchQueue` | 4 consumers | Same |
| `transformRecord` | `seda:transformQueue` | 8 consumers | Same |
| `insertToKinetica` | `seda:insertQueue` | 6 consumers + aggregate 10 000 | Same + Resilience4j CB |
| `manualTrigger` | `direct:triggerPipeline` | — | Same |

**Circuit breaker (`insertToKinetica`):**

| Parameter | Value |
|---|---|
| `circuitBreakerName` | `kineticaInsert` |
| `slidingWindowSize` | 10 calls |
| `failureRateThreshold` | 50 % |
| `waitDurationInOpenState` | 10 s |
| `permittedCallsInHalfOpen` | 3 |
| Auto-transition OPEN→HALF-OPEN | enabled |

---

## Native Image (GraalVM)

```bash
# Requires GraalVM JDK 21 with native-image component
./mvnw -Pnative native:compile

# Run the native binary
./target/camel-aggregate-demo
```

> Native compilation takes ~5 minutes on a modern laptop. The resulting binary starts in < 100 ms
> versus ~8 s for the JVM version.
