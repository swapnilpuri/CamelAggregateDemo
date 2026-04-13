# Project: Camel Banking ETL Pipeline

## What this is
A Spring Boot 3.x + Apache Camel project that reads millions of banking records from Oracle DB, transforms them, and bulk-inserts into Kinetica DB. Uses Spring XML DSL for route definitions (not Java DSL).

## Architecture
- 4 Camel routes connected via SEDA queues with configurable concurrency
- Route 1: JDBC cursor fetch from Oracle → split into batches → seda:batchQueue
- Route 2: seda:batchQueue → split into individual records → seda:transformQueue  
- Route 3: seda:transformQueue → transformation (placeholder) → seda:insertQueue
- Route 4: seda:insertQueue → aggregate 10000 records → batch insert into Kinetica

## Tech stack
- Spring Boot 3.x, Apache Camel 4.x, Spring XML DSL
- Oracle XE (Docker), Kinetica (Docker)
- Resilience4j circuit breaker for retry/fault tolerance
- GraalVM native-image support

## Key conventions
- All Camel routes live in src/main/resources/camel/routes.xml (Spring XML DSL only)
- Java classes are only for beans, aggregation strategies, and config — not route definitions
- Docker Compose manages both databases
- Banking domain: BANK_TRANSACTIONS table

## Build & run
- `docker-compose up -d` to start databases
- `./mvnw spring-boot:run` for JVM mode
- `./mvnw -Pnative native:compile` for GraalVM native image