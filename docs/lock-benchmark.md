# Lock Benchmark

This benchmark compares the same transfer endpoint under two account locking strategies:

- `CORE_BANKING_ACCOUNT_LOCK_MODE=optimistic`
- `CORE_BANKING_ACCOUNT_LOCK_MODE=pessimistic`

The benchmark target is MySQL. The application now uses MySQL by default.

## Run The Stack

Create a local environment file if it does not exist:

```powershell
Copy-Item .env.example .env
```

Start or restart the app:

```powershell
docker compose build app
docker compose up -d app
```

Seed benchmark data:

```powershell
docker compose run --rm seed
```

## Switch Lock Mode

Edit `.env`:

```env
CORE_BANKING_ACCOUNT_LOCK_MODE=optimistic
```

or:

```env
CORE_BANKING_ACCOUNT_LOCK_MODE=pessimistic
```

Then rebuild and restart:

```powershell
docker compose build app
docker compose up -d app
```

## Run JMeter

JMeter plan:

```text
jmeter/corebanking-transfer-lock-benchmark.jmx
```

Recommended local JMeter home:

```text
C:\Users\jiho5\tools\apache-jmeter-5.6.3
```

CLI example:

```powershell
C:\Users\jiho5\tools\apache-jmeter-5.6.3\bin\jmeter.bat -n -t jmeter/corebanking-transfer-lock-benchmark.jmx -l build/jmeter/result.jtl
```

## Measured Hot-Account Results

| Lock mode | API | Samples | Average | Min | Max | Std. Dev. | Error % | Throughput |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| optimistic | POST /api/transfers | 9,018 | 180 ms | 20 ms | 1,484 ms | 125 ms | 0.88% | 150/sec |
| pessimistic | POST /api/transfers | 2,256 | 743 ms | 183 ms | 2,813 ms | 288 ms | 0.00% | 37/sec |

## What To Compare

- Error rate
- Throughput
- Average and p95 latency
- Max latency
- Database lock waits
- Final account balances

## Interpretation

Optimistic locking allowed higher throughput, but concurrent updates to the same account produced version conflicts:

```json
{
  "code": "OPTIMISTIC_LOCK_CONFLICT",
  "message": "Concurrent update conflict. Retry the request."
}
```

Pessimistic locking serialized updates for the hot account row. It removed optimistic lock conflicts, but average latency increased and throughput dropped.

The current hot-account bottleneck is contention on the same account row.
