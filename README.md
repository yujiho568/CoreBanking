# CoreBanking

CoreBanking은 계좌 이체 도메인을 다루는 모듈러 모놀리스 프로젝트입니다.
기존 Kafka 기반 MSA 시도 대신, 하나의 Spring Boot 애플리케이션 안에서 데이터베이스 동작, 트랜잭션 정합성, 모듈 경계, 성능 병목을 측정하는 데 초점을 둡니다.

## Phase A 범위

- 단일 Spring Boot 애플리케이션
- 패키지 기반 모듈 경계
- 계좌 이체 happy path
- 단일 `@Transactional` 이체 흐름
- `BigDecimal` 기반 금액 처리
- `@Version` 기반 계좌 optimistic locking
- 복식부기 방식의 append-only 원장 기록
- 이체 생성 idempotency key
- 커밋 이후 실행되는 인프로세스 알림 이벤트

Kafka, Saga 보상 트랜잭션, 분산 데이터베이스, Zookeeper, Zipkin은 의도적으로 제거했습니다.

## 구조

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

MVC 패키지 규칙:

- HTTP 엔드포인트는 `controller`에 둡니다.
- 요청/응답 record와 서비스 command는 `dto`에 둡니다.
- 트랜잭션 유스케이스는 `service`에 둡니다.
- JPA 모델과 enum은 `entity`에 둡니다.
- Spring Data repository는 `repository`에 둡니다.
- 비즈니스 예외는 `exception`에 둡니다.
- 커밋 이후 알림은 Spring application event로 처리합니다.

## 이체 흐름

`TransferWriteService.execute()`는 Phase A 기준 happy path를 하나의 DB 트랜잭션으로 처리합니다.

1. `idempotencyKey` 중복 여부를 확인합니다.
2. `Transfer`를 생성합니다.
3. 출금 계좌의 금액을 예약합니다.
4. 출금 계좌 차감과 입금 계좌 증가를 확정합니다.
5. `DEBIT`, `CREDIT` 원장 기록 2건을 저장합니다.
6. 이체 상태를 `COMPLETED`로 변경합니다.
7. `TransferCompletedEvent`를 발행하고, 알림 리스너는 커밋 이후 처리합니다.

중간 단계에서 실패하면 DB 트랜잭션은 롤백됩니다. Phase A에는 Saga 보상 경로가 없습니다.

## API

이체 생성:

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

이체 조회:

```http
GET /api/transfers/{transferId}
```

계좌 조회:

```http
GET /api/accounts/{accountId}
```

계좌별 최근 원장 조회:

```http
GET /api/accounts/{accountId}/ledger-entries?limit=50
```

브라우저 테스트 페이지:

```text
http://localhost:8080
```

페이지에서 시드 계좌 잔액 조회, idempotency key 생성, 이체 요청을 테스트할 수 있습니다.

기본 시드 계좌:

```text
ACC-001 1000000.00
ACC-002  500000.00
ACC-003  250000.00
```

## 실행

애플리케이션은 기본적으로 MySQL을 사용합니다. Docker Compose로 로컬 스택을 실행합니다.

```bash
cp .env.example .env
docker compose up app
```

Windows PowerShell:

```powershell
Copy-Item .env.example .env
docker compose up app
```

테스트 실행:

```bash
./gradlew test
```

전체 인프라 실행:

```bash
docker compose up -d
```

Docker Compose는 MySQL과 Redis를 실행합니다. 애플리케이션은 기본적으로 호스트 `3306` 포트의 MySQL에 연결합니다.

MySQL 로컬 주의사항:

- 앱은 Docker MySQL 인스턴스가 호스트 `3306` 포트를 사용한다고 가정합니다.
- 기존 로컬 MySQL 서비스가 `3306`을 사용 중이면 먼저 중지한 뒤 Docker MySQL을 실행합니다.
- 인증 오류가 오래된 MySQL client/plugin을 언급하면, 앱이 실제로 Docker MySQL에 연결 중인지 확인합니다.

## Phase B 시드 데이터

Phase B 시드 데이터는 Spring Batch job으로 적재합니다. 일반 `app` 서비스에서는 Batch 실행을 끄고, `seed` 서비스에서만 활성화합니다.

```powershell
docker compose run --rm seed
```

시드 적재가 끝나면 앱을 정상 실행합니다.

```powershell
docker compose up app
```

기본 시드 크기:

```text
accounts: 1,000
transfers: 10,000
ledger_entries: 20,000
```

생성되는 ID는 결정적이므로 seed job을 다시 실행해도 중복 저장되지 않습니다.

```text
account_id: PHASEB-ACC-000001
transfer_id: PHASEB-TR-00000001
idempotency_key: phase-b-key-00000001
```

데이터 크기 변경:

```env
CORE_BANKING_SEED_ACCOUNT_COUNT=1000
CORE_BANKING_SEED_TRANSFER_COUNT=100000
```

큰 인덱스 실험이 필요하면 이체 데이터를 100만 건까지 늘릴 수 있습니다.

```env
CORE_BANKING_SEED_TRANSFER_COUNT=1000000
```

이 설정은 다음 데이터를 생성합니다.

```text
accounts: 1,000
transfers: 1,000,000
ledger_entries: 2,000,000
```

## Phase B 락 벤치마크

이체 벤치마크는 JMeter로 hot account에 동시 이체 요청을 보냅니다.

```http
POST /api/transfers
```

락 모드는 `.env`에서 제어합니다.

```env
CORE_BANKING_ACCOUNT_LOCK_MODE=pessimistic
```

`optimistic` 또는 `pessimistic`으로 설정한 뒤 앱을 다시 빌드/실행하고 같은 JMeter 플랜을 실행합니다.

```powershell
docker compose build app
docker compose up -d app
```

JMeter 플랜:

```text
jmeter/corebanking-transfer-lock-benchmark.jmx
```

측정된 hot account 결과:

| Lock mode | API | Samples | Average | Min | Max | Std. Dev. | Error % | Throughput |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| optimistic | POST /api/transfers | 9,018 | 180 ms | 20 ms | 1,484 ms | 125 ms | 0.88% | 150/sec |
| pessimistic | POST /api/transfers | 2,256 | 743 ms | 183 ms | 2,813 ms | 288 ms | 0.00% | 37/sec |

관찰 결과:

- Optimistic locking은 처리량이 높았지만, 같은 계좌에 대한 동시 업데이트에서 version conflict가 발생했습니다.
- Pessimistic locking은 optimistic lock conflict를 제거했지만 row-level lock 대기 때문에 응답 시간이 증가하고 처리량이 감소했습니다.
- hot account 시나리오의 핵심 병목은 같은 계좌 row에 대한 경합입니다.

Optimistic conflict 응답 예시:

```json
{
  "code": "OPTIMISTIC_LOCK_CONFLICT",
  "message": "Concurrent update conflict. Retry the request."
}
```

## Phase B 원장 조회 벤치마크

계좌 원장 조회 벤치마크는 하나의 시드 계좌에 대해 최신 원장 기록을 조회합니다.

```http
GET /api/accounts/PHASEB-ACC-000001/ledger-entries?limit=50
```

JMeter 플랜:

```text
jmeter/corebanking-read-transaction-benchmark.jmx
```

쿼리 패턴:

```sql
SELECT *
FROM ledger_entries
WHERE account_id = ?
ORDER BY created_at DESC
LIMIT 50;
```

이 접근 패턴을 위해 원장 테이블에는 복합 인덱스가 있습니다.

```sql
CREATE INDEX idx_ledger_entries_account_created_at
ON ledger_entries (account_id, created_at DESC);
```

2,000,000건 원장 데이터 기준 측정 결과:

| Index | API | Samples | Average | Min | Max | Std. Dev. | Error % | Throughput |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| none | GET /api/accounts/PHASEB-ACC-000001/ledger-entries?limit=50 | 1,650 | 194 ms | 7 ms | 3,710 ms | 471 ms | 0.00% | 0.366/sec |
| `idx_ledger_entries_account_created_at` | GET /api/accounts/PHASEB-ACC-000001/ledger-entries?limit=50 | 1,500 | 52 ms | 7 ms | 337 ms | 58 ms | 0.00% | 0.340/sec |

관찰 결과:

- 복합 인덱스 적용 후 평균 응답 시간이 194 ms에서 52 ms로 감소했습니다.
- 최대 응답 시간이 3,710 ms에서 337 ms로 감소했습니다.
- 표준편차가 471 ms에서 58 ms로 감소해 응답 시간이 더 안정적이었습니다.
- `EXPLAIN` 결과는 `idx_ledger_entries_account_created_at`을 사용하도록 바뀌었고, account ledger 조회에서 full scan을 피했습니다.

## Phase C 분산 이체 트래픽 벤치마크

Phase C에서는 Redis cache와 distributed lock 실험을 준비합니다. 이때 hot account만 때리는 부하와 별도로, 여러 계좌 쌍에 분산된 이체 트래픽이 필요합니다.

생성된 CSV 위치:

```text
jmeter/distributed-transfer-requests.csv
```

절대 경로:

```text
C:\my\CoreBanking\jmeter\distributed-transfer-requests.csv
```

CSV 컬럼:

```text
fromAccountId,toAccountId,amount
```

CSV 생성:

```powershell
.\jmeter\generate-distributed-transfer-csv.ps1 `
  -AccountCount 1000 `
  -Rows 50000 `
  -OutputPath jmeter/distributed-transfer-requests.csv
```

JMeter 플랜:

```text
jmeter/corebanking-distributed-transfer-benchmark.jmx
```

JMeter 실행:

```powershell
jmeter -n `
  -t jmeter/corebanking-distributed-transfer-benchmark.jmx `
  -l jmeter/distributed-transfer-results.jtl `
  -Jthreads=50 `
  -JrampUp=30 `
  -Jloops=1000 `
  -JcsvFile=jmeter/distributed-transfer-requests.csv
```

주의사항:

- CSV는 계좌 분포와 금액만 제어합니다.
- `idempotencyKey`는 JMeter의 `__UUID()`로 요청마다 생성합니다.
- JMeter 플랜은 CSV row를 재사용하지 않습니다. CSV가 소진되면 스레드가 중지됩니다.
- CSV row 수는 최소 `threads * loops` 이상이어야 합니다.
- 이 벤치마크는 hot account 경합이 아닌 일반 분산 write throughput을 보기 위한 기준선입니다.

세부 문서:

```text
docs/distributed-transfer-jmeter.md
```

## Redis 계좌 원장 조회 캐시 검증

계좌 원장 조회 API에 Redis cache를 적용하고 JMeter로 적용 전/후 성능을 비교했습니다.

```http
GET /api/accounts/PHASEB-ACC-000001/ledger-entries?limit=50
```

### 문제 원인

Redis cache 적용 후 API 호출 시 Redis 직렬화 오류가 발생했습니다.

- Spring Boot 기본 Redis cache serializer는 JDK serialization을 사용합니다.
- cache value인 DTO가 `Serializable`을 구현하지 않아 Redis 저장 단계에서 500 오류가 발생했습니다.

오류 로그:

```text
java.io.NotSerializableException: com.corebanking.ledger.dto.LedgerEntryResponse
```

### 해결 방법

cache 대상 DTO가 JDK serialization 대상이 되도록 `Serializable`을 구현했습니다.

```java
public record LedgerEntryResponse(...) implements Serializable
```

```java
public record AccountResponse(...) implements Serializable
```

수정 파일:

```text
src/main/java/com/corebanking/ledger/dto/LedgerEntryResponse.java
src/main/java/com/corebanking/account/dto/AccountResponse.java
```

이후 최신 image를 다시 빌드하고 앱을 재기동했습니다.

```powershell
.\gradlew.bat test
docker compose build app
docker compose up -d app
```

Redis cache key 생성도 확인했습니다.

```powershell
docker compose exec redis redis-cli keys "*"
```

생성된 key:

```text
accountLedgerEntries::PHASEB-ACC-000001:50
```

### JMeter 실행

```text
jmeter/corebanking-read-ledger-cache-benchmark.jmx
```

JMeter 실행 예시:

```powershell
jmeter.bat -n `
  -t jmeter\corebanking-read-ledger-cache-benchmark.jmx `
  -l jmeter\corebanking-read-ledger-cache-measured.jtl `
  -Jthreads=5 `
  -Jloops=300 `
  -JrampUp=1 `
  -JaccountId=PHASEB-ACC-000001 `
  -Jlimit=50
```

Redis 미적용 비교는 임시로 cache type을 `none`으로 설정한 app container를 실행해서 측정했습니다.

```powershell
docker compose stop app
docker compose run -d --name corebanking-app-nocache --service-ports -e SPRING_CACHE_TYPE=none app
```

측정 후 원래 Redis 적용 app으로 복구했습니다.

```powershell
docker rm -f corebanking-app-nocache
docker compose up -d app
```

### JMeter 비교 결과

공통 조건:

```text
threads=5
loops=300
samples=1500
accountId=PHASEB-ACC-000001
limit=50
```

| 구분 | Average | Min | Max | P50 | P90 | P95 | P99 | Error | Throughput |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Redis 미적용 | 26.02 ms | 9 ms | 516 ms | 19 ms | 48 ms | 58 ms | 82 ms | 0.00% | 177.5/sec |
| Redis 적용 | 9.31 ms | 5 ms | 66 ms | 8 ms | 14 ms | 18 ms | 35 ms | 0.00% | 408.3/sec |

결론:

- Redis 적용 후 평균 응답시간이 `26.02 ms`에서 `9.31 ms`로 감소했습니다.
- Throughput은 `177.5/sec`에서 `408.3/sec`로 증가했습니다.
- Redis 적용 상태에서는 측정 구간에서 ledger 조회 SQL이 반복 실행되지 않았습니다.
- Redis 미적용 상태에서는 Redis key가 생성되지 않았고 ledger 조회 SQL이 반복 실행됐습니다.
