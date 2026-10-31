# 거래 정합성·멱등성·캐시 무효화 설계

## 설계 목표

계좌 이체는 출금, 입금, 원장 기록, 이체 상태, 캐시 무효화 작업을 하나의 원자적 흐름으로 처리합니다. 핵심 목표는 다음과 같습니다.

- 반대 방향 동시 이체에서도 계좌 잠금 순서를 일관되게 유지
- 동일 멱등성 키의 동시 요청을 한 번의 이체로 수렴
- 같은 키에 다른 요청 내용을 사용하면 충돌로 거부
- 거래 커밋과 캐시 무효화 작업을 함께 저장
- Redis 장애 중에도 오래된 캐시를 사용하지 않고 DB로 우회
- 늦게 끝난 조회가 이전 값을 캐시에 다시 적재해도 다음 조회에서 사용하지 않도록 격리

## 잠금 순서와 트랜잭션 경계

호출 경로는 Controller → TransferWriteService → TransferTransactionService → 두 계좌 순서 잠금 → reserve → commitTransfer → 원장 두 건 → COMPLETED → 계좌별 캐시 무효화 작업 저장 → 커밋 순서입니다.

`AccountTransferPort.lockTransferAccounts`는 두 계좌 ID를 Java 문자열 순서로 정렬하고 같은 순서로 잠급니다. reserve 전에 두 잠금을 확보하므로 반대 방향 이체가 서로 다른 첫 잠금을 보유한 채 기다리는 순환 가능성을 줄입니다.

예약 해제, 출금 차감, 입금 증가, 이체 상태, 원장과 캐시 무효화 작업은 같은 DB 트랜잭션에 포함됩니다. 계좌 변경 서비스에는 `MANDATORY` 전파 속성을 적용해 트랜잭션 없는 잠금과 상태 변경을 거부합니다.

낙관 모드는 `findById`와 `@Version`을 사용합니다. 충돌은 HTTP 409 `OPTIMISTIC_LOCK_CONFLICT`로 반환하며 무제한 자동 재시도는 하지 않습니다. 계좌 잠금 순서 통일은 계좌 행의 순환 대기를 줄이기 위한 정책이며, 다른 인덱스 잠금이나 DB 내부 잠금까지 포함한 모든 데드락 제거를 의미하지는 않습니다.

## 멱등성

멱등성 키가 이미 존재하면 출금 계좌, 입금 계좌와 금액을 비교합니다. 금액은 `BigDecimal.compareTo`를 사용하므로 `10.0`과 `10.00`을 같은 값으로 처리합니다. 요청 내용이 다르거나 기존 이체 상태를 정상 결과로 반환할 수 없으면 HTTP 409 `IDEMPOTENCY_CONFLICT`를 반환합니다.

새 이체는 `saveAndFlush`로 DB 유일 제약을 확인합니다. `uk_transfers_idempotency_key` 충돌만 복구 대상으로 분류하고 다른 `DataIntegrityViolationException`은 그대로 전파합니다. 경쟁 요청의 트랜잭션이 롤백된 뒤 외부 서비스가 `REQUIRES_NEW` 조회로 승자의 커밋 결과를 확인합니다. 조회는 한 번만 수행하며 무제한 재시도하지 않습니다.

동일한 내용의 재전송은 기존 `transferId`를 반환합니다. 진행 중이거나 실패한 상태를 수동으로 가진 키는 성공으로 간주하지 않습니다. 정상 경로에서는 `COMPLETED` 상태만 외부에 커밋되고, 실패하면 계좌·이체·원장·캐시 무효화 작업이 모두 롤백됩니다.

## 캐시 무효화와 장애 복구

계좌와 원장 캐시는 쓰기 메서드가 끝날 때 바로 삭제하지 않습니다. 이 시점은 바깥 이체 트랜잭션의 커밋보다 빠를 수 있고, 삭제 직후 다른 조회가 이전 DB 값을 다시 캐시에 넣을 수 있기 때문입니다.

대신 이체 트랜잭션 안에서 `cache_invalidations`에 계좌별 작업을 저장합니다.

- 작업은 계좌 ID, 완료 여부, 실패 횟수, 다음 시도 시각과 마지막 오류를 보관합니다.
- 처리기는 due 작업을 조회하고 각 작업을 별도 `REQUIRES_NEW` 트랜잭션에서 `PESSIMISTIC_WRITE`로 잠급니다.
- 계좌 캐시 키와 원장 조회의 limit 1~100 키를 삭제하고, 해당 계좌의 버전 키 접두사를 SCAN한 뒤 DEL합니다.
- DEL 응답을 받은 경우에만 작업을 완료합니다. 삭제 개수 0도 성공입니다.
- Redis 타임아웃이나 예외는 실패 횟수와 다음 시도 시각을 기록합니다.
- 재시도 간격은 2, 4, 8, ... 256초로 제한해 증가시키며 작업은 삭제하지 않습니다.
- Redis 삭제 뒤 DB 완료 표시 커밋 전에 프로세스가 종료되면 같은 삭제를 다시 실행합니다. 삭제는 반복해도 안전합니다.

여러 처리기가 같은 작업을 선택해도 행 잠금과 상태 재확인으로 한 처리기만 실행합니다. SKIP LOCKED 방식은 아니므로 처리기 수에 비례한 처리량 증가는 보장하지 않습니다. Redis SCAN은 키 공간을 순회할 수 있고 작업 행 잠금을 보유한 채 Redis를 호출하는 비용이 있습니다.

## 캐시 세대와 DB 우회

`CacheStateReader`는 주 DB에서 계좌별 전체 캐시 무효화 작업 수와 미완료 작업 존재 여부를 읽습니다. 미완료 작업이 하나라도 있으면 계좌와 원장 캐시의 get/put을 모두 우회하고 DB를 조회합니다.

전체 작업 수를 캐시 세대로 사용합니다. 캐시 키는 `cacheName::base64url(accountId):v세대[:limit]` 형식입니다. 조회 전후의 세대와 미완료 상태가 같을 때만 캐시에 저장합니다. 이체보다 먼저 시작한 조회가 늦게 끝나 이전 값을 저장하더라도 이전 세대 키에만 저장되므로 다음 조회에서는 사용되지 않습니다. 모든 limit은 같은 세대가 변경되어 논리적으로 함께 무효화됩니다.

Redis get/put 장애도 DB 조회로 우회합니다. DB 상태 조회가 실패하면 캐시를 신뢰하지 않고 요청을 실패시킵니다. `NoOpCacheManager`에서는 상태 조회를 생략합니다.

이 정책은 커밋이 끝난 뒤 시작한 조회가 이전 세대 캐시를 사용하지 않도록 합니다. 거래 커밋과 동시에 진행 중인 모든 읽기에 최신 값을 보장하는 선형화 가능성은 제공하지 않습니다. 모든 쓰기 경로가 캐시 무효화 작업을 함께 저장한다는 전제가 있으며, 직접 SQL 수정·외부 쓰기·복제 지연은 별도 대응이 필요합니다.

## TTL과 운영 제약

Redis 캐시는 JSON 직렬화를 사용합니다. 기본 fallback TTL은 `core-banking.cache.ttl=60s`, 계좌는 `account-ttl=30s`, 원장은 `ledger-ttl=60s`입니다. 다음 환경변수로 조정할 수 있습니다.

- `CORE_BANKING_CACHE_TTL`
- `CORE_BANKING_ACCOUNT_CACHE_TTL`
- `CORE_BANKING_LEDGER_CACHE_TTL`

완료된 캐시 무효화 행을 삭제하면 세대 번호가 감소하거나 재사용될 수 있으므로 현재 구조에서는 완료 행을 삭제하지 않습니다. 장기 운영에는 별도의 영구 계좌 세대 테이블과 안전한 정리 절차가 필요합니다. 캐시 hit에도 주 DB 상태 조회가 1회 필요하고 miss에는 2회가 추가됩니다. 계좌별 이력이 쌓이면 COUNT 비용이 커질 수 있습니다.

기본 설정은 단일 Redis와 기본 키 접두사를 전제로 합니다. Redis Cluster나 사용자 정의 prefix는 추가 검증이 필요합니다. `ddl-auto=update`를 사용하므로 운영 적용 전 신규 테이블과 인덱스 DDL을 별도로 검토해야 합니다.

운영 확인 SQL:

```sql
SELECT account_id, COUNT(*) AS pending, MAX(failures) AS max_failures,
       MIN(next_attempt) AS earliest_retry
FROM cache_invalidations
WHERE completed = false
GROUP BY account_id;

SELECT id, account_id, failures, next_attempt, last_error
FROM cache_invalidations
WHERE completed = false
ORDER BY next_attempt;
```

미완료 누적, 반복 실패와 오래 지난 `next_attempt`를 감시합니다. 실패 원인이 해결되면 예약된 시각에 자동 재시도하며, `completed=true`를 수동으로 설정해 실패를 숨기지 않습니다.

## 검증 범위

단위 테스트와 컴파일 검증 항목은 다음과 같습니다.

- 양방향 이체의 최초 두 계좌 잠금 순서
- 낙관 모드와 금액 합·예약 해제
- 표현 자릿수가 다른 동일 금액 재전송
- 다른 계좌나 금액으로 같은 키를 사용한 충돌
- 지정한 유일 제약만 복구하고 다른 무결성 오류는 재전파
- 미완료 작업이 있을 때 캐시 우회
- 지연된 읽기의 이전 값 재적재 격리
- 모든 limit의 세대 변경과 무관한 계좌 캐시 보존
- Redis 삭제 실패 시 미완료 상태와 백오프 유지

`common`, `account`, `transfer` 단위 테스트 8건과 전체 main/test 소스 컴파일이 통과했습니다. MySQL과 Redis가 필요한 통합 테스트 7건은 `COREBANKING_INTEGRATION=true`와 전용 인프라가 있을 때만 실행됩니다.

```powershell
$env:COREBANKING_INTEGRATION='true'
.\gradlew.bat :app:test --tests '*TransferHardeningIntegrationTest' --no-daemon
Remove-Item Env:COREBANKING_INTEGRATION
```

통합 테스트는 양방향·동일 출금 계좌 동시 이체, 동시 멱등성, 유일 제약, HTTP 201/409 응답, 잔액 부족 롤백, 여러 limit 조회, Redis 장애와 동시 처리기를 검증하도록 구성했습니다. 전용 테스트 DB에 UUID 계좌와 관련 행을 생성하므로 운영 DB에서는 실행하지 않습니다.

단위 테스트와 컴파일 성공만으로 실제 DB 잠금, 유일 제약 적용, Redis 타임아웃, 프로세스 종료 복구가 검증됐다고 간주하지 않습니다. 실제 장애 검증은 Redis 중단 중 이체 커밋과 DB 우회, 복구 후 재처리, DEL 직후 프로세스 종료와 재시작을 포함해야 합니다.

## 성능 수치 해석

README의 인덱스 194→52ms, 캐시 26.02→9.31ms와 처리량 177.5→408.3/sec는 각 벤치마크의 측정값입니다. 인덱스 비교는 요청 수가 1,650건과 1,500건으로 달라 동일 조건 비교로 단정하지 않습니다. 캐시 정합성 정책 자체의 성능은 별도 조건에서 측정해야 합니다.

재현 실험에서는 데이터 규모·분포, 환경·버전, 워밍업, DB 버퍼 상태, 캐시 설정, 평균·P95·P99·오류율과 관측 처리량을 함께 기록합니다. 자세한 절차는 [성능 측정 재현 기록](benchmark-reproduction.md)에 정리했습니다.
