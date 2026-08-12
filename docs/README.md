# 소림파워 기능 명세

이 문서는 현재 Android 앱 구현을 기준으로 정리한 기능 명세다. 과거 `결정 기록` 기능은 화면과 데이터 흐름에서 제거되어 포함하지 않는다.

| 문서 | 범위 |
| --- | --- |
| [앱 공통 및 홈](app-and-home-spec.md) | 앱 구조, 탐색, 홈, 더보기 |
| [앱 차단](app-blocker-spec.md) | 차단 규칙, 접근성 서비스, 비밀번호 |
| [건강 기록 및 검진](health-spec.md) | 체중·식사·주사 기록, 건강검진 분석 |
| [부동산 경매](auction-spec.md) | 사건 수집, 관심, 종료 결과, AI 분석·추천 |
| [부동산 세금](property-tax-spec.md) | 자산 포트폴리오, 취득·보유·양도세 Rule Engine, 시뮬레이션·공식 최신 법령 검증 |
| [AI 챙김](phone-insight-spec.md) | 휴대폰 데이터 수집, AI 분석, 알림 정책 |
| [AI 및 Firebase](ai-and-backend-spec.md) | OpenAI 호출 경로, 비밀값, Cloud Functions |

## 공통 원칙

- 개인용 Android APK를 기준으로 한다.
- 기록·분석 결과는 기능별 Room 로컬 DB에 저장한다.
- OpenAI API 키는 앱에 두지 않고 Firebase Cloud Functions의 Secret Manager를 거쳐 사용한다.
- AI 결과는 참고용이다. 경매 권리분석과 건강 관련 의견은 최종 의사결정이나 의료 진단을 대체하지 않는다.
