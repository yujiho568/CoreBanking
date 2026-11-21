# Mock 데이터로 실행하는 오픈뱅킹 데모

실제 테스트베드의 응답 데이터 관리 권한 없이 개발을 이어가기 위한 로컬 모드다.
SQLite에 생성 계좌 거래를 저장하고, 금융결제원 형태의 HTTP 응답을 제공한다.
CoreBanking은 기존 OAuth 서비스, HTTP 어댑터, 외부 계좌 DB와 캐시를 그대로 사용한다.
실제 금융결제원 연동 설정과 테스트 기록은 유지한다.

## 실행

프로젝트 루트에서 두 터미널을 사용한다. Python 표준 라이브러리만 필요하다.

터미널 1 — Mock API:

```powershell
python scripts/mock_openbanking.py --delay-ms 30
```

터미널 2 — CoreBanking:

```powershell
.\gradlew.bat '-Dorg.gradle.java.home=C:/Program Files/Java/jdk-17' :app:mockBootRun
```

JDK 경로는 설치 환경에 맞게 조정한다. 이 작업은 H2를 데모 실행 클래스패스에만 추가한다.
실제 `.env`의 금융결제원 키를 읽거나 수정하지 않는다.

브라우저에서 [Mock 데모](http://localhost:8082/mock-demo)를 연다.

1. **Mock 계좌 연결**을 누른다. 새 탭에서 가짜 인증 코드 발급과 콜백 처리가 자동 완료된다.
2. 원래 데모 탭에서 **연결 계좌 불러오기**를 누른다.
3. 계좌를 선택해 잔액과 거래내역을 조회한다.
4. 같은 조회를 반복한 뒤 **외부 호출 횟수**를 확인한다.
5. 새로고침 버튼을 누르면 외부 Mock API를 다시 호출하고 `fetchedAt`을 갱신한다.

목록·잔액·거래는 모두 생성 데이터다. 사용자 식별자는 데모 전용 `mock-demo-user`다.
실제 사용자 본인인증·은행 계좌·금융결제원 서버는 사용하지 않는다.

## 구성과 데이터

| 구성 | 값 |
|---|---|
| CoreBanking | `127.0.0.1:8082`, `mock` 프로필 |
| 독립 Mock 서버 | `127.0.0.1:9099` |
| 계좌 | 가짜 계좌 2개 |
| 거래 | 기본 총 1,000건, 계좌별 500건 |
| 날짜 | `2026-11-01 09:00`부터 분 단위 생성 |
| Mock 거래 저장 | `build/mock-demo/provider.sqlite` |
| 애플리케이션 저장 | 전용 메모리 H2, 앱 종료 시 사라짐 |
| 캐시 | 메모리 `ConcurrentMapCache` |

SQLite 파일은 다시 실행해도 유지된다. `--rows`를 바꾸려면 새로운 `--database` 경로를 지정한다.
행 수가 다르면 기존 DB를 삭제·덮어쓰지 않고 시작을 거부한다.
애플리케이션이나 Mock 서버를 재시작한 뒤에는 Mock 계좌 연결을 다시 실행한다.

```powershell
python scripts/mock_openbanking.py --rows 30000 --database build/mock-demo/provider-30000.sqlite
```

이 데모는 캐시 적중과 명시적 갱신을 검증하기 위해 메모리 캐시를 사용한다.
**Redis용 TTL 설정은 이 모드에 적용되지 않는다.** 자동 만료나 실제 Redis 성능을 검증한 결과로 해석하지 않는다.
실제 테스트베드 모드의 잔액 10초·거래내역 60초 Redis 설정은 기존 구현을 유지한다.

## 제공하는 Mock API

- `GET /oauth/2.0/authorize`: 가짜 일회용 코드 발급 후 지정한 로컬 콜백으로 이동
- `POST /oauth/2.0/token`: 코드 교환·Refresh Token 갱신, 가짜 토큰 발급
- `GET /v2.0/user/me`: 조회 동의한 가짜 계좌 2개 반환
- `GET /v2.0/account/balance/fin_num`: SQLite 거래의 최종 잔액 반환
- `GET /v2.0/account/transaction_list/fin_num`: 기간·입출금·정렬·25건 페이지 조회
- `GET /health`, `GET /stats`: Mock 상태와 경로별 호출 횟수 확인

실제 어댑터의 폼 전송·Bearer 인증·JSON 매핑·응답코드 검사·다중 페이지 호출을 통과한다.
Mock 인증은 실제 OAuth 제공자의 보안 동작 전체를 재현하는 구현이 아니다.
고정 가짜 자격증명만 허용하며, 외부 Callback 주소로는 이동하지 않는다.
Mock 전용 연결·데모 엔드포인트는 기본 프로필에서는 등록되지 않는다.

## 오류와 지연 실험

서버를 종료한 뒤 아래 옵션으로 다시 시작한다.

```powershell
python scripts/mock_openbanking.py --delay-ms 1000
python scripts/mock_openbanking.py --failure http500
python scripts/mock_openbanking.py --failure api-error
```

지연·오류 주입은 잔액·거래내역 조회에만 적용한다. 오류 모드에서도 Mock 계좌 연결은 가능하다.
Mock 서버를 재시작하면 이전 가짜 토큰은 무효가 된다.
기본 읽기 타임아웃을 넘는 `--delay-ms 6000`으로 지연 실패도 재현할 수 있다.
공개 금융결제원 테스트베드에 부하를 보내지 않는다.

## 검증

```powershell
python -m unittest discover -s scripts -p 'test_*.py'
python scripts/verify_mock_demo.py
```

두 번째 명령은 두 서버가 기본 설정으로 실행된 상태에서 사용한다.
Mock 연결, 계좌 2개, 잔액 캐시·강제 갱신, 3페이지 거래 75건, 최대 페이지 수별 캐시 분리,
캐시 적중 시에도 소유권 확인, 잘못된 페이지 제한의 외부 호출 차단을 검증한다.
결과는 `build/mock-demo/verification.json`에 저장한다.

2026-11-21 로컬 검증에서 모든 항목이 통과했다. 30ms 지연을 넣은 전체 HTTP API 순차 요청
30건 비교 결과는 다음과 같다. 소유권 DB 조회와 HTTP 어댑터 비용을 포함한다.

추가 검증: Java 회귀 테스트 27건 통과, MySQL 전용 7건은 기존 조건에 따라 건너뜀.
Python HTTP·정규화 테스트 4건, Mock 전체 흐름 검증과 실행 JAR 빌드도 통과했다.

| 조건 | 평균 ms | p95 ms | 외부 Mock 호출 | 오류 |
|---|---:|---:|---:|---:|
| 매번 새로고침 | 54.27 | 67.88 | 30 | 0 |
| 캐시 재사용 | 11.88 | 28.54 | 0 | 0 |

작은 표본의 로컬 H2·메모리 캐시 실험이다. 금융결제원·MySQL·Redis 성능 수치가 아니다.
원시 결과는 [HTTP 측정](benchmarks/2026-11-21-mock-http-performance.json),
[기능 검증](benchmarks/2026-11-21-mock-verification.json)에 보관한다.
