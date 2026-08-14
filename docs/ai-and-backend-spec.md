# AI 및 Firebase 백엔드 명세

## 호출 구조

```text
Android 앱
  → Firebase Callable Function: openAiGenerate (asia-northeast3)
  → OpenAI Responses API
  → 구조화된 결과를 앱으로 반환
```

앱은 `OpenAiProvider`와 `AiModelRouter`를 통해 기능별 프롬프트와 모델을 호출한다. API 키는 앱 코드나 APK에 저장하지 않고 Firebase Secret Manager의 `OPENAI_API_KEY`를 Cloud Function 런타임 환경변수로 주입한다.

## 사용 위치

- 건강 기록: 체중·식단·마운자로 통합 분석, 일별 총 섭취 칼로리 추정
- 건강검진: 문서 검사값 추출, 연도별 건강 추이, 선택검사 추천
- 부동산 경매: 사건 권리 분석, AI 추천
- 부동산 세금: Rule Engine 계산 결과의 위험·누락 정보·추가 시나리오 설명
- AI 챙김: 기기 데이터의 일정·기한·쿠폰 등 구조화

## 모델 정책

사용자 선택이 제공되는 분석 화면은 Luna/Terra를 선택할 수 있다. AI 건강 경과 분석은 품질 우선을 위해 Terra와 reasoning effort `high`로 고정한다. 자동 경매 추천은 Luna를 사용한다. Callable Function은 `none / low / medium / high / xhigh / max` reasoning effort 요청을 지원하며, 실제 지원 수준·비용은 모델 및 OpenAI 프로젝트의 결제·사용량 설정에 따라 달라진다.

부동산 세금 정밀분석은 예외적으로 `gpt-5.6-sol`과 Responses API의 reasoning effort `max`를 사용한다. 앱 요청값뿐 아니라 Callable Function에서도 이 조합을 강제한다. 매 요청마다 `web_search`를 필수 도구로 지정하고 국가법령정보센터·국세청·기획재정부·행정안전부·위택스·국토교통부 공식 도메인만 검색한다. 공식 출처가 반환되지 않으면 분석을 성공 처리하지 않는다. 세액은 최신 공식 법령과 현재 Rule Engine이 일치한다고 검증된 경우에만 Engine 결과를 원본으로 설명하며, 모델은 계산값을 수정하거나 새로운 세액을 산출할 수 없다. 변경을 감지하면 화면에 시행일·경과규정·영향·공식 링크를 표시하고 해당 계산값을 안전한 결과로 표시하지 않는다. 같은 세금 시뮬레이션의 직전 분석은 비교 데이터로 함께 보내되 응답 캐시로 사용하지 않는다. 새 응답은 이전 오류 정정, 새 차이, 유지되는 판단을 구조화하고 공식 검색 결과와 함께 새 분석 이력으로 저장한다.

## Firebase 구성

- Android: `app/google-services.json`
- Functions: `functions/index.js`, Node.js 의존성은 `functions/package.json`
- 배포 설정: `firebase.json`, `.firebaserc`
- 푸시 알림 및 백그라운드 작업은 Android의 FCM/로컬 알림·WorkManager/AlarmManager를 함께 사용한다.

## 보안 및 비용

- OpenAI 키는 Secret Manager에서만 관리한다.
- 분석 버튼이나 자동 작업은 실제 API 비용을 발생시킬 수 있다.
- 세금 분석은 매번 실시간 웹 검색을 수행하므로 캐시형 분석보다 지연시간과 API 비용이 커질 수 있다.
- 민감한 기기 데이터 분석은 사용자가 해당 소스를 활성화한 경우에만 수행한다.
