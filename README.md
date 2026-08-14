# 나잘알

개인용 APK 설치를 전제로 만든 Kotlin·Jetpack Compose Android 앱입니다. 생활 관리, 건강 기록, 부동산 경매·세금, 휴대폰 데이터 기반 AI 챙김을 하나의 앱에서 제공합니다.

> 버전: `v0.13.5` · 최소 Android 8.0(API 26) · target SDK 35

## 주요 기능

### 관점 확장

- 활성 YouTube MediaSession 기반 제목·채널·재생시간 자동 기록
- Gemini Flash-Lite 저비용 주제 제안과 알림에서 주제 등록/건너뛰기
- 요청형 Gemini 영상·음성 심층 분석과 GPT 공개정보 fallback
- 실제 관심 비중과 최근 탐색 경로 중심 홈, 영상 썸네일이 포함된 주제별 사고 확장 지도
- 내가 안 본 세상, 생각이 넓어진 순간, 주간 정보 편식 리포트
- 최근 영상 중심 탐색과 별도 시청 기록, 영상 분석 캐시 및 승인 주제의 이름·설명 수정/수집 ON·OFF

### AI 챙김

사용자가 허용한 휴대폰 데이터를 통합 분석해 놓치기 쉬운 일정과 기한을 찾아줍니다.

- 문자, 앱 알림, 사진·이미지, 파일·문서, 통화 녹음, 앱 사용 기록, 캘린더, 연락처, 통화 기록을 선택적으로 분석
- 반복 캘린더 일정은 실제 발생일 기준으로 수집
- 앱 내 챙길 항목은 오늘부터 14일 이내 일정·기한을 표시
- 쿠폰·교환권도 동일한 14일 기준 적용
- 매일 오전 8시에 새 정보를 분석하고, 오늘·내일 항목만 요약 푸시 알림
- 일정 시각에는 개별 로컬 알림 예약
- 완료·넘기기 처리, 유사 항목 병합, 소스별 권한·실행 이력 관리

### 앱 차단

- 접근성 서비스를 활용한 선택 앱 차단
- 요일·시간대·매주/격주/매월 반복 조건 및 앱별 규칙
- 차단 해제 조건, 긴급 1회 사용, 비밀번호 보호
- 차단 시도 횟수와 사용자 안내 문구 관리

### 건강 기록 및 건강검진

- 체중, 마운자로 주사, 식사, 식사 사진, 빠른 식사 기록
- 식사 저장·수정 직후 GPT-5.6 Luna가 개별 칼로리를 추정하고 해당 날짜 총합을 즉시 로컬 계산
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

### 부동산 세금

- 보유 주택 포트폴리오와 취득가·공시가격·지분·보유기간 관리
- 현재 상황과 매도·입주 계획을 자연어로 입력하는 AI 매도계획 시뮬레이션
- 공식 최신 법령을 검색해 자산 타임라인, 추천 매도 순서, 비과세 가능성·실패 조건과 법정 기한 비교
- 버전이 고정된 Rule Engine으로 취득세·재산세·종합부동산세·양도소득세 계산
- 적용 규칙, 계산 단계, 법령·국세청 출처, 확인 필요 조건을 결과와 함께 표시
- 가상 매도 시뮬레이션 저장, 재계산 전후 Revision 이력 보존
- 1주택+1분양권 특례, 조정대상지역 다주택 취득·양도 중과, 12억원 1주택 비과세 판정
- 부부 공동명의 지분별 재산세·양도세와 종부세 개별 과세/공동명의 1주택 특례 비교
- 분석할 때마다 GPT-5.6 Sol(max)이 공식 법령을 실시간 검색해 현재 Rule Engine과 비교하고, 변경 감지 시 계산값 확정 사용을 차단
- 같은 매도 시뮬레이션의 직전 AI 분석도 비교 자료로 사용해 이전 오류, 새 차이, 그대로 유효한 판단을 구분
- 확인에 사용한 공식 출처·시행일·경과규정·영향을 구조화해 표시하며 AI는 세율이나 계산식을 자동 변경하지 않음

### 공통

- 홈, 챙김, 차단, 기록, 경매, 세금, 더보기 하단 탭
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
│  ├─ propertytax/       부동산 세금 Rule Engine·시뮬레이션
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
- [부동산 세금](docs/property-tax-spec.md)
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
