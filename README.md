# 소림파워 (SorimPower)

개인용 APK 설치를 목적으로 한 Kotlin·Jetpack Compose Android 앱입니다. Play Store 배포를 전제로 하지 않으며, 접근성 서비스를 통해 선택한 앱의 실행을 감지해 설정한 조건에 맞으면 차단 화면을 표시합니다. 차단 화면은 자동으로 닫히지 않으며 사용자가 홈으로 돌아가거나 긴급 사용 절차를 완료할 때까지 유지됩니다. 앱을 강제 종료하지 않습니다.

## 현재 기능

- 밝은 회색 캔버스, 흰색 라운드 카드, 보라·핑크 그라데이션 핵심 카드와 필 형태 활성 탭을 조합한 공통 UI
- 설치 앱의 실제 아이콘을 표시하는 앱별 차단 목록과 시스템 안전 영역을 고려한 하단 메뉴 바
- 네이비·코발트·오렌지 컬러의 적응형 런처 아이콘과 Android 13 이상 테마 아이콘
- Room에 체중·목표·식사·음식 항목·사진을 로컬 저장하는 Body Log
- 실제 측정 시각 간격, 선택 가이드와 탭 상세를 제공하는 일·주·월·연 체중 그래프 및 모든 기간에 상시 노출되는 월 달력
- 현재·목표 체중과 변화량을 가리는 Body Log 프라이버시 표시 모드
- 오늘 또는 과거 날짜를 직접 선택하는 체중·식사 기록
- 주사·식사를 날짜별 통합 목록으로 모두 보여주고, 사진을 탭하면 확대해 볼 수 있는 일일 기록
- 식사 삭제·사진 수정 후 참조되지 않는 원본·썸네일 파일을 즉시와 다음 실행 시 정리하는 로컬 사진 저장소 관리
- 마운자로 투여일·용량·부작용 기록, 월 달력의 주사 배지, 최신 기록에서 바꿀 수 있는 알림 ON/OFF 및 1~4주 반복 주기 설정
- 차단 조건·차단 메시지·차단 앱을 번호와 색상으로 구분한 설정 UI
- 요일, 시간대, 매주·2주마다·매월 반복, 매월 특정 날짜를 조합한 여러 차단·해제 조건
- 조건의 공통 활성화 스위치 없이 조건을 추가·편집·삭제하고, 앱마다 적용할 조건을 개별 선택하는 앱별 정책
- 접근성 차단 서비스가 설정 변경을 메모리에 구독해 앱 전환마다 저장소를 다시 읽지 않도록 최적화
- 차단 화면에 표시할 사용자 문구 설정(최대 120자)
- 사용자 문구를 강조하고 앱별 오늘 실행 시도 횟수를 일관되게 표시하는 차단 경고 UI
- 사용자가 직접 설정한 숫자 비밀번호를 이용한 차단 설정 변경 보호
- 꼭 앱을 사용해야 할 때 100자 확인 문구를 정확히 입력하면 해당 앱의 이번 실행만 허용하는 긴급 사용 기능
- 이름 또는 패키지명을 이용한 설치 앱 검색
- 앱 실행 시 기능 홈 또는 App Blocker를 시작 화면으로 선택
- 설정한 차단 앱·조건·문구·시작 화면·비밀번호 해시·1회성 긴급 사용 상태를 DataStore에 로컬 저장

한 조건 안에서 활성화한 항목은 모두 만족해야 하며(AND), 앱에 할당된 여러 차단 조건 중 하나라도 만족하면 차단됩니다(OR). 할당된 해제 조건이 같은 시점에 만족하면 차단 조건보다 우선하여 앱을 허용합니다. 시간 범위는 `22:00~07:00`처럼 자정을 넘길 수 있으며, 2주 반복은 해당 반복 옵션을 선택한 날짜를 기준으로 계산합니다. 매월 반복은 기준일과 같은 일자에 적용되고, 월 특정 날짜를 켜면 선택한 일자가 이를 대신합니다.

설정 보호 비밀번호는 사용자가 직접 입력하는 4~12자리 숫자입니다. 비밀번호는 평문이 아니라 PBKDF2 해시와 임의 salt로 저장되며, 전체 차단, 개별 앱 차단, 조건 유형·할당·삭제 및 기존 비밀번호 변경·삭제에 필요합니다.

차단 화면에는 자동 종료 타이머가 없습니다. 시스템 뒤로 가기와 **홈으로 돌아가기**는 홈 화면으로 이동합니다. 차단 화면은 소림파워 메인 화면과 별도 임시 태스크에서 열리므로, 화면을 벗어난 뒤 소림파워 아이콘을 누르면 정상적인 메인 화면이 열립니다. **정말 사용해야 해요**를 선택한 뒤 화면에 표시된 100자를 정확히 따라 입력하면 해당 앱의 이번 실행만 허용되고 원래 앱이 다시 열립니다. 다른 앱이나 홈으로 이동하면 허용 세션이 끝나므로 다음 실행부터 다시 차단됩니다.

차단 조건이 실제로 일치해 차단 화면이 열릴 때마다 앱별 실행 시도 횟수를 날짜별로 1회 증가시킵니다. 지난 날짜의 카운트는 다음 기록 시 제거되고 매일 0회부터 다시 시작합니다.

## 요구 환경

- Android Studio Ladybug 이상, JDK 17
- Android SDK 35 (프로젝트 `compileSdk`/`targetSdk`)
- 최소 지원 Android 8.0 (API 26)

## 주요 파일과 구조

- `app/src/main/java/com/sorimpower/app/MainActivity.kt`: 앱 생성과 시스템 권한 연결만 담당하는 공통 진입점
- `core/app/SorimPowerApp.kt`: 공통 화면 내비게이션과 앱 셸
- `core/ui/Theme.kt`: 공통 Material 3 색상·모양과 밝은 카드형 테마
- `feature/home/presentation/HomeScreen.kt`: 기능 홈
- `feature/settings/presentation/SettingsScreen.kt`: 공통 설정 화면
- `feature/blocker/data/BlockerRepository.kt`: App Blocker 전용 DataStore 저장소
- `feature/blocker/presentation/BlockerScreens.kt`: 차단 조건·앱별 정책·비밀번호 설정 UI
- `feature/blocker/presentation/BlockerViewModel.kt`: 설치 앱 목록과 차단 화면 상태를 제공하는 ViewModel
- `feature/blocker/domain/BlockRule.kt`: 다중 조건의 요일·시간·반복·월 특정일 모델과 현재 적용 여부 계산
- `feature/blocker/service/AppBlockAccessibilityService.kt`: 다른 앱의 화면 전환을 감지하여 차단 처리
- `feature/blocker/presentation/BlockedActivity.kt`: 강조된 차단 문구·앱 이름·오늘 실행 시도 횟수와 100자 긴급 사용 절차 표시
- `feature/bodylog/data/BodyLogDatabase.kt`: 체중·목표·마운자로 투여·식사·음식·사진 Room 스키마와 DAO
- `feature/bodylog/data/BodyLogRepository.kt`: Body Log 저장, 사진 가져오기·압축·삭제 및 참조되지 않는 사진 파일 정리
- `feature/bodylog/domain/BodyLogModels.kt`: 기간 집계와 일일 대표 체중 계산
- `feature/bodylog/presentation/BodyLogScreen.kt`: 대시보드·그래프·달력·체중·식사·사진 UI
- `feature/bodylog/presentation/BodyLogViewModel.kt`: Body Log 상태와 사용자 동작 연결
- `feature/bodylog/reminder/MounjaroReminder.kt`: 마운자로 기록 알림의 반복 예약·표시 및 재부팅 후 복원
- `app/src/main/AndroidManifest.xml`: 접근성 서비스, 전체 설치 앱 조회, 알림 및 부팅 완료 권한 선언

향후 기능은 `feature/<기능명>` 패키지에 `domain`, `data`, `presentation`을 두고 홈 메뉴에 연결하면 됩니다. 기능이 추가되면 `StartDestination`에도 항목을 추가하여 시작 화면으로 선택할 수 있습니다.

## 기획 문서

- [Body Log 체중·식사 기록 기능명세서](docs/weight-tracker-spec.md)

현재 v0.6.0에는 Body Log의 날짜 지정 기록, 목표, 상호작용 가능한 기간별 그래프, 상시 달력, 식사·사진 목록, 마운자로 투여·부작용 기록과 사용자 설정 반복 알림이 구현되어 있습니다. 세부 표시 단위 설정과 Health Connect는 명세의 후속 단계로 남겨두었습니다.

## 빌드와 설치

1. Android Studio에서 이 폴더를 열고 SDK 35 및 JDK 17을 선택합니다.
2. 터미널에서 `./gradlew assembleDebug`를 실행합니다. (처음에는 Gradle 의존성 다운로드가 필요합니다.)
3. 생성 파일은 `app/build/outputs/apk/debug/app-debug.apk`입니다.
4. 휴대폰에서 개발자 옵션의 USB 디버깅을 켠 뒤 `adb install -r app/build/outputs/apk/debug/app-debug.apk`를 실행하거나, APK를 휴대폰에 옮겨 설치합니다. 출처를 알 수 없는 앱 설치 허용이 필요할 수 있습니다.
5. 앱의 설정 화면에서 **접근성 권한**을 눌러 “소림파워 앱 차단 서비스”를 활성화합니다. App Blocker를 켜고 앱을 선택하면 차단이 동작합니다.

## 유의 사항

`QUERY_ALL_PACKAGES` 권한은 설치된 앱 목록을 보여주기 위해 사용합니다. 본 프로젝트는 개인 설치용 APK이므로 Play Store의 해당 권한 정책 검토 대상이 아닙니다. 접근성 서비스는 앱 실행 감지 목적 외에는 사용하지 않도록 유지하세요.
