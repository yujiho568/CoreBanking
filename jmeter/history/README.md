# 과거 자료 출처

- 원본 경로: `jmeter/corebanking-read-transaction-benchmark.jmx`
- 작업 시작 기준 HEAD: `a1ce6d7` (main). 확장자는 jmx였지만 내용은 CSV였다.
- 위 파일의 원본 바이트를 `corebanking-read-transaction-legacy.csv`에 복사했다.
- SHA-256: `1e8bc1f71f4cd653668848f26cdfdd67dc26308b78e43bcb8175d17a5508ba6b`
- 실행 계획 복원 출처: `git show 406fdc7:jmeter/corebanking-read-transaction-benchmark.jmx`.
- 복원 기본값: threads=5, rampUp=5, loops=300, 단일 계좌, limit=50.
- CSV가 README의 194ms/52ms 실험을 뒷받침한다고 확정하지 않는다.
- 기존 사용자가 수정 중인 캐시 벤치마크 JMX는 변경하지 않았다.
