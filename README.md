# CoreBanking — MSA 코어뱅킹 시스템 실습

Spring Boot + Kafka 기반의 MSA 학습 프로젝트입니다.  
멱등성, 재시도, 정합성, Saga 패턴, 보상 트랜잭션, 관찰성을 직접 구현하며 실습합니다.

---

## 목차

1. [아키텍처 개요](#아키텍처-개요)
2. [Saga 흐름 설계](#saga-흐름-설계)
3. [Kafka 토픽 구조](#kafka-토픽-구조)
4. [모듈별 코드 설명](#모듈별-코드-설명)
   - [common](#common)
   - [transfer-service](#transfer-service)
   - [account-service](#account-service-진행중)
   - [ledger-service](#ledger-service-예정)
   - [notification-service](#notification-service-예정)
5. [인프라 구성](#인프라-구성)
6. [실행 방법](#실행-방법)
7. [학습 포인트 정리](#학습-포인트-정리)

---

## 아키텍처 개요

```
Client
  │
  ▼
transfer-service (8081)   ── publishes ──▶  Kafka
  │                                           │
  │  consumes ◀───────────────────────────── │
  │                                           │
  │                           ┌──────────────┼──────────────┐
  │                           ▼              ▼              ▼
  │                   account-service   ledger-service  notification-service
  │                      (8083)            (8082)           (8084)
  │
  ▼
MySQL / H2 (각 서비스 독립 DB)
```

**설계 원칙**
- 각 서비스는 **독립된 데이터베이스**를 가짐 (DB per Service)
- 서비스 간 직접 HTTP 호출 없음 — **Kafka 이벤트로만 통신**
- Saga 패턴: **Choreography 방식** (중앙 오케스트레이터 없이 이벤트로 흐름 제어)

---

## Saga 흐름 설계

### Happy Path (정상 이체)

```
1. POST /api/transfers  →  transfer-service
2. transfer-service      →  DB: Transfer(ACCOUNT_CHECKING) 저장
                         →  Kafka: transfer.created 발행

3. account-service       ←  Kafka: transfer.created 수신
                         →  출금 계좌 잔액 확인 및 예약 (낙관적 락으로 중복 차감 방지)
                         →  Kafka: account.reserved 발행

4. transfer-service      ←  Kafka: account.reserved 수신
                         →  DB: Transfer(LEDGER_PROCESSING) 업데이트

5. ledger-service        ←  Kafka: account.reserved 수신
                         →  멱등성 키 체크 (중복 원장 기록 방지)
                         →  차변/대변 원장 기록
                         →  Kafka: ledger.recorded 발행

6. transfer-service      ←  Kafka: ledger.recorded 수신
                         →  DB: Transfer(COMPLETED) 업데이트
                         →  Kafka: transfer.completed 발행

7. notification-service  ←  Kafka: transfer.completed 수신
                         →  이체 완료 알림 발송
```

### 잔액 부족 시 (보상 트랜잭션 필요 없음)

```
3. account-service       ←  Kafka: transfer.created 수신
                         →  잔액 부족 확인
                         →  Kafka: account.failed 발행

4. transfer-service      ←  Kafka: account.failed 수신
                         →  DB: Transfer(CANCELLED) 업데이트
                         →  Kafka: transfer.cancelled 발행

5. notification-service  ←  Kafka: transfer.cancelled 수신 → 이체 실패 알림
```

### 원장 기록 실패 시 (보상 트랜잭션 발동)

```
5. ledger-service        ←  Kafka: account.reserved 수신
                         →  원장 기록 실패
                         →  Kafka: ledger.failed 발행

6. transfer-service      ←  Kafka: ledger.failed 수신
                         →  DB: Transfer(CANCELLED) 업데이트
                         →  Kafka: transfer.cancelled 발행  ◀── 보상 트리거

7. account-service       ←  Kafka: transfer.cancelled 수신
                         →  예약된 잔액 롤백 (보상 트랜잭션)  ◀── 핵심!

8. notification-service  ←  Kafka: transfer.cancelled 수신 → 이체 취소 알림
```

---

## Kafka 토픽 구조

| 토픽 | 발행자 | 소비자 | 설명 |
|------|--------|--------|------|
| `transfer.created` | transfer-service | account-service | Saga 시작 |
| `account.reserved` | account-service | transfer-service, ledger-service | 잔액 예약 성공 |
| `account.failed` | account-service | transfer-service | 잔액 부족 |
| `ledger.recorded` | ledger-service | transfer-service | 원장 기록 성공 |
| `ledger.failed` | ledger-service | transfer-service | 원장 기록 실패 |
| `transfer.completed` | transfer-service | notification-service | 이체 완료 |
| `transfer.cancelled` | transfer-service | account-service, notification-service | 이체 취소 (보상 트리거) |

---

## 모듈별 코드 설명

### common

**위치**: `common/src/main/java/com/example/eshop/common/event/`

서비스 간 공유되는 Kafka 이벤트 DTO 모음입니다.

#### `Topics.java`

```java
public final class Topics {
    public static final String TRANSFER_CREATED  = "transfer.created";
    public static final String TRANSFER_COMPLETED = "transfer.completed";
    public static final String TRANSFER_CANCELLED = "transfer.cancelled";
    public static final String ACCOUNT_RESERVED  = "account.reserved";
    public static final String ACCOUNT_FAILED    = "account.failed";
    public static final String LEDGER_RECORDED   = "ledger.recorded";
    public static final String LEDGER_FAILED     = "ledger.failed";
}
```

토픽 이름을 상수로 관리합니다. 서비스마다 문자열을 직접 쓰면 오타로 인한 버그가 생기기 쉽기 때문에 common 모듈에서 단일 진실 공급원(Single Source of Truth)으로 관리합니다.

#### 이벤트 클래스 설계

모든 이벤트는 Java `record`로 정의됩니다. 이벤트마다 공통으로 포함하는 필드:

| 필드 | 타입 | 목적 |
|------|------|------|
| `eventId` | `String (UUID)` | 이벤트 고유 ID — 중복 소비 감지에 사용 |
| `transferId` | `String` | Saga 전체를 관통하는 상관관계 ID |
| `occurredAt` | `Instant` | 이벤트 발생 시각 |

```java
// 예: TransferCreatedEvent
public record TransferCreatedEvent(
    String eventId,       // 이벤트 중복 감지용
    String transferId,    // Saga 상관관계 ID
    String fromAccountId,
    String toAccountId,
    BigDecimal amount,
    Instant occurredAt
) {}
```

**왜 eventId가 필요한가?**  
Kafka는 at-least-once 전달을 보장합니다. 즉, 네트워크 장애나 컨슈머 재시작 시 같은 메시지가 두 번 이상 도착할 수 있습니다. 소비자 측에서 `eventId`를 처리 여부 테이블에 기록해두면 중복 소비를 방지할 수 있습니다(멱등 소비자 패턴).

---

### transfer-service

**위치**: `transfer-service/src/main/java/com/example/eshop/order/`  
**포트**: 8081

Saga의 진입점이자 흐름 조율 역할을 합니다. 이체를 생성하고, 다른 서비스들의 응답 이벤트에 따라 이체 상태를 전환합니다.

#### `Transfer.java` (도메인 엔티티)

```java
@Entity
@Table(name = "transfers")
public class Transfer {
    @Id
    private String transferId;       // UUID — DB auto-increment 아님
    private String fromAccountId;
    private String toAccountId;
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private TransferStatus status;   // 상태 머신
    private String failureReason;
}
```

**왜 PK를 UUID로?**  
여러 서비스가 독립적으로 ID를 생성해야 할 때, DB auto-increment는 DB 조회 없이는 ID를 미리 알 수 없습니다. UUID를 애플리케이션 레이어에서 생성하면 DB 없이도 ID를 먼저 결정하고 이벤트에 포함시킬 수 있습니다.

#### `TransferStatus.java` (상태 머신)

```
PENDING
  │
  ▼
ACCOUNT_CHECKING   ← transfer.created 발행 직후
  │
  ├─[account.reserved]──▶ LEDGER_PROCESSING
  │                              │
  │                    [ledger.recorded]──▶ COMPLETED
  │                              │
  │                    [ledger.failed]────▶ CANCELLED
  │
  └─[account.failed]─────────────────────▶ CANCELLED
```

#### `TransferService.java` (Saga 조율 로직)

```java
// Saga 시작
@Transactional
public Transfer createTransfer(CreateTransferRequest req) {
    Transfer transfer = Transfer.create(transferId, ...);
    transfer.markAccountChecking();
    transferRepository.save(transfer);               // 1) DB 저장
    publisher.publishTransferCreated(event);         // 2) 이벤트 발행
    return transfer;
}
```

> **주의**: DB 저장과 Kafka 발행이 하나의 메서드에 있지만 다른 트랜잭션입니다.  
> DB 커밋 후 Kafka 발행 전에 장애가 나면 이벤트가 유실될 수 있습니다.  
> 이를 완전히 해결하려면 **Transactional Outbox 패턴**이 필요합니다 (추후 실습 항목).

**원장 기록 실패 시 보상 트랜잭션 트리거**:

```java
@Transactional
public void onLedgerFailed(LedgerFailedEvent event) {
    transfer.cancel(event.reason());                          // 이체 취소
    TransferCancelledEvent cancelled = TransferCancelledEvent.of(...);
    publisher.publishTransferCancelled(cancelled);            // transfer.cancelled 발행
    // → account-service가 이걸 소비해서 예약 잔액을 복원 (보상 트랜잭션)
}
```

#### `TransferEventConsumer.java` (Kafka 소비자)

```java
@KafkaListener(topics = Topics.ACCOUNT_RESERVED, groupId = "transfer-service")
public void onAccountReserved(AccountReservedEvent event) { ... }

@KafkaListener(topics = Topics.LEDGER_FAILED, groupId = "transfer-service")
public void onLedgerFailed(LedgerFailedEvent event) { ... }
```

**`groupId = "transfer-service"`의 의미**:  
같은 groupId를 공유하는 컨슈머들은 파티션을 나눠 소비합니다. transfer-service 인스턴스가 여러 개 뜨면 Kafka가 파티션을 분배해 중복 소비를 막습니다. notification-service도 같은 토픽을 소비하지만 groupId가 다르기 때문에 독립적으로 소비합니다.

---

### account-service (진행중)

**위치**: `account-service/`  
**포트**: 8083

구현 예정 핵심 포인트:

- **낙관적 락 (`@Version`)**: 동시에 여러 이체 요청이 같은 계좌 잔액을 차감하려 할 때 하나만 성공하도록
- **멱등 소비**: `transferId`로 이미 처리한 예약인지 확인 후 중복 차감 방지
- **보상 트랜잭션**: `transfer.cancelled` 수신 시 예약된 잔액 복원

```java
// 예정 코드 - 낙관적 락
@Entity
public class Account {
    private String accountId;
    private BigDecimal balance;

    @Version               // ← 낙관적 락 핵심
    private Long version;  // 동시 수정 시 OptimisticLockingFailureException 발생
}
```

---

### ledger-service (예정)

**포트**: 8082

구현 예정 핵심 포인트:

- **멱등성 키 테이블**: 같은 `transferId`로 두 번 원장 기록 요청이 와도 한 번만 처리
- **복식 부기**: 차변(출금 계좌) / 대변(입금 계좌) 동시 기록

```java
// 예정 코드 - 멱등성 체크
@Transactional
public LedgerResult recordEntry(String transferId, BigDecimal amount) {
    return idempotencyKeyRepository.findByTransferId(transferId)
        .map(key -> key.getCachedResult())                    // 캐시된 결과 반환
        .orElseGet(() -> doRecordAndSave(transferId, amount)); // 실제 처리
}
```

---

### notification-service (예정)

**포트**: 8084

`transfer.completed`, `transfer.cancelled` 수신 후 알림 발송 (로그 기반 시뮬레이션).

---

## 인프라 구성

### docker-compose.yml

```yaml
services:
  kafka:        # 이벤트 브로커 (port 9092)
  kafka-ui:     # Kafka 토픽/메시지 시각화 (port 8090)
  mysql:        # 운영 DB - 서비스별 독립 스키마 (port 3306)
  zipkin:       # 분산 추적 UI (port 9411)
```

**서비스별 DB 스키마 분리** (`docker/mysql/init.sql`):

```sql
CREATE DATABASE transfer_db;
CREATE DATABASE account_db;
CREATE DATABASE ledger_db;
CREATE DATABASE notification_db;
```

각 서비스가 다른 서비스의 DB에 직접 접근하지 않는 것이 MSA의 핵심 원칙입니다.

### settings.gradle (멀티모듈)

```groovy
include 'common'
include 'transfer-service'
include 'account-service'
include 'ledger-service'
include 'notification-service'
```

`common` 모듈은 다른 서비스들이 `implementation project(':common')`으로 의존합니다.

---

## 실행 방법

### 1. 인프라 실행

```bash
docker-compose up -d
```

| 서비스 | URL |
|--------|-----|
| Kafka UI | http://localhost:8090 |
| Zipkin | http://localhost:9411 |
| MySQL | localhost:3306 |

### 2. 서비스 실행 (각 터미널에서)

```bash
./gradlew :transfer-service:bootRun
./gradlew :account-service:bootRun
./gradlew :ledger-service:bootRun
./gradlew :notification-service:bootRun
```

### 3. 이체 요청 테스트

```bash
curl -X POST http://localhost:8081/api/transfers \
  -H "Content-Type: application/json" \
  -d '{
    "fromAccountId": "ACC-001",
    "toAccountId": "ACC-002",
    "amount": 50000
  }'
```

### 4. 이체 상태 조회

```bash
curl http://localhost:8081/api/transfers/{transferId}
```

---

## 학습 포인트 정리

| 개념 | 구현 위치 | 설명 |
|------|-----------|------|
| **MSA** | 전체 구조 | 서비스별 독립 빌드/배포/DB |
| **Choreography Saga** | `TransferService` + 각 서비스 consumer | 중앙 조율자 없이 이벤트로 흐름 제어 |
| **보상 트랜잭션** | `TransferService.onLedgerFailed()` → account-service | 원장 기록 실패 시 예약 잔액 복원 |
| **멱등성** | ledger-service `idempotency_keys` | 같은 요청 두 번 와도 한 번만 처리 |
| **중복 소비 방지** | 각 consumer의 `eventId` 체크 | Kafka at-least-once 보완 |
| **잔액 동시성** | account-service `@Version` | 낙관적 락으로 이중 차감 방지 |
| **분산 추적** | Micrometer Tracing + Zipkin | traceId로 서비스 간 요청 추적 |
| **구조화 로그** | `application.yml` logging pattern | traceId를 로그에 자동 포함 |
| **Outbox 패턴** | (TODO) | DB 저장과 Kafka 발행의 원자성 보장 |

### 장애 시나리오 재현 방법 (예정)

```bash
# 잔액 부족 시나리오
# → account-service에서 계좌 잔액을 0으로 세팅 후 이체 요청

# 원장 기록 실패 시나리오
# → ledger-service에 실패 모드 활성화 엔드포인트 추가

# Kafka 장애 시나리오
# → docker-compose stop kafka 후 이체 요청 → 재시작 후 이벤트 처리 확인
```

---

## 구현 진행 현황

- [x] 인프라 구성 (docker-compose, multi-module Gradle)
- [x] common 모듈 (이벤트 클래스, 토픽 상수)
- [x] transfer-service (이체 생성, Saga 흐름 조율, 상태 머신)
- [ ] account-service (잔액 예약, 낙관적 락, 보상 트랜잭션)
- [ ] ledger-service (멱등성, 복식 부기 원장 기록)
- [ ] notification-service (이벤트 수신, 알림)
- [ ] 테스트 코드 (EmbeddedKafka 통합 테스트)
- [ ] 관찰성 (Zipkin 분산 추적, MDC 로그)
