# 제출 이후 정합성 개선 기록 — 2026-10-30

## 1. 작업 기준과 확인한 사실

작업 시작 브랜치는 `main`, HEAD는 `a1ce6d7`이었다. 작업 폴더에는 이미 단일 모듈을 여러 Gradle 모듈로 이동하는 사용자 변경이 있었다. 기존 삭제 표시와 신규 모듈을 되돌리지 않고 신규 모듈 경로에서 수정했다. Dockerfile, 루트 build.gradle/settings.gradle/gradle.properties 및 캐시 JMeter 계획의 기존 변경은 유지했다. 저장소 파일 검색에서 별도 AGENTS.md는 발견되지 않았으며 대화에서 받은 지침을 적용했다. README와 로컬 커밋 이력을 확인했고 원격 push, PR, 배포는 수행하지 않았다.

실제 문제:

| 위치 | 확인한 동작 | 발생 가능한 문제 |
| --- | --- | --- |
| transfer/.../TransferWriteService.execute | 기존 키 조회 후 새 이체 저장, 하나의 트랜잭션 | 동시 중복 키 실패 요청을 기존 결과로 복구하지 못함; 다른 내용의 키 재사용도 기존 결과 반환 |
| account/.../AccountWriteService.reserve, commitTransfer | reserve에서 출금 계좌를 먼저 잠금 | 반대 방향 이체가 서로 다른 첫 잠금을 보유한 채 기다릴 수 있음 |
| account/.../AccountRepository.findByIdForUpdate | PESSIMISTIC_WRITE 지정 | 메서드 하나의 락 지정만으로 두 계좌의 순서가 정해지지는 않음 |
| ledger/.../LedgerWriteService.record | 전체 원장 캐시를 메서드 종료 시 삭제 | 바깥 이체 커밋보다 빠른 삭제와 관계없는 계좌 삭제 |
| account/.../AccountWriteService | 예약/확정 메서드 종료 시 계좌 캐시 삭제 | 롤백 가능 시점에 삭제; 커밋 전 오래된 값 재적재 가능 |
| Transfer 엔티티 | uk_transfers_idempotency_key 존재 | 제약 선언 자체는 있었으나 실제 DB 적용 여부는 별도 검증 필요 |
| CacheConfig / application.yml | JSON Redis 직렬화, 캐시별 TTL, 기본 cache type=none | 과거 README의 JDK 직렬화 설명은 과거 기록이며 현재 구현 설명과 다름 |
| JMeter index .jmx | CSV 결과 데이터였음 | 실행 계획으로 재현할 수 없고 과거 수치와 대응 관계 불명확 |

## 2. 잠금 순서와 트랜잭션

호출 경로는 Controller → TransferWriteService(트랜잭션 중지) → TransferTransactionService.execute(새 트랜잭션) → 두 계좌 순서 잠금 → reserve → commitTransfer → 원장 두 건 → COMPLETED → 계좌별 outbox 두 건 → 커밋이다.

`AccountTransferPort.lockTransferAccounts`를 추가했다. 두 계좌 ID를 Java 문자열 순서로 정렬해, 최초 계좌 조회부터 같은 순서로 잠근다. reserve 이전에 이미 두 잠금을 보유하므로 commitTransfer만 정렬하는 방식과 다르다. 이후 출금 예약 가능 금액, 활성 상태와 양수 금액을 검사한다. 예약 해제, 출금 차감, 입금 증가, 이체 상태, 원장과 outbox는 모두 같은 DB 트랜잭션이다. 원장에 REQUIRES_NEW를 추가하지 않았다. 계좌 변경 서비스에는 MANDATORY를 적용해 트랜잭션 없이 잠금/변경하는 호출을 거부한다.

낙관 모드는 기존 findById와 @Version을 유지한다. 충돌 시 기존 409 OPTIMISTIC_LOCK_CONFLICT 응답을 유지하며 자동 무제한 재시도는 없다. 최초 계좌 잠금 순서를 통일하는 것은 반대 방향의 계좌 행 잠금 순환을 줄이는 수정이다. 다른 인덱스 잠금, 다른 업무 경로, DB 내부 락까지 포함해 모든 데드락이 없어졌다는 뜻은 아니다. 계좌 ID의 대소문자/별칭이 DB에서 동일 계좌로 해석되는 경우에는 별도 정규화 정책이 필요하다.

## 3. 멱등성

이전에는 키가 존재하면 요청 내용과 무관하게 기존 객체를 반환했다. 지금은 출금 계좌, 입금 계좌, 실제 금액을 비교한다. BigDecimal.compareTo를 사용하므로 10.0과 10.00은 같다. 다른 내용 또는 반환할 수 없는 상태는 `IdempotencyConflictException`이며 기존 GlobalExceptionHandler의 CoreBankingException 경로를 통해 HTTP 409 / IDEMPOTENCY_CONFLICT가 된다. 기존 핸들러 자체는 수정할 필요가 없었다.

새 이체는 saveAndFlush로 유일 제약 검사를 먼저 유도한다. 최종 중복 방지 장치는 기존 DB 제약이다. execute 프록시가 롤백을 마치고 예외를 밖으로 전달한 뒤에만 외부 TransferWriteService가 잡는다. Hibernate ConstraintViolationException의 제약 이름이 정확히 uk_transfers_idempotency_key(테이블 접두사 허용)인 경우만 recover로 보낸다. 다른 DataIntegrityViolationException은 그대로 전파한다. recover는 REQUIRES_NEW 조회로 기존 스냅샷과 롤백 전용 상태를 피한다. 복구 조회는 한 번이며 무제한 재시도하지 않는다. 드라이버가 제약 이름을 제공하지 않는 경우에는 오분류해 성공시키지 않고 오류로 남는다.

동일 내용 재전송은 기존 Controller 계약을 유지해 HTTP 201과 동일 transferId 본문을 반환한다. 이는 구현상 계약이며 실제 HTTP 검증 테스트는 작성했지만 환경 문제로 실행하지 못했다.

현재 업무 흐름의 REQUESTED/PROCESSING은 같은 트랜잭션 내부 상태다. 정상 경로에서는 COMPLETED만 외부에 커밋된다. 실패는 계좌/이체/원장/outbox 모두 롤백되어 실패 상태 행을 남기지 않는다. 대기 중 경쟁 요청은 DB 유일 제약 검사에서 기다린 후 승자의 커밋 결과를 복구하거나, 승자가 롤백하면 자신이 진행할 수 있다. 수동 입력된 진행/실패 상태의 키는 성공으로 반환하지 않고 409로 처리한다.

## 4. 캐시와 실패 복구

커밋 전 CacheEvict를 계좌/원장 쓰기에서 제거했다. 기존 TransferCompletedEvent는 알림용 AFTER_COMMIT 리스너를 그대로 유지한다. 캐시의 내구성 있는 작업 저장은 AFTER_COMMIT 이벤트에서 수행하지 않는다. 그렇게 하면 커밋과 이벤트 처리 사이 종료 시 작업 기록 자체를 잃을 수 있기 때문이다. 대신 같은 이체 트랜잭션에서 `cache_invalidations`에 계좌별 작업을 저장한다.

- 작업은 계좌 ID, 완료 여부, 실패 횟수, 다음 시도 시각, 마지막 오류를 가진다.
- 기본 1초 간격으로 최대 20개 due ID를 조회한다. 각 작업은 별도 REQUIRES_NEW 트랜잭션에서 행을 PESSIMISTIC_WRITE로 잠그고 완료/실행 시각을 다시 검사한다.
- 여러 처리기가 같은 작업을 골라도 한 처리기만 진행하며 나머지는 잠금 후 상태를 재확인한다. SKIP LOCKED 방식은 아니므로 처리기 수 증가가 비례한 처리량 증가를 보장하지 않는다.
- 기존 계좌 캐시 키와 원장 limit 1~100 키를 삭제하고, Redis에서는 해당 계좌의 새 버전 키 접두사를 SCAN해 모두 DEL한다. 무관한 계좌를 DEL하거나 전체 캐시를 clear하지 않는다. SCAN은 전체 키 공간을 순회할 수 있으므로 비용은 남는다.
- DEL 응답을 받은 뒤에만 작업을 완료한다. 삭제 개수 0은 성공이다. 타임아웃/예외는 실패로 기록하고 미완료를 유지한다.
- Redis 삭제 후 완료 커밋 전에 종료되거나 DB 커밋이 실패하면 완료 상태가 롤백되므로 다시 처리한다. 삭제는 반복해도 안전하다. 이전 작업 재처리가 새 키를 삭제할 수는 있어도 오래된 값을 복원하지 않는다.
- 실패 후 재시도 간격은 2, 4, 8, ... 256초로 상한을 둔다. 제한된 빈도로 계속 재시도하며 작업을 포기하거나 삭제하는 횟수 한도를 두지 않았다. 실패 횟수 표시는 1,000,000에서 포화된다. 로그에 작업 ID/계좌/실패 횟수를 남긴다.
- Redis 연결/명령 timeout 기본값은 각각 1초로 명시했다. 환경변수로 조정 가능하다. 작업 행 잠금을 잡은 동안 Redis를 호출하는 비용은 남는다.

### 최신성과 오래된 조회의 재적재

`CacheStateReader`는 Redis 대신 같은 주 DB에서 계좌별 전체 outbox 수와 미완료 존재 여부를 한 쿼리로 읽는다. 새 독립 트랜잭션으로 읽어 바깥의 오래된 DB 스냅샷을 재사용하지 않는다. 미완료 작업이 하나라도 있으면 계좌/원장 모두 캐시 get/put을 우회하고 DB를 조회한다. 여러 거래 중 이전 작업 하나를 완료해도 다른 작업이 남아 있으면 계속 우회한다.

전체 outbox 개수를 증가하는 캐시 세대로 사용한다. 새 키는 `cacheName::base64url(accountId):v세대[:limit]`이다. 계좌 인코딩은 접두사/와일드카드 충돌을 피한다. 조회 전 상태가 미완료가 아니면 그 세대의 캐시만 읽는다. DB 조회 후 상태를 다시 확인해 동일할 때만 저장한다. 이체 전에 출발한 오래된 조회가 삭제 이후 값을 저장하는 경우에도 옛 세대에만 저장할 수 있어 이후 새 세대 조회에 쓰이지 않는다. 같은 계좌의 모든 limit은 같은 세대가 바뀌므로 50 이외의 결과도 논리적으로 무효화된다. Redis SCAN과 경합해 물리 삭제를 피한 옛 세대 키도 이후 조회에서 접근하지 않고 TTL로 만료된다.

Redis get/put 장애도 DB 조회로 우회한다. DB 상태 조회 자체가 실패하면 임의로 캐시를 신뢰하지 않고 요청이 실패한다. 캐시 비활성 NoOpCacheManager에서는 상태 조회도 생략한다.

이 설계는 순차적인 커밋 후 조회가 옛 세대 캐시를 쓰지 않도록 한 것이다. 거래 커밋과 겹쳐 이미 시작된 읽기에 항상 가장 최신 값이 반환되는 선형화 가능성은 보장하지 않는다. 모든 변경이 이 경로에서 outbox를 함께 저장한다는 전제가 있다. 직접 SQL 수정, 외부 쓰기, 복제 지연, 여러 계좌를 하나의 시점으로 조회하는 기능은 별도 범위다.

### TTL과 운영 제약

CacheConfig는 현재 JSON 직렬화를 사용한다. 기본 fallback TTL은 core-banking.cache.ttl=60s, accounts는 account-ttl=30s, accountLedgerEntries는 ledger-ttl=60s로 덮어쓴다. 환경변수 CORE_BANKING_CACHE_TTL / CORE_BANKING_ACCOUNT_CACHE_TTL / CORE_BANKING_LEDGER_CACHE_TTL로 변경할 수 있다. spring.cache.redis.time-to-live도 60s지만 이 두 캐시는 사용자 지정 CacheConfig의 값이 우선한다. TTL은 저장 시점부터 만료 시간이며 이체 성공 즉시 최신성의 보장은 아니다. 실제 실행 컨테이너의 환경변수 값은 확인하지 못했다. 기본 spring.cache.type은 none이고 redis 설정일 때 Redis를 쓴다.

완료된 outbox 행을 지우면 세대 번호가 감소/재사용되므로 **현재 설계에서 완료 행 삭제는 금지**다. 장기 운영 시 별도 영구 계좌 세대 테이블과 안전한 정리 절차를 도입해야 한다. 캐시 hit도 주 DB 상태 쿼리 1회, miss는 2회가 추가된다. 계좌별 이력 누적으로 COUNT 비용이 커지며 캐시 성능을 일부 포기한 선택이다. 새 구현의 성능은 미측정이다. 기본 단일 Redis와 현재 기본 키 접두사 설정을 전제로 하며 Redis Cluster/사용자 정의 prefix는 추가 구현·검증이 필요하다. ddl-auto=update를 유지했으나 운영 적용 전 신규 테이블/인덱스 DDL을 별도 검토해야 한다.

운영 확인 SQL(읽기 전용):

```sql
SELECT account_id, COUNT(*) AS pending, MAX(failures) AS max_failures,
       MIN(next_attempt) AS earliest_retry
FROM cache_invalidations WHERE completed = false GROUP BY account_id;
SELECT id, account_id, failures, next_attempt, last_error
FROM cache_invalidations WHERE completed = false ORDER BY next_attempt;
```

미완료 누적, 반복 실패, 오래 지난 next_attempt를 감시해야 한다. 이 저장소에서 외부 알림/대시보드 연결까지 구축하지는 않았다. 실패 원인 해결 후 예약 시각에 자동 재시도한다. 수동으로 completed=true를 설정해 실패를 숨기지 않는다.

## 5. 실제 변경 파일

기존 모듈 이동 변경 전체가 이번 작업 결과인 것은 아니다. 이번에 편집/추가한 파일만 아래에 적는다.

- account/src/main/java/com/corebanking/account/port/AccountTransferPort.java
- account/src/main/java/com/corebanking/account/service/AccountWriteService.java
- account/src/main/java/com/corebanking/account/service/AccountReadService.java
- transfer/src/main/java/com/corebanking/transfer/service/TransferWriteService.java
- transfer/src/main/java/com/corebanking/transfer/service/TransferTransactionService.java (추가)
- transfer/src/main/java/com/corebanking/transfer/exception/IdempotencyConflictException.java (추가)
- ledger/src/main/java/com/corebanking/ledger/service/LedgerWriteService.java
- ledger/src/main/java/com/corebanking/ledger/service/LedgerReadService.java
- common/build.gradle (JPA/cache/Redis 의존성; 버전 업그레이드 없음)
- common/src/main/java/com/corebanking/common/cache/AccountCache.java (추가)
- common/src/main/java/com/corebanking/common/cache/CacheStateReader.java (추가)
- common/src/main/java/com/corebanking/common/cache/CacheInvalidation.java (추가)
- common/src/main/java/com/corebanking/common/cache/CacheInvalidationRepository.java (추가)
- common/src/main/java/com/corebanking/common/cache/CacheInvalidationWorker.java (추가)
- common/src/main/java/com/corebanking/common/cache/CacheInvalidationScheduler.java (추가)
- app/src/main/resources/application.yml
- account/src/test/java/com/corebanking/account/service/AccountWriteServiceTest.java (추가)
- transfer/src/test/java/com/corebanking/transfer/service/TransferWriteServiceTest.java (추가)
- common/src/test/java/com/corebanking/common/cache/AccountCacheTest.java (추가)
- common/src/test/java/com/corebanking/common/cache/CacheInvalidationWorkerTest.java (추가)
- app/src/test/java/com/corebanking/transfer/service/TransferHardeningIntegrationTest.java (추가)
- jmeter/corebanking-read-transaction-benchmark.jmx (과거 XML 복원)
- jmeter/history/README.md, corebanking-read-transaction-legacy.csv (추가)
- jmeter/run-metadata.template.json, jmeter/summarize-results.py (추가)
- docs/benchmark-reproduction.md, docs/post-submission-improvements.md (추가)
- README.md, .gitignore (.gradle-local 제외)

## 6. 검증 결과와 미실행 범위

실행 명령:

```powershell
.\gradlew.bat :common:test :account:test :transfer:test :app:test --no-daemon
```

최종 실행 결과:

| 검증 | 결과 |
| --- | --- |
| common 단위 테스트 | 4건 통과 |
| account 단위 테스트 | 2건 통과 |
| transfer 단위 테스트 | 2건 통과 |
| 전체 main/test 소스 컴파일 | 성공 |
| 기존 app 통합 테스트 | 2건 실패: MySQL 접속 거부로 context 초기화 실패 |
| 신규 실제 MySQL/Redis 통합 테스트 | 7건 건너뜀: COREBANKING_INTEGRATION 미설정 및 실제 인프라 없음 |
| 전체 명령 종료 상태 | 실패(exit 1), 원인은 app 통합 테스트의 환경 문제 |
| 복원 JMX XML 파싱 | 통과; JMeter 실제 실행은 미수행 |
| CSV 원본 보존/요약 계산 검증 | 통과; 새로운 성능 측정 아님 |

최종 단위 테스트 합계는 8건이며 전체 테스트 성공으로 보고하지 않는다. Gradle은 Java 17.0.19 toolchain을 사용한다. 기본 셸 Java는 21이었으나 gradle.properties의 기존 Java 17 경로를 유지했다. 최초 Gradle 저장 폴더 권한 및 다운로드 DNS 실패 후 허용된 실행 경로로 Gradle 8.13을 받아 빌드를 진행했다.

단위 테스트 검증 범위:

- 양방향 모두 예약 이전 최초 두 계좌 락 순서 A→B, 낙관 모드 유지, 금액 합과 예약 해제.
- 표현 자릿수가 다른 같은 금액 재전송, 다른 계좌/금액 충돌, 해당 유일 제약만 복구하며 다른 무결성 오류는 재전파.
- 미완료 DB 상태의 캐시 우회, latch로 지연시킨 오래된 읽기의 재적재 방지, 모든 limit의 세대 변경과 무관 계좌 캐시 보존.
- 삭제 실패 시 미완료/백오프 유지 및 정상 경로의 limit 1/50/100 삭제.

이는 mock/메모리 캐시 기반 단위 검증이다. 실제 DB 락, 유일 제약 적용, Redis 응답/타임아웃, 프로세스 종료 복구의 통과 근거로 사용하지 않는다.

실제 MySQL/Redis용 통합 테스트 7개는 작성했지만 실행하지 못했다. 테스트 클래스는 전용 DB/Redis에서 `COREBANKING_INTEGRATION=true`일 때만 활성화된다. 테스트 전체에 자동 롤백을 적용하지 않아 실제 커밋을 검사하도록 했다. barrier를 사용해 양방향/동일 출금 동시 이체, 동시 멱등성, 유일 제약 직접 중복 저장, HTTP 201/409 본문, 잔액 부족 롤백, 여러 limit/계좌 조회, 완료 커밋 실패 모사, 동시 처리기를 검사하도록 작성했다. 테스트는 UUID 계좌를 생성하고 기존 데이터를 삭제하지 않지만 생성한 행을 남기므로 반드시 전용 테스트 DB에서 실행한다.

```powershell
# 전용 MySQL 8 및 Redis 7.2가 준비된 환경에서만 실행
$env:COREBANKING_INTEGRATION='true'
# SPRING_DATASOURCE_URL/USERNAME/PASSWORD 및 Redis 접속 정보는 전용 테스트 인스턴스로 설정
.\gradlew.bat :app:test --tests '*TransferHardeningIntegrationTest' --no-daemon
Remove-Item Env:COREBANKING_INTEGRATION
```

현재 Docker CLI는 설치되어 있으나 Docker Desktop Linux engine pipe가 없었다. 실제 전체 app:test 실행은 MySQL localhost:3306 연결 거부로 기존 2개 테스트가 실패했다. 따라서 현재 코드의 실제 DB 스키마 생성, 쿼리 실행, 락/유일 제약 동작, 실제 HTTP 본문, Redis SCAN/DEL 동작은 검증되지 않았다. 컴파일 성공으로 이를 대체하지 않는다.

추가 장애 재현 절차(이번에는 미실행):

1. 전용 MySQL/Redis에서 두 계좌/여러 limit 캐시를 채운다. Redis만 중단한다.
2. 이체 POST가 커밋/201인지 DB의 이체·원장·잔액으로 확인한다. worker가 실패 횟수/next_attempt를 갱신하는지 확인한다.
3. Redis 중단 동안 두 계좌와 여러 limit을 조회해 DB 최신 값이 반환되는지 확인한다.
4. Redis 복구 후 backoff 시각을 지나 작업이 완료되고 새 캐시가 채워지는지 확인한다.
5. Redis DEL 직후 작업 완료 DB 커밋 전 프로세스를 종료한 뒤 재시작한다. 같은 작업이 안전하게 재처리되는지 확인한다. 작성한 통합 테스트의 DB 롤백 모사는 실제 프로세스 kill 실험을 대체하지 않는다.
6. 같은 계좌에 후속 이체를 추가해 이전 작업 완료만으로 후속 pending이 해제되지 않는지 확인한다. 두 앱 처리기를 함께 실행해 중복 선택과 DB 행 잠금 경합을 관찰한다.

복원 XML은 Python XML 파서로 문법 검사를 통과했다. 보존 CSV는 Git의 LF/작업 폴더 CRLF 차이를 정규화하면 HEAD 원본과 같은 내용이다. 별도 원본 SHA-256을 기록했다. 결과 요약 스크립트는 인공 샘플 2개로 평균/P95/P99/오류율/처리량 계산을 검사했다. 이는 부하 측정 결과가 아니다. git diff --check는 공백 오류 없이 통과했다(기존 파일의 줄바꿈 변환 경고는 존재).

## 7. 성능 수치 구분

README의 과거 인덱스 194→52ms, 캐시 26.02→9.31ms 및 177.5→408.3/sec는 보존했다. 새 성능 수치는 없다. 인덱스 요청 수 1,650/1,500 차이와 CSV 원본의 수치 대응 불확실성을 문서에 명시했다. 실행 계획과 결과를 분리했고 실제 파라미터/데이터 규모/분포/환경/버전/워밍업/DB 버퍼/애플리케이션 캐시 여부/평균/P95/P99/오류율/관측 처리량을 기록하는 템플릿을 추가했다. 대규모 부하 실험은 하지 않았다.

## 8. 면접에서 설명할 제출 이후 개선

“제출본의 이체 트랜잭션을 다시 확인해, 반대 방향 요청에서 최초 계좌 잠금 순서가 달라질 수 있는 점을 수정했습니다. 동시 멱등성은 DB 유일 제약을 유지하면서 실패한 트랜잭션 밖에서 기존 결과를 복구하도록 분리했습니다. 캐시는 커밋 전에 삭제하던 방식을 제거하고 이체와 함께 DB에 무효화 작업을 저장했습니다. Redis 장애 중에는 DB 상태를 보고 조회를 우회하며 캐시 세대를 바꿔 오래된 읽기의 재적재를 격리했습니다. 단위 테스트와 컴파일은 검증했지만 이 환경의 MySQL/Redis가 없어 실제 동시성·장애 통합 검증은 아직 완료하지 못했습니다. 과거 성능 수치를 이 개선의 성능 결과로 주장하지 않습니다.”

## 9. 이해해야 할 핵심 개념

- **잠금 순서**: 두 사람이 방 두 개를 쓰려는데 각자 다른 방 열쇠를 먼저 쥐면 서로 기다릴 수 있다. 모두 작은 번호 방부터 열쇠를 받게 바꾼 것이다.
- **트랜잭션**: 출금, 입금, 영수증, 재처리 메모를 한 봉투로 묶어 전부 저장하거나 전부 취소한다. 여기서 메모만 나중에 따로 쓰면 봉투 저장 직후 종료될 때 메모를 잃을 수 있다.
- **멱등성 키**: 같은 주문 번호를 다시 보내도 돈은 한 번만 움직인다. 같은 번호로 내용까지 바꾸면 같은 주문으로 취급하지 않는다.
- **롤백 후 복구 조회**: 실패한 작업장의 상태를 그대로 재사용하지 않고 정리를 끝낸 다음 새 창구에서 완료된 주문을 조회한다.
- **아웃박스**: 돈을 옮겼다는 기록과 “캐시를 치울 일” 메모를 함께 저장한다. 캐시가 고장 나도 메모가 남아 나중에 다시 치운다. 즉시 처리를 보장하는 장치는 아니다.
- **캐시 세대**: 오래된 안내문을 지웠는데 늦게 도착한 직원이 다시 붙일 수 있다. 안내판 번호를 새 번호로 바꾸면 예전 번호판에 붙인 안내문은 다음 손님이 보지 않는다.
- **DB 우회 비용**: 안내문을 믿어도 되는지 매번 원본 장부에 물으므로 안전성을 높이는 대신 조회 비용이 늘어난다. 이번 수정의 성능은 다시 측정해야 한다.
