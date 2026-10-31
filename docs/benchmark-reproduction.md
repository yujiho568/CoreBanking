# 성능 측정 재현 기록

README의 인덱스 평균 194→52ms와 캐시 평균 26.02→9.31ms, 처리량 177.5→408.3/sec는 각 벤치마크의 측정값이다.
인덱스 미적용 1,650건과 적용 1,500건은 요청 수가 다르므로 통제된 동일 조건 비교로 확정하지 않는다.
고정 사용자 수에서 관측한 처리량은 시스템의 최대 처리 능력을 의미하지 않는다.

## 실행 절차

1. 전용 실험 DB를 준비한다. 원장 실제 COUNT, 계좌별 분포와 EXPLAIN을 저장한다.
2. `run-metadata.template.json`을 실행별 폴더에 복사하고 모든 REQUIRED와 null을 실제 값으로 채운다.
3. Java/JMeter/DB/Redis 실제 버전, CPU/RAM, 컨테이너 제한, 커밋과 미커밋 diff를 보존한다.
4. 인덱스 비교는 앱에 `SPRING_CACHE_TYPE=none`을 적용하고 재시작한다. 새 캐시 우회 상태 조회도 NoOpCacheManager에서는 생략한다.
5. DB 버퍼 warm/cold 조건을 기록한다. warm은 동일 사전 조회 수와 절차를 사용한다. cold 실험은 전용 DB에서 수행하고 재시작만으로 OS 캐시까지 cold가 된다고 주장하지 않는다.
6. 워밍업 결과와 본 측정 결과는 서로 다른 파일에 저장한다. 비교군의 데이터·계좌 분포·limit·동시 사용자·ramp-up·반복 수를 동일하게 맞춘다.
7. 아래 명령처럼 모든 매개변수를 명시한다. 기존 결과 파일에 덧붙이지 않도록 새 폴더를 사용한다.

```powershell
jmeter -n -t jmeter/corebanking-read-transaction-benchmark.jmx -l <new-run>/warmup.jtl -Jthreads=5 -JrampUp=5 -Jloops=20 -Jhost=localhost -Jport=8080 -JaccountId=PHASEB-ACC-000001 -Jlimit=50
jmeter -n -t jmeter/corebanking-read-transaction-benchmark.jmx -l <new-run>/measured.jtl -e -o <new-run>/html -Jthreads=5 -JrampUp=5 -Jloops=300 -Jhost=localhost -Jport=8080 -JaccountId=PHASEB-ACC-000001 -Jlimit=50
python jmeter/summarize-results.py <new-run>/measured.jtl
```

`<new-run>`은 실제 새 디렉터리 경로로 교체한다. JMeter CSV 저장 형식에 timeStamp, elapsed, success 필드가 있어야 한다.
이 XML은 단일 계좌 분포다. 다른 분포는 별도 계획과 CSV로 명시한다.
캐시 비교는 같은 계획을 사용하고 앱 캐시 none/redis를 구분한다. 초기화·워밍업·outbox 미처리 건수도 기록한다.
출력 JSON과 JMeter HTML의 평균/P95/P99/오류율/관측 처리량을 보존한다. 백분위 계산 방식과 측정 시간창도 함께 비교한다.
계획 XML 문법 검사와 실제 JMeter 실행 성공은 서로 다른 검증이다.
