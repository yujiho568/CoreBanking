# CoreBanking

CoreBanking is a modular monolith for a banking transfer domain. It replaces the archived Kafka-based MSA attempt with a single deployable Spring Boot application focused on measurable database behavior, transaction correctness, and simple module boundaries.

## Phase A Scope

- Single Spring Boot application
- Package-based module boundaries
- Account transfer happy path
- Single `@Transactional` transfer flow
- BigDecimal money handling
- Account optimistic locking with `@Version`
- Double-entry append-only ledger entries
- Idempotency key for transfer creation
- In-process notification event after commit

Kafka, Saga compensation, distributed databases, Zookeeper, and Zipkin are intentionally removed.

## Structure

```text
com.corebanking
|-- CoreBankingApplication.java
|-- common
|   |-- dto
|   |-- event
|   `-- exception
|-- account
|   |-- controller
|   |-- dto
|   |-- entity
|   |-- exception
|   |-- repository
|   `-- service
|-- transfer
|   |-- controller
|   |-- dto
|   |-- entity
|   |-- exception
|   |-- repository
|   `-- service
|-- ledger
|   |-- entity
|   |-- repository
|   `-- service
`-- notification
    `-- service
```

MVC package rules:

- HTTP endpoints live in `controller`.
- Request/response records and service commands live in `dto`.
- Transaction use cases live in `service`.
- JPA models and enums live in `entity`.
- Spring Data repositories live in `repository`.
- Business exceptions live in `exception`.
- Post-commit notifications use Spring application events.

## Transfer Flow

`TransferService.execute()` runs the Phase A happy path in one database transaction:

1. Check `idempotencyKey`.
2. Create a `Transfer`.
3. Reserve source account balance.
4. Commit source debit and destination credit.
5. Record two ledger entries: `DEBIT` and `CREDIT`.
6. Mark transfer as `COMPLETED`.
7. Publish `TransferCompletedEvent`; notification listener handles it after commit.

If any step fails, the database transaction rolls back. There is no Saga compensation path in Phase A.

## API

Create a transfer:

```http
POST /api/transfers
Content-Type: application/json

{
  "idempotencyKey": "demo-001",
  "fromAccountId": "ACC-001",
  "toAccountId": "ACC-002",
  "amount": 10000.00
}
```

Read a transfer:

```http
GET /api/transfers/{transferId}
```

Read an account:

```http
GET /api/accounts/{accountId}
```

Browser test page:

```text
http://localhost:8080
```

The page can refresh seeded account balances, create a new idempotency key, and submit a transfer request.

Seed accounts:

```text
ACC-001 1000000.00
ACC-002  500000.00
ACC-003  250000.00
```

## Running

The default profile uses H2 in-memory DB:

```bash
./gradlew bootRun
```

Run against the single MySQL instance from Docker Compose:

```bash
docker compose up -d mysql
SPRING_PROFILES_ACTIVE=mysql ./gradlew bootRun
```

On Windows PowerShell:

```powershell
docker compose up -d mysql
$env:SPRING_PROFILES_ACTIVE = "mysql"
.\gradlew.bat bootRun
```

Run tests:

```bash
./gradlew test
```

Docker Compose keeps only the infrastructure intended for later phases:

```bash
docker compose up -d
```

It starts MySQL and Redis. Phase A defaults to H2 for fast local iteration.

MySQL local note:

- The app expects the Docker MySQL instance to own host port `3306`.
- If an old local MySQL service already uses `3306`, stop that service first, then start Docker MySQL again.
- If authentication errors mention an old MySQL client/plugin, verify that the app is really connecting to the Docker MySQL container.

## Next Phases

- Phase B: MySQL schema, indexes, EXPLAIN, N+1 checks, isolation/lock tests, HikariCP tuning, k6 load tests
- Phase C: Redis cache and distributed lock experiments
- Phase D: split only modules proven by load testing to be bottlenecks
