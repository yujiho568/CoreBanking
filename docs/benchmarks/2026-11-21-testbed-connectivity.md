# 2026-11-21 실제 금융결제원 테스트베드 호출 확인

대상: `https://testapi.openbanking.or.kr`. 모의 서버가 아닌 실제 HTTPS 서버에 Python 표준
HTTP 클라이언트로 GET 요청을 각각 1회 보냈다. 인증 토큰과 조회 파라미터 없이 보낸 연결·인증 차단 확인이다.
CoreBanking 애플리케이션을 통한 정상 조회 통합 테스트는 아니다.

| 경로 | HTTP 상태 | 업무 응답 코드 | 관측 시간 ms |
|---|---:|---|---:|
| `/v2.0/user/me` | 200 | `O0001` | 272.6 |
| `/v2.0/account/balance/fin_num` | 200 | `O0001` | 47.3 |
| `/v2.0/account/transaction_list/fin_num` | 200 | `O0001` | 45.8 |

세 응답 모두 `인증요청거부-인증 파라미터 오류([992])`를 반환했다.
HTTP 200을 조회 성공으로 해석하면 안 된다. 정상 업무 코드 `A0000`은 확인되지 않았다.
시간은 각 단일 요청의 연결 포함 관측값이며 성능 비교 자료로 사용할 수 없다.

현재 프로젝트 `.env`와 프로세스 환경변수에서 Client ID, Client Secret, 이용기관코드,
사용자 Access Token을 찾지 못했다. 실제 비밀값은 출력하거나 문서에 저장하지 않았다.

정상 조회 검증에 남은 단계:

1. 발급받은 `OPEN_BANKING_CLIENT_ID`, `OPEN_BANKING_CLIENT_SECRET`, `OPEN_BANKING_CLIENT_USE_CODE` 설정.
2. 등록 Callback URL과 서버 설정 확인, 로컬 암호화 키 준비.
3. 사용자 OAuth 인증 및 토큰 발급, 테스트 계좌 등록 데이터 확인.
4. CoreBanking을 경유한 계좌 목록·잔액·거래내역의 정상 응답 확인.
5. 동일 조회 재요청과 `refresh=true`를 비교해 실제 외부 호출 계측값 확인.

실제 인증·정상 조회·토큰 갱신·실제 Redis/MySQL 연동 성공은 아직 검증하지 않았다.
