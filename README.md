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

Read recent ledger entries for an account:

```http
GET /api/accounts/{accountId}/ledger-entries?limit=50
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

The application uses MySQL by default. Run the full local stack with Docker Compose:

```bash
cp .env.example .env
docker compose up app
```

On Windows PowerShell:

```powershell
Copy-Item .env.example .env
docker compose up app
```

Run tests:

```bash
./gradlew test
```

Docker Compose keeps only the infrastructure intended for later phases:

```bash
docker compose up -d
```

It starts MySQL and Redis. The application connects to MySQL on host port `3306`.

MySQL local note:

- The app expects the Docker MySQL instance to own host port `3306`.
- If an old local MySQL service already uses `3306`, stop that service first, then start Docker MySQL again.
- If authentication errors mention an old MySQL client/plugin, verify that the app is really connecting to the Docker MySQL container.

## Phase B Seed Data

Phase B seed data is loaded through a Spring Batch job. Batch execution is disabled for the normal `app` service and enabled only for the `seed` service:

```powershell
docker compose run --rm seed
```

After it finishes, run the app normally for API testing:

```powershell
docker compose up app
```

Default seed size:

```text
accounts: 1,000
transfers: 10,000
ledger_entries: 20,000
```

The generated rows use deterministic IDs, so restarting with the seed enabled does not duplicate data:

```text
account_id: PHASEB-ACC-000001
transfer_id: PHASEB-TR-00000001
idempotency_key: phase-b-key-00000001
```

To change the data size:

```env
CORE_BANKING_SEED_ACCOUNT_COUNT=1000
CORE_BANKING_SEED_TRANSFER_COUNT=100000
```

For larger index experiments, the seed can be increased to one million transfers:

```env
CORE_BANKING_SEED_TRANSFER_COUNT=1000000
```

That produces:

```text
accounts: 1,000
transfers: 1,000,000
ledger_entries: 2,000,000
```

## Phase B Lock Benchmark

The transfer benchmark uses JMeter to send concurrent hot-account transfer requests to:

```http
POST /api/transfers
```

The lock mode is controlled by `.env`:

```env
CORE_BANKING_ACCOUNT_LOCK_MODE=pessimistic
```

Set it to `optimistic` or `pessimistic`, rebuild/restart the app, then run the same JMeter plan:

```powershell
docker compose build app
docker compose up -d app
```

JMeter plan:

```text
jmeter/corebanking-transfer-lock-benchmark.jmx
```

Measured hot-account results:

| Lock mode | API | Samples | Average | Min | Max | Std. Dev. | Error % | Throughput |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| optimistic | POST /api/transfers | 9,018 | 180 ms | 20 ms | 1,484 ms | 125 ms | 0.88% | 150/sec |
| pessimistic | POST /api/transfers | 2,256 | 743 ms | 183 ms | 2,813 ms | 288 ms | 0.00% | 37/sec |

Observed behavior:

- Optimistic locking allowed higher throughput, but concurrent updates to the same account caused version conflicts.
- Pessimistic locking removed optimistic lock conflicts by using row-level lock waiting, but response time increased and throughput dropped.
- The main bottleneck in the hot-account scenario is contention on the same account row.

The optimistic conflict response looked like:

```json
{
  "code": "OPTIMISTIC_LOCK_CONFLICT",
  "message": "Concurrent update conflict. Retry the request."
}
```

## Phase B Ledger Read Benchmark

The account ledger read benchmark measures the latest ledger entries for one seeded account:

```http
GET /api/accounts/PHASEB-ACC-000001/ledger-entries?limit=50
```

JMeter plan:

```text
jmeter/corebanking-read-transaction-benchmark.jmx
```

The query pattern is:

```sql
SELECT *
FROM ledger_entries
WHERE account_id = ?
ORDER BY created_at DESC
LIMIT 50;
```

The ledger table has a composite index for this access pattern:

```sql
CREATE INDEX idx_ledger_entries_account_created_at
ON ledger_entries (account_id, created_at DESC);
```

Measured results with 2,000,000 ledger entries:

| Index | API | Samples | Average | Min | Max | Std. Dev. | Error % | Throughput |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| none | GET /api/accounts/PHASEB-ACC-000001/ledger-entries?limit=50 | 1,650 | 194 ms | 7 ms | 3,710 ms | 471 ms | 0.00% | 0.366/sec |
| `idx_ledger_entries_account_created_at` | GET /api/accounts/PHASEB-ACC-000001/ledger-entries?limit=50 | 1,500 | 52 ms | 7 ms | 337 ms | 58 ms | 0.00% | 0.340/sec |

Observed behavior:

- The composite index reduced average response time from 194 ms to 52 ms.
- The max response time dropped from 3,710 ms to 337 ms.
- The standard deviation dropped from 471 ms to 58 ms, so response times became more stable.
- `EXPLAIN` changed to use `idx_ledger_entries_account_created_at` with `type=ref`, avoiding a full scan for the account ledger lookup.

## Next Phases

- Phase B: MySQL schema, indexes, EXPLAIN, N+1 checks, isolation/lock tests, HikariCP tuning, JMeter load tests
- Phase C: Redis cache and distributed lock experiments
- Phase D: split only modules proven by load testing to be bottlenecks
