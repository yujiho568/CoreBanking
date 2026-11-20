# 금융결제원 오픈뱅킹 테스트베드 연동

## 적용 범위

기존 `account`, `transfer`, `ledger` 모듈은 자체 계좌와 내부 이체의 원장입니다. 새 `openbanking`
모듈은 사용자가 금융결제원에 등록한 외부 계좌를 조회하며, 외부 잔액을 자체 계좌 잔액에 반영하거나
외부 거래내역을 자체 원장에 기록하지 않습니다.

현재 구현 범위:

- OAuth 2.0 Authorization Code 방식의 사용자 인증 시작과 callback
- `state`의 10분 만료, 1회 사용 및 DB 저장
- 사용자정보조회 결과의 외부 계좌 동기화
- 핀테크이용번호 기반 잔액조회
- 핀테크이용번호 기반 거래내역조회와 최대 페이지 제한
- 외부 계좌 소유자 확인
- Access Token 만료 전 Refresh Token 갱신
- 연결·읽기 타임아웃 및 API/은행 응답코드 확인
- Access Token, Refresh Token, 핀테크이용번호 AES-256-GCM 암호화 저장

출금·입금이체는 포함하지 않습니다. 외부 이체는 기존 내부 이체의 단일 DB 트랜잭션으로 처리할 수
없으므로 별도 상태 머신과 결과 대사 설계를 추가한 뒤 확장해야 합니다.

## 테스트베드 준비

금융결제원 개발자사이트에서 다음 절차를 완료합니다.

1. 오픈뱅킹 서비스를 신청하고 API Key를 등록합니다.
2. Callback URL에 `http://localhost:8080/api/open-banking/oauth/callback`을 등록합니다.
3. 조회 API용 테스트 응답 데이터를 등록합니다.
4. 발급받은 Client ID, Client Secret, 이용기관코드를 `.env`에 입력합니다.

암호화 키는 32바이트 난수를 Base64로 변환한 값이어야 합니다. PowerShell 생성 예시:

```powershell
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
[Convert]::ToBase64String($bytes)
```

필수 설정:

```env
OPEN_BANKING_CLIENT_ID=발급값
OPEN_BANKING_CLIENT_SECRET=발급값
OPEN_BANKING_CLIENT_USE_CODE=10자리이용기관코드
OPEN_BANKING_REDIRECT_URI=http://localhost:8080/api/open-banking/oauth/callback
OPEN_BANKING_ENCRYPTION_KEY=Base64로인코딩한32바이트키
```

실제 비밀값은 `.env.example`, Git 이력, 프론트엔드 코드에 입력하지 않습니다. 암호화 키를 잃으면
기존 암호문을 복구할 수 없으므로 운영에서는 별도 비밀관리 시스템과 키 회전 절차가 필요합니다.

## 호출 흐름

브라우저에서 인증 시작 API를 호출합니다.

```http
GET /api/open-banking/oauth/authorize
X-User-Id: demo-user
```

서버는 임의 `state`를 저장한 뒤 금융결제원 인증 페이지로 302 redirect합니다. 인증 완료 callback에서
토큰을 교환하고 `/v2.0/user/me` 결과에 포함된 계좌를 `external_accounts`에 동기화합니다.

연결 계좌 목록:

```http
GET /api/external-accounts
X-User-Id: demo-user
```

잔액조회:

```http
GET /api/external-accounts/{externalAccountId}/balance
X-User-Id: demo-user
```

거래내역조회:

```http
GET /api/external-accounts/{externalAccountId}/transactions?fromDate=2026-11-01&toDate=2026-11-20&inquiryType=A&sortOrder=D&maxPages=3
X-User-Id: demo-user
```

`inquiryType`은 전체 `A`, 입금 `I`, 출금 `O`이며 `sortOrder`는 내림차순 `D`, 오름차순 `A`입니다.
금융결제원은 한 페이지에 최대 25건을 반환합니다. 서버는 `befor_inquiry_trace_info`를 다음 호출에
전달하며 `maxPages`는 1~20으로 제한해 한 HTTP 요청이 외부 API를 무제한 호출하지 않도록 합니다.
제한에 도달하면 응답의 `hasMore`와 `nextTraceInfo`로 남은 페이지를 알립니다.

## 보안 경계

현재 프로젝트에는 회원 로그인과 Spring Security가 없습니다. 그래서 예제의 `X-User-Id`는 구조를
검증하기 위한 임시 값이며 클라이언트가 임의로 바꿀 수 있습니다. 운영 전에는 인증된 Principal에서
사용자 ID를 얻도록 변경해야 합니다. 그 전에는 이 API를 인터넷에 공개해서는 안 됩니다.

API 응답과 로그에는 Access Token, Refresh Token, 핀테크이용번호를 반환하지 않습니다. 계좌번호는
금융결제원이 제공한 마스킹 값만 반환합니다.

## 검증 전략

- 테스트베드: OAuth, 사용자정보, 잔액, 거래내역의 실제 계약 확인
- 단위 테스트: 타 사용자 계좌 접근 차단, 페이지 추적정보 전달, 최대 페이지 제한, 암호화 왕복
- 로컬 모의 서버: 응답 지연, 연결 실패, HTTP 오류, 토큰 갱신 실패
- JMeter: 금융결제원 자체 성능이 아니라 외부 지연 중 CoreBanking의 연결 풀·스레드·타임아웃 검증

테스트베드 응답 시간은 실제 금융기관 운영 성능으로 해석하지 않습니다.
