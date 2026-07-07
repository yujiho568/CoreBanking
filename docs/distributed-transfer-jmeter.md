# 분산 이체 JMeter 플랜

이 플랜은 하나의 hot account만 때리는 대신 여러 계좌 쌍으로 `POST /api/transfers` 요청을 보냅니다.
Phase C에서 DB 락 방식과 Redis 기반 distributed lock 방식을 비교하기 위한 기준 트래픽입니다.

## CSV 생성

저장소 루트에서 실행합니다.

```powershell
.\jmeter\generate-distributed-transfer-csv.ps1 `
  -AccountCount 1000 `
  -Rows 50000 `
  -OutputPath jmeter/distributed-transfer-requests.csv
```

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

CSV 생성기는 Phase B 시드 계좌 ID를 사용합니다.

```text
PHASEB-ACC-000001 ... PHASEB-ACC-001000
```

CSV row 수는 최소 다음 값 이상이어야 합니다.

```text
threads * loops
```

JMeter 플랜은 CSV row를 재사용하지 않습니다. 파일을 모두 읽으면 같은 분포를 반복하지 않고 스레드를 중지합니다.

## 실행

측정할 lock mode로 앱을 시작합니다.

```powershell
docker compose build app
docker compose up -d app
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

## 참고

- CSV는 계좌 분포와 금액만 제어합니다.
- `idempotencyKey`는 JMeter `__UUID()`로 요청마다 생성합니다.
- 긴 테스트 중 계좌 잔액이 빠르게 소진되지 않도록 기본 이체 금액은 작게 잡았습니다.
- hot account 벤치마크와 비교하면 row 경합과 일반 write throughput을 분리해서 볼 수 있습니다.
