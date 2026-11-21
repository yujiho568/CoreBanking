# DATOP 반입과 오픈뱅킹 조회 성능 실험

## 범위와 확인한 사실

`codex/openbanking-external-accounts`의 조회 연동을 확장했다. 개선 대상은 CoreBanking의 자체
조회와 외부 호출 재사용이다. 금융결제원 서버의 성능을 개선하거나 외부 이체를 구현한 것이 아니다.

실제 DATOP 파일과 컬럼 명세는 아직 제공되지 않았다. 따라서 특정 DATOP 상품의 스키마나 원본
건수를 가정하지 않는다. 아래 도구는 실제 파일의 항목을 확인한 뒤 명시적으로 매핑하는 반입 경로다.
저장소의 성능 실험은 **생성 데이터 30,000건**과 **로컬 HTTP 지연 서버**를 사용했다.

## 외부 조회 캐시

- `SPRING_CACHE_TYPE=redis`로 활성화한다. `none`이면 결과 재사용을 하지 않는다.
- 잔액 TTL 기본 10초, 거래내역 및 반입 조회 TTL 기본 60초다. `.env.example`의 환경변수로 조정한다.
- 계좌 소유권·조회 동의·연결 상태는 캐시 접근 전에 DB에서 확인한다.
- 캐시 키는 사용자, 계좌, 연결 토큰 세대, 전체 조회 조건과 최대 페이지 수를 해시한다.
- 잔액·거래내역 API에 `refresh=true`를 추가하면 저장된 결과를 건너뛰고 갱신한다.
- 같은 서버 프로세스에서 동시에 진행 중인 동일 조회는 하나로 합친다. 여러 인스턴스에 걸친
  분산 잠금은 구현하지 않았다. Redis 결과는 인스턴스 사이에서 공유한다.
- 캐시 장애는 외부 조회로 대체한다. 실패한 외부 응답은 캐시하지 않는다.
- 서로 다른 조회의 동시 로드 기본 한도는 프로세스당 16개다. 초과 시 503을 반환한다.
  `OPEN_BANKING_MAX_CONCURRENT_QUERIES`로 조정한다. 반입 조회의 캐시 로드도 이 한도를 공유한다.
- 같은 요청을 기다리는 호출은 최대 120초까지 대기한다. 외부 API 연결·읽기 타임아웃은 기존 설정을 따른다.
- 응답 `fetchedAt`은 실제 조회 시각을 유지한다. 화면에서는 마지막 갱신 시각으로 표시해야 한다.
  캐시된 잔액을 이체 가능 여부 판단에 사용하지 않는다. 이 브랜치는 프론트엔드를 추가하지 않는다.

실제 HTTP 시도 횟수는 `/actuator/metrics/openbanking.provider.requests`에서 확인한다.
`path` 태그는 고정 API 경로만 포함한다. 사용자, 토큰, 계좌번호를 태그에 넣지 않는다.
호출 전후 `COUNT` 차이를 기록한다. 거래내역 다중 페이지와 토큰 갱신은 각각 별도 HTTP 호출로 센다.
동시 다른 트래픽이 있으면 계측 결과에 포함되므로 전용 실험 환경에서 측정한다.

```http
GET /api/external-accounts/{id}/balance?refresh=true
X-User-Id: demo-user

GET /api/external-accounts/{id}/transactions?fromDate=2026-11-01&toDate=2026-11-20&maxPages=3&refresh=false
X-User-Id: demo-user
```

## DATOP 파일 준비와 반입

1. 실제 데이터셋의 항목, 인코딩, 시간대, 금액 단위와 계좌 식별 정보를 확인한다.
2. 원본은 별도로 보관한다. 실제 식별자가 있다면 정규화 전에 실험용 참조값으로 비식별화한다.
3. CSV 원본은 `scripts/normalize_datop.py`에 명시적 매핑을 제공한다. CSV 이외 형식은 별도로
   변환해야 한다. 계좌 식별 정보가 없다면 실제 계좌처럼 추정하지 말고 생성 참조값 사용 사실을 기록한다.
4. 원본과 정규화 파일을 함께 반입한다. 원본 데이터와 생성 데이터는 서로 다른 데이터셋으로 반입한다.

매핑 JSON 예시다. 아래 컬럼은 설명용이며 실제 DATOP 스키마를 의미하지 않는다.

```json
{
  "encoding": "utf-8-sig",
  "delimiter": ",",
  "account_column": "account",
  "time_column": "time",
  "amount_column": "value",
  "direction_column": "flow",
  "description_column": "memo",
  "timestamp_format": "%Y%m%d%H%M%S",
  "incoming_values": ["deposit"],
  "outgoing_values": ["withdrawal"]
}
```

```powershell
python scripts/normalize_datop.py datop-data/raw.csv datop-data/normalized.csv --mapping datop-data/mapping.json
```

정규화 결과는 UTF-8 CSV이며 헤더 순서를 고정한다. 금액은 음수가 아닌 최대 소수 둘째 자리의
원 단위 값, `direction`은 입금 `I` 또는 출금 `O`, 시간은 사전에 확인한 동일한 로컬 시간대다.
부호·시간대·금액 단위를 도구가 추측하지 않는다.

```csv
account_ref,occurred_at,amount,direction,description
experiment-account-1,2026-11-01T10:00:00,1000.00,I,synthetic example
```

실행 JAR을 빌드한 뒤 아래처럼 **명시적으로 활성화한 경우에만** 서버 시작 시 반입한다.
실제 데이터 반입은 `synthetic=false`, 생성 데이터 반입은 `synthetic=true`를 지정한다.

```powershell
java -jar app/build/libs/app-0.0.1-SNAPSHOT.jar `
  --core-banking.datop.enabled=true `
  --core-banking.datop.source=datop-data/raw.csv `
  --core-banking.datop.normalized=datop-data/normalized.csv `
  --core-banking.datop.archive=datop-data/archive `
  --core-banking.datop.owner=demo-user `
  --core-banking.datop.label=dataset-name-and-version `
  --core-banking.datop.synthetic=false
```

원본과 정규화 파일은 SHA-256 이름으로 보관한다. DB에는 두 해시, 출처 라벨, 생성 여부,
실제 적재 건수와 적재 시각을 기록한다. 같은 사용자·파일·출처·생성 여부의 재반입은 중복을 만들지 않는다.
동시 동일 반입에서 PK 충돌이 발생하면 한 요청은 실패할 수 있으며, 완료 후 재시도하면 기존 데이터셋을 반환한다.
CSV는 스트리밍으로 읽고 500건 단위 JDBC 배치로 저장한다. 한 데이터셋은 단일 트랜잭션이며
중간 오류 시 DB 적재 전체를 롤백한다. 보관된 파일은 오류 후에도 남겨 재현에 사용한다.
대규모 반입의 트랜잭션 로그·디스크 용량은 별도로 확인해야 한다.

반입 완료 로그의 `dataset` 값으로 조회한다. 시간 범위는 `from` 포함, `until` 제외다.

```http
GET /api/imported-datasets/{dataset}/transactions?account=experiment-account-1&from=2026-11-01T00:00:00&until=2026-12-01T00:00:00&limit=100
X-User-Id: demo-user
```

다음 조회에는 응답의 `nextTime`과 `nextId`를 각각 `afterTime`, `afterId`로 전달한다.
같은 시각의 거래도 ID로 구분해 빠짐없이 페이지를 이동한다. 최대 500건을 반환한다.
`(dataset_id, account_ref, occurred_at, id)` 복합 인덱스를 적용했다.
반입 테이블은 내부 `accounts`, `transfers`, `ledger_entries`를 변경하지 않는다.
원본 건수와 생성 건수는 `imported_datasets.synthetic`, `row_count`로 별도 집계한다.

기존 브랜치와 같이 `X-User-Id`는 로컬 실험용 식별자다. 운영에서는 인증된 사용자로 대체해야 한다.
원본 파일·매핑 파일·아카이브는 Git에서 제외한 `datop-data/` 아래에 둔다.

## 재현과 검증

JDK 17을 사용한다. 저장소의 `gradle.properties`에 있는 Java 경로가 로컬과 다르면 다음처럼 덮어쓴다.

```powershell
.\gradlew.bat '-Dorg.gradle.java.home=C:/Program Files/Java/jdk-17' -PlocalTest test :app:bootJar
.\gradlew.bat '-Dorg.gradle.java.home=C:/Program Files/Java/jdk-17' :openbanking:externalQueryBenchmark
python -m unittest discover -s scripts -p 'test_*.py'
```

`-PlocalTest`는 app 회귀 테스트에만 임시 H2를 사용한다. MySQL 전용 잠금·동시성 테스트는
기존 `COREBANKING_INTEGRATION=true` 조건을 유지하며 이 실행에서는 건너뛴다.
성능 실험은 일반 테스트와 분리했고 출력은 `openbanking/build/reports/*benchmark.json`에 저장한다.
캐시 적중·강제 갱신·조건별 분리·소유권 확인·연결 취소·동시 요청 합치기·과부하 거절·캐시 장애,
반입 중복 방지·배치 후 오류 롤백·원본 보관·동일 시각 페이지 이동·Redis 직렬화를 테스트한다.

로컬 API의 HTTP 응답시간·오류율·처리량 측정 도구도 제공한다.

```powershell
python scripts/benchmark_external_queries.py 'http://localhost:8080/api/external-accounts/ACCOUNT_ID/balance' --owner demo-user --requests 100 --concurrency 1 --refresh
python scripts/benchmark_external_queries.py 'http://localhost:8080/api/external-accounts/ACCOUNT_ID/balance' --owner demo-user --requests 100 --concurrency 1
```

`--refresh`는 캐시를 우회한다. 동시성 1에서 비교한 뒤 동시 요청 합치기는 별도 실험한다.
캐시 사용 실행은 측정 전 1회 워밍업한다. TTL 안에서 수행해야 반복 조회 적중을 비교할 수 있다.
동시성, 요청 수, TTL, 데이터 건수와 실제 외부 HTTP 호출 수를 함께 기록한다.
오류 응답도 지연 통계에 포함하고 상태별 건수를 보고한다.
부하 테스트는 로컬 모의 API를 연결한 CoreBanking에 수행한다. 공개 테스트베드에 부하를 보내지 않는다.

측정 결과와 한계는 [로컬 실험 기록](benchmarks/2026-11-21-external-queries.md)에 별도로 기록한다.
