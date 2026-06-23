# Lock Benchmark

This benchmark compares the same transfer endpoint under two account locking strategies:

- `core-banking.account.lock-mode=optimistic`
- `core-banking.account.lock-mode=pessimistic`

Use MySQL for meaningful lock behavior. H2 is useful for smoke tests, but it is not a realistic lock benchmark target.

## Start MySQL

```bash
docker compose up -d mysql
```

Start the app in optimistic mode:

```bash
set SPRING_PROFILES_ACTIVE=mysql
set CORE_BANKING_ACCOUNT_LOCK_MODE=optimistic
.\gradlew.bat bootRun
```

Run JMeter in another terminal:

```bash
mkdir build\jmeter
jmeter -n -t jmeter/corebanking-transfer-lock-benchmark.jmx -l build/jmeter/optimistic.jtl -e -o build/jmeter/optimistic-report
```

You can override load settings:

```bash
jmeter -n -t jmeter/corebanking-transfer-lock-benchmark.jmx -Jthreads=100 -JrampUp=20 -Jduration=120 -l build/jmeter/optimistic-100t.jtl
```

Stop the app, reset the database, then start in pessimistic mode:

```bash
docker compose down -v
docker compose up -d mysql
set SPRING_PROFILES_ACTIVE=mysql
set CORE_BANKING_ACCOUNT_LOCK_MODE=pessimistic
.\gradlew.bat bootRun
```

Run the same JMeter plan:

```bash
jmeter -n -t jmeter/corebanking-transfer-lock-benchmark.jmx -l build/jmeter/pessimistic.jtl -e -o build/jmeter/pessimistic-report
```

## What To Compare

- Throughput: requests/sec
- Average and p95 latency
- Error rate
- Database deadlocks or lock wait timeouts
- Final account balances

The test sends many concurrent transfers from `BENCH-FROM` to `BENCH-TO`. Each request uses a unique idempotency key.

## Expected Shape

Optimistic locking may show higher throughput at low contention, but under a hot single-account workload it can produce more failed commits and retries if retry logic is added later.

Pessimistic locking should serialize updates for the hot account. It may have higher latency, but it should avoid optimistic update conflicts for the same row.
