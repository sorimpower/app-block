# 소림파워 (SorimPower)

개인용 APK 설치를 목적으로 한 Kotlin·Jetpack Compose Android 앱입니다. Play Store 배포를 전제로 하지 않으며, 접근성 서비스를 통해 선택한 앱의 실행을 감지해 설정한 조건에 맞으면 차단 화면을 표시한 뒤 홈으로 이동합니다. 앱을 강제 종료하지 않습니다.

## 현재 기능

- Material 3 Expressive, Bento 카드, 반투명 플로팅 내비게이션을 조합한 기능 홈과 App Blocker 화면
- 요일, 시간대, 매주·2주마다·매월 반복, 매월 특정 날짜를 조합한 여러 차단·해제 조건
- 앱마다 서로 다른 조건을 선택하는 앱별 정책
- 차단 화면에 표시할 사용자 문구 설정(최대 120자)
- 차단 화면에 앱별 오늘 실행 시도 횟수를 크게 표시하는 경고 UI
- 숫자 비밀번호를 이용한 차단 실행·해제 설정 변경 보호
- 이름 또는 패키지명을 이용한 설치 앱 검색
- 앱 실행 시 기능 홈 또는 App Blocker를 시작 화면으로 선택
- 설정한 차단 앱·조건·문구·시작 화면·비밀번호를 DataStore에 로컬 저장

한 조건 안에서 활성화한 항목은 모두 만족해야 하며(AND), 앱에 할당된 여러 차단 조건 중 하나라도 만족하면 차단됩니다(OR). 할당된 해제 조건이 같은 시점에 만족하면 차단 조건보다 우선하여 앱을 허용합니다. 시간 범위는 `22:00~07:00`처럼 자정을 넘길 수 있으며, 2주 반복은 해당 반복 옵션을 선택한 날짜를 기준으로 계산합니다. 매월 반복은 기준일과 같은 일자에 적용되고, 월 특정 날짜를 켜면 선택한 일자가 이를 대신합니다.

설정 보호 비밀번호는 4~12자리 숫자로 설정합니다. 평문이 아니라 PBKDF2 해시와 임의 salt로 저장되며, 전체 차단, 개별 앱 차단, 조건 활성화·유형·할당·삭제 및 기존 비밀번호 변경·삭제에 필요합니다. 차단 화면에서는 비밀번호를 입력하거나 차단을 우회할 수 없으며 시스템 뒤로 가기도 홈으로 이동합니다.

차단 조건이 실제로 일치해 차단 화면이 열릴 때마다 앱별 실행 시도 횟수를 날짜별로 1회 증가시킵니다. 지난 날짜의 카운트는 다음 기록 시 제거되고 매일 0회부터 다시 시작합니다.

## 요구 환경

- Android Studio Ladybug 이상, JDK 17
- Android SDK 35 (프로젝트 `compileSdk`/`targetSdk`)
- 최소 지원 Android 8.0 (API 26)

## 주요 파일과 구조

- `app/src/main/java/com/sorimpower/app/MainActivity.kt`: Compose 홈·App Blocker·설정 화면과 접근성 설정 진입점
- `feature/blocker/presentation/BlockerViewModel.kt`: 설치 앱 목록과 화면 상태를 제공하는 MVVM ViewModel
- `feature/blocker/domain/BlockRule.kt`: 다중 조건의 요일·시간·반복·월 특정일 모델과 현재 적용 여부 계산
- `feature/blocker/service/AppBlockAccessibilityService.kt`: 다른 앱의 화면 전환을 감지하여 차단 처리
- `feature/blocker/presentation/BlockedActivity.kt`: 경고 UI에 차단 문구·앱 이름·오늘 실행 시도 횟수를 표시하고 홈으로 이동
- `data/BlockerRepository.kt`: DataStore에 차단 앱, 앱별 조건 할당, 다중 차단·해제 조건, 일일 실행 카운트, 문구, 시작 화면과 비밀번호 해시 저장
- `ui/Theme.kt`: 시스템 다크 모드에 따르는 Material 3 테마
- `app/src/main/AndroidManifest.xml`: 접근성 서비스와 전체 설치 앱 조회 권한 선언

향후 기능은 `feature/<기능명>` 패키지에 `domain`, `data`, `presentation`을 두고 홈 메뉴에 연결하면 됩니다. 기능이 추가되면 `StartDestination`에도 항목을 추가하여 시작 화면으로 선택할 수 있습니다.

## 빌드와 설치

1. Android Studio에서 이 폴더를 열고 SDK 35 및 JDK 17을 선택합니다.
2. 터미널에서 `./gradlew assembleDebug`를 실행합니다. (처음에는 Gradle 의존성 다운로드가 필요합니다.)
3. 생성 파일은 `app/build/outputs/apk/debug/app-debug.apk`입니다.
4. 휴대폰에서 개발자 옵션의 USB 디버깅을 켠 뒤 `adb install -r app/build/outputs/apk/debug/app-debug.apk`를 실행하거나, APK를 휴대폰에 옮겨 설치합니다. 출처를 알 수 없는 앱 설치 허용이 필요할 수 있습니다.
5. 앱의 설정 화면에서 **접근성 권한**을 눌러 “소림파워 앱 차단 서비스”를 활성화합니다. App Blocker를 켜고 앱을 선택하면 차단이 동작합니다.

## 유의 사항

`QUERY_ALL_PACKAGES` 권한은 설치된 앱 목록을 보여주기 위해 사용합니다. 본 프로젝트는 개인 설치용 APK이므로 Play Store의 해당 권한 정책 검토 대상이 아닙니다. 접근성 서비스는 앱 실행 감지 목적 외에는 사용하지 않도록 유지하세요.
