# 소림파워 (SorimPower)

개인용 APK 설치를 전제로 만든 Kotlin·Jetpack Compose Android 앱입니다. 생활 관리, 건강 기록, 부동산 경매, 휴대폰 데이터 기반 AI 챙김을 하나의 앱에서 제공합니다.

> 버전: `v0.6.0` · 최소 Android 8.0(API 26) · target SDK 35

## 주요 기능

### AI 챙김

사용자가 허용한 휴대폰 데이터를 통합 분석해 놓치기 쉬운 일정과 기한을 찾아줍니다.

- 문자, 앱 알림, 사진·이미지, 파일·문서, 통화 녹음, 앱 사용 기록, 캘린더, 연락처, 통화 기록을 선택적으로 분석
- 반복 캘린더 일정은 실제 발생일 기준으로 수집
- 앱 내 챙길 항목은 오늘부터 14일 이내 일정·기한을 표시
- 쿠폰·교환권도 동일한 14일 기준 적용
- 매일 오전 8시와 오후 7시에 새 정보를 분석하고, 오늘·내일 항목만 요약 푸시 알림
- 일정 시각에는 개별 로컬 알림 예약
- 완료·필요 없음 처리, 유사 항목 병합, 소스별 권한·실행 이력 관리

### 앱 차단

- 접근성 서비스를 활용한 선택 앱 차단
- 요일·시간대·매주/격주/매월 반복 조건 및 앱별 규칙
- 차단 해제 조건, 긴급 1회 사용, 비밀번호 보호
- 차단 시도 횟수와 사용자 안내 문구 관리

### 건강 기록 및 건강검진

- 체중, 마운자로 주사, 식사, 식사 사진, 빠른 식사 기록
- 월 기준 체중 추이와 달력 기반 일일 기록
- 체중·식단·주사 기록을 함께 보는 AI 건강 경과 분석
- 건강검진 PDF/이미지에서 검사값 추출, 검진일 수정, 여러 연도 추이 분석
- 다음 검진의 유료 선택검사 PDF 분석 및 추천

### 부동산 경매

- 법원경매정보 API를 앱에서 직접 수집하고 로컬 캐시로 표시
- 서울 아파트, 감정가 15억 이상 조건의 진행 사건 조회
- 검색·정렬·관심 사건·종료 사건·새 사건 배지
- 사건번호 복사, 아파트명 기반 법원 경매/지도 이동, 매각기일 캘린더 등록
- 종료 사건의 낙찰·취하 등 최종 결과 조회
- 사건별 AI 권리 분석과 Luna/Terra 모델 선택
- 새 사건 중심의 매일 아침 AI 맞춤 추천 및 로컬 알림

### 공통

- 홈, 챙김, 차단, 기록, 경매, 더보기 하단 탭
- 시작 화면 선택, 앱 정보, 접근성 권한, 비밀번호 설정
- 개인 기록과 분석 결과는 기능별 Room 로컬 DB에 저장

## AI 호출 구조

```text
Android 앱
  → Firebase Callable Function (asia-northeast3)
  → OpenAI API
  → 구조화된 분석 결과를 앱에 저장
```

OpenAI API 키는 APK에 넣지 않습니다. Firebase Secret Manager의 `OPENAI_API_KEY`를 Cloud Functions에 주입하는 방식입니다. AI 분석은 API 비용이 발생할 수 있으며, 건강·경매 분석은 최종 진단이나 투자 결정을 대체하지 않습니다.

## 프로젝트 구조

```text
app/src/main/java/com/sorimpower/app/
├─ core/                 공통 앱 셸, 테마, AI 호출
├─ feature/
│  ├─ blocker/           앱 차단
│  ├─ bodylog/           체중·식사·주사 기록
│  ├─ healthcheckup/     건강검진 기록·분석
│  ├─ auction/           부동산 경매
│  ├─ phoneinsight/      AI 챙김
│  ├─ home/              홈
│  └─ settings/          설정
└─ MainActivity.kt       앱 시작점

functions/               Firebase Cloud Functions OpenAI 프록시
docs/                    현재 기능 명세
```

## 기능 명세

전체 기능 기준 문서는 [docs/README.md](docs/README.md)에서 확인합니다.

- [앱 공통 및 홈](docs/app-and-home-spec.md)
- [앱 차단](docs/app-blocker-spec.md)
- [건강 기록 및 건강검진](docs/health-spec.md)
- [부동산 경매](docs/auction-spec.md)
- [AI 챙김](docs/phone-insight-spec.md)
- [AI 및 Firebase](docs/ai-and-backend-spec.md)

## 개발 환경 및 빌드

- Android Studio Ladybug 이상
- JDK 17
- Android SDK 35
- Firebase 프로젝트 및 `app/google-services.json`

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

생성 APK는 `app/build/outputs/apk/debug/app-debug.apk`에 위치합니다.

## Firebase Functions 설정

```bash
npm --prefix functions install
firebase login
firebase functions:secrets:set OPENAI_API_KEY
firebase deploy --only functions:openAiGenerate
```

Functions 배포에는 Firebase 결제 계정이 필요할 수 있습니다. API 키·GitHub 토큰 등 민감한 값은 저장소나 소스 코드에 커밋하지 않습니다.

## 권한 안내

- `QUERY_ALL_PACKAGES`: 차단할 설치 앱 목록 표시
- 접근성 권한: 앱 차단 동작
- 알림 권한: 경매·AI 챙김·주사 알림
- AI 챙김 소스별 권한: 문자, 캘린더, 연락처, 통화 기록, 앱 알림 접근, 파일·사진 폴더 등

각 권한은 해당 기능을 사용자가 켤 때만 요청하거나 설정으로 안내합니다.
