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

- 건강 기록: 체중·식단·마운자로 통합 분석
- 건강검진: 문서 검사값 추출, 연도별 건강 추이, 선택검사 추천
- 부동산 경매: 사건 권리 분석, AI 추천
- AI 챙김: 기기 데이터의 일정·기한·쿠폰 등 구조화

## 모델 정책

사용자 선택이 제공되는 분석 화면은 Luna/Terra를 선택할 수 있다. 자동 경매 추천은 Luna를 사용한다. 실제 사용 가능 모델과 비용은 OpenAI 프로젝트의 결제·사용량 설정에 따라 달라진다.

## Firebase 구성

- Android: `app/google-services.json`
- Functions: `functions/index.js`, Node.js 의존성은 `functions/package.json`
- 배포 설정: `firebase.json`, `.firebaserc`
- 푸시 알림 및 백그라운드 작업은 Android의 FCM/로컬 알림·WorkManager/AlarmManager를 함께 사용한다.

## 보안 및 비용

- OpenAI 키는 Secret Manager에서만 관리한다.
- 분석 버튼이나 자동 작업은 실제 API 비용을 발생시킬 수 있다.
- 민감한 기기 데이터 분석은 사용자가 해당 소스를 활성화한 경우에만 수행한다.
