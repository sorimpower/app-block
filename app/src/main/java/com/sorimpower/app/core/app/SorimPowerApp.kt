package com.sorimpower.app.core.app

import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import com.sorimpower.app.BuildConfig
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sorimpower.app.R
import com.sorimpower.app.feature.blocker.data.BlockerState
import com.sorimpower.app.feature.blocker.data.BottomNavigationTab
import com.sorimpower.app.feature.blocker.data.StartDestination
import com.sorimpower.app.feature.blocker.domain.BlockSchedule
import com.sorimpower.app.feature.blocker.domain.RepeatCycle
import com.sorimpower.app.feature.blocker.domain.ScheduleAction
import com.sorimpower.app.feature.blocker.presentation.BlockerViewModel
import com.sorimpower.app.feature.blocker.presentation.InstalledApp
import com.sorimpower.app.feature.bodylog.presentation.BodyLogScreen
import com.sorimpower.app.feature.bodylog.presentation.BodyLogViewModel
import com.sorimpower.app.feature.auction.presentation.AuctionScreen
import com.sorimpower.app.feature.auction.presentation.AuctionViewModel
import com.sorimpower.app.feature.healthcheckup.presentation.HealthCheckupScreen
import com.sorimpower.app.feature.healthcheckup.presentation.HealthCheckupViewModel
import com.sorimpower.app.feature.phoneinsight.presentation.PhoneInsightScreen
import com.sorimpower.app.feature.phoneinsight.presentation.PhoneInsightViewModel
import com.sorimpower.app.feature.propertytax.presentation.PropertyTaxScreen
import com.sorimpower.app.feature.propertytax.presentation.PropertyTaxViewModel
import com.sorimpower.app.feature.perspective.presentation.PerspectiveScreen
import com.sorimpower.app.feature.perspective.presentation.PerspectiveViewModel
import com.sorimpower.app.core.ui.AppCobalt
import com.sorimpower.app.core.ui.AppLilac
import com.sorimpower.app.core.ui.AppNavy
import com.sorimpower.app.core.ui.AppOrange
import com.sorimpower.app.core.ui.SorimPowerTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

import com.sorimpower.app.feature.home.presentation.HomeScreen
import com.sorimpower.app.feature.blocker.presentation.AppRulesScreen
import com.sorimpower.app.feature.blocker.presentation.BlockerScreen
import com.sorimpower.app.feature.blocker.presentation.PasswordGateDialog
import com.sorimpower.app.feature.blocker.presentation.ScheduleScreen
import com.sorimpower.app.feature.settings.presentation.SettingsScreen

private enum class Screen(val label: String) {
    HOME("홈"), PERSPECTIVE("유튜브"), BLOCKER("차단"), BODY_LOG("건강"), AUCTION("경매"), PHONE_INSIGHT("알림"), PROPERTY_TAX("세금"), MORE("더보기"), SCHEDULE("조건"), APP_RULES("앱별 조건"), SETTINGS("설정")
}

private enum class HealthRecordTab(val label: String) { DAILY("데일리 기록"), CHECKUP("건강검진") }

/** Header help is intentionally kept in one place so every feature explains itself in the same language. */
private data class HeaderFeatureInfo(
    val title: String,
    val description: String,
    val features: List<String>,
    val ai: List<String>,
    val schedule: List<String>,
)

private fun headerFeatureInfo(screen: Screen): HeaderFeatureInfo? = when (screen) {
    Screen.PHONE_INSIGHT -> HeaderFeatureInfo(
        title = "AI 알림 안내",
        description = "휴대폰의 선택한 정보를 합쳐, 놓치기 쉬운 일정과 기한을 정리합니다.",
        features = listOf("문자·알림·사진·파일·통화 녹음·캘린더 등 선택한 데이터만 확인", "오늘·내일 일정과 2주 안의 기한을 카드와 푸시로 안내"),
        ai = listOf("GPT-5.6 Luna", "새로 들어온 후보 데이터를 묶어 중복을 제거하고 챙길 항목만 추출"),
        schedule = listOf("매일 오전 8시 자동 확인", "시간이 있는 일정은 시작 30분 전에 별도 알림"),
    )
    Screen.BODY_LOG -> HeaderFeatureInfo(
        title = "건강 기록 안내",
        description = "체중·식사·마운자로·운동·인바디 기록과 건강검진을 한 흐름으로 관리합니다.",
        features = listOf("체중 추이, 식사와 일별 추정 칼로리, 주사·운동 기록을 함께 확인", "인바디·검진 PDF에서 검사값을 추출하고 변화를 비교"),
        ai = listOf("식사별 칼로리·인바디 수치 추출: GPT-5.6 Luna", "건강 경과 분석: GPT-5.6 Terra · high 추론", "검진 문서 추출·추이·선택검사 추천: GPT-5.6 Luna"),
        schedule = listOf("식사를 저장하거나 수정하면 해당 식사를 바로 분석하고 하루 합계 갱신", "주사 알림을 켠 경우, 설정한 투여 주기에 맞춰 다음 알림 예약"),
    )
    Screen.AUCTION -> HeaderFeatureInfo(
        title = "부동산 경매 안내",
        description = "서울 아파트 진행 사건을 모아 보고, 법원 문서 기반의 예비 권리분석을 제공합니다.",
        features = listOf("서울·아파트·진행 중·감정가 15억 원 이상·매각기일 90일 이내 사건 수집", "관심·종료 사건을 별도로 보관하고 종료 결과를 다시 확인"),
        ai = listOf("수동 권리분석: GPT-5.6 Luna 또는 Terra 선택", "매일 추천 분석: GPT-5.6 Luna · 법원 문서와 사용자 조건을 함께 검토"),
        schedule = listOf("AI 추천을 켠 경우 설정한 오전 시간(6~10시)에 아직 분석하지 않은 사건을 분석", "추천 알림은 조건을 통과한 사건이 있을 때만 발송"),
    )
    Screen.PROPERTY_TAX -> HeaderFeatureInfo(
        title = "부동산 세금 안내",
        description = "보유 자산과 매도 계획을 타임라인으로 정리해 세금상 유의점을 비교합니다.",
        features = listOf("취득·보유·양도 흐름, 공동명의·분양권·재개발 상황을 시나리오로 비교", "이전 분석과 비교해 달라진 전제와 추가 확인 사항을 표시"),
        ai = listOf("GPT-5.6 Sol · max 추론", "분석 시 공식 법령·국세청 등 근거를 확인해 계획과 조건부 결과를 정리"),
        schedule = listOf("정해진 자동 분석은 없음", "새로 분석하기를 누를 때마다 최신 근거 확인과 이전 결과 비교 실행"),
    )
    Screen.BLOCKER -> HeaderFeatureInfo(
        title = "앱 차단 안내",
        description = "집중 시간에 지정한 앱을 차단하고, 조건별 예외와 일정을 관리합니다.",
        features = listOf("시간·요일·반복 조건과 앱별 규칙을 조합", "접근성 서비스를 통해 차단 화면을 즉시 적용"),
        ai = listOf("AI 분석을 사용하지 않음", "모든 차단 판단은 기기에 저장한 규칙으로 처리"),
        schedule = listOf("각 차단 조건에 설정한 시간과 반복 주기에 맞춰 동작", "사용자가 켠 조건만 적용"),
    )
    Screen.PERSPECTIVE -> HeaderFeatureInfo(
        title = "유튜브 분석 안내",
        description = "시청한 유튜브 영상을 주제와 관점으로 정리해, 다른 시각의 영상을 발견하도록 돕습니다.",
        features = listOf("5분 이상 시청한 YouTube 영상을 관심 지도에 반영", "뇌 지도에서 주제별 관점과 다음 질문 탐색"),
        ai = listOf("시청한 내용을 주제별로 정리", "다음에 확인할 수 있는 구체적인 질문과 관련 영상 추천"),
        schedule = listOf("고정 시각 스케줄 없음", "영상 재생 종료 직후 주제 또는 다른 관점 알림을 판단하며 하루 최대 2회 발송"),
    )
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
internal fun SorimPowerApp(
    viewModel: BlockerViewModel,
    bodyLogViewModel: BodyLogViewModel,
    auctionViewModel: AuctionViewModel,
    healthCheckupViewModel: HealthCheckupViewModel,
    phoneInsightViewModel: PhoneInsightViewModel,
    propertyTaxViewModel: PropertyTaxViewModel,
    perspectiveViewModel: PerspectiveViewModel,
    accessibilityEnabled: () -> Boolean,
    openAccessibilitySettings: () -> Unit,
    openAuctionAnalysesRequest: Int = 0,
    openPhoneInsightRequest: Int = 0,
    openPerspectiveRequest: Int = 0,
    openPerspectiveTopicsRequest: Int = 0,
    sharedYoutubeUrl: String? = null,
    onSharedYoutubeUrlConsumed: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val phoneLatestRun by phoneInsightViewModel.latestRun.collectAsStateWithLifecycle()
    // Compose 효과가 실행되기 전 첫 프레임부터 알림 목적지를 그린다.
    // 이렇게 해야 시작 화면 설정이 있는 경우에도 알림 대상이 잠깐 다른 화면으로
    // 보이거나 덮어써지지 않는다.
    var screen by remember {
        mutableStateOf(
            when {
                openPerspectiveTopicsRequest > 0 || openPerspectiveRequest > 0 -> Screen.PERSPECTIVE
                openAuctionAnalysesRequest > 0 -> Screen.AUCTION
                openPhoneInsightRequest > 0 -> Screen.PHONE_INSIGHT
                else -> Screen.HOME
            },
        )
    }
    var editingSchedule by remember { mutableStateOf<BlockSchedule?>(null) }
    var selectedApp by remember { mutableStateOf<InstalledApp?>(null) }
    var healthRecordTab by remember { mutableStateOf(HealthRecordTab.DAILY) }
    var protectedAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var infoScreen by remember { mutableStateOf<Screen?>(null) }
    // 알림으로 진입한 화면은 사용자가 설정한 시작 화면보다 항상 우선한다.
    // AI 챙김을 시작 화면으로 설정한 경우 관점 확장 알림이 로딩 뒤 AI 챙김으로
    // 다시 덮어써지던 경쟁 상태를 막는다.
    val hasNotificationDeepLink = openAuctionAnalysesRequest > 0 ||
        openPhoneInsightRequest > 0 ||
        openPerspectiveRequest > 0 ||
        openPerspectiveTopicsRequest > 0

    LaunchedEffect(state.loaded, state.startDestination, hasNotificationDeepLink) {
        if (state.loaded && !hasNotificationDeepLink) {
            screen = when (state.startDestination) {
                StartDestination.HOME -> Screen.HOME
                StartDestination.APP_BLOCKER -> Screen.BLOCKER
                StartDestination.BODY_LOG -> Screen.BODY_LOG
                StartDestination.REAL_ESTATE_AUCTION -> Screen.AUCTION
                StartDestination.PHONE_INSIGHT -> Screen.PHONE_INSIGHT
                StartDestination.PROPERTY_TAX -> Screen.PROPERTY_TAX
                StartDestination.PERSPECTIVE -> Screen.PERSPECTIVE
                StartDestination.MORE -> Screen.MORE
            }
        }
    }

    LaunchedEffect(openAuctionAnalysesRequest) {
        if (openAuctionAnalysesRequest > 0) {
            screen = Screen.AUCTION
            auctionViewModel.showAiAnalyses()
        }
    }
    LaunchedEffect(openPhoneInsightRequest) { if (openPhoneInsightRequest > 0) screen = Screen.PHONE_INSIGHT }
    LaunchedEffect(openPerspectiveRequest) { if (openPerspectiveRequest > 0) screen = Screen.PERSPECTIVE }
    LaunchedEffect(openPerspectiveTopicsRequest) { if (openPerspectiveTopicsRequest > 0) screen = Screen.PERSPECTIVE }

    BackHandler(enabled = infoScreen != null) { infoScreen = null }
    BackHandler(enabled = infoScreen == null && protectedAction == null && screen != Screen.HOME) {
        screen = when (screen) {
            Screen.SCHEDULE, Screen.APP_RULES -> Screen.BLOCKER
            Screen.SETTINGS -> Screen.MORE
            Screen.BODY_LOG -> if (healthRecordTab == HealthRecordTab.CHECKUP) {
                healthRecordTab = HealthRecordTab.DAILY
                Screen.BODY_LOG
            } else Screen.HOME
            else -> Screen.HOME
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                shadowElevation = 5.dp,
                color = MaterialTheme.colorScheme.surface,
            ) {
            Box(
                Modifier.background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = .72f),
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .35f),
                        ),
                    ),
                ),
            ) {
            TopAppBar(
                navigationIcon = {
                    Image(
                        painter = painterResource(R.drawable.najalal_logo),
                        contentDescription = "나잘알 로고",
                        modifier = Modifier
                            .padding(start = 18.dp, end = 10.dp)
                            .size(42.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                },
                title = {
                    Column {
                        Text(when (screen) {
                            Screen.HOME -> "나잘알"
                            Screen.BLOCKER -> "앱 차단"
                            Screen.BODY_LOG -> "건강"
                            Screen.AUCTION -> "부동산 경매"
                            Screen.MORE -> "더보기"
                            Screen.PHONE_INSIGHT -> "AI 알림"
                            Screen.PROPERTY_TAX -> "부동산 세금"
                            Screen.PERSPECTIVE -> "유튜브 분석"
                            Screen.SCHEDULE -> "조건 편집"
                            Screen.APP_RULES -> "앱별 조건"
                            Screen.SETTINGS -> "설정"
                        }, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                        if (screen == Screen.HOME) Text("오늘의 개인 대시보드", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    if (screen == Screen.HOME) {
                        Surface(
                            modifier = Modifier.padding(end = 18.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = .72f),
                        ) {
                            Text(
                                LocalDate.now().format(DateTimeFormatter.ofPattern("M.d E", Locale.KOREAN)),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    headerFeatureInfo(screen)?.let { info ->
                        HeaderInfoButton(
                            contentDescription = "${info.title} 열기",
                            onClick = { infoScreen = screen },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
            }
            }
        },
        bottomBar = {
            if (screen != Screen.SCHEDULE && screen != Screen.APP_RULES) FloatingNavigation(screen, state.bottomNavigationOrder) { screen = it }
        },
    ) { padding ->
        val navigationScreens = state.bottomNavigationOrder.map(BottomNavigationTab::screen)
        Box(
                Modifier.fillMaxSize().horizontalSwipe(
                onSwipeLeft = {
                    if (screen != Screen.AUCTION && screen != Screen.PHONE_INSIGHT && screen != Screen.BODY_LOG && screen != Screen.PROPERTY_TAX && screen != Screen.PERSPECTIVE) {
                        val tabs = state.bottomNavigationOrder.map(BottomNavigationTab::screen)
                        val index = tabs.indexOf(screen)
                        if (index >= 0 && index < tabs.lastIndex) screen = tabs[index + 1]
                    }
                },
                onSwipeRight = {
                    if (screen != Screen.AUCTION && screen != Screen.PHONE_INSIGHT && screen != Screen.BODY_LOG && screen != Screen.PROPERTY_TAX && screen != Screen.PERSPECTIVE) {
                        val tabs = state.bottomNavigationOrder.map(BottomNavigationTab::screen)
                        val index = tabs.indexOf(screen)
                        if (index > 0) screen = tabs[index - 1]
                    }
                },
            )
        ) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                val forward = navigationScreens.indexOf(targetState) >= navigationScreens.indexOf(initialState)
                if (forward) {
                    (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut())
                } else {
                    (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
                }
            },
            label = "main_screen_transition",
        ) { currentScreen ->
        when (currentScreen) {
            Screen.HOME -> HomeScreen(
                padding,
                state,
                accessibilityEnabled(),
                { screen = Screen.BLOCKER },
                { screen = Screen.BODY_LOG },
                { screen = Screen.AUCTION },
                { screen = Screen.PHONE_INSIGHT },
                { screen = Screen.PROPERTY_TAX },
                { screen = Screen.PERSPECTIVE },
                openAccessibilitySettings,
            )
            Screen.BODY_LOG -> Column(Modifier.fillMaxSize().padding(padding)) {
                HealthRecordTabs(
                    selected = healthRecordTab,
                    onSelected = { healthRecordTab = it },
                )
                Box(Modifier.fillMaxWidth().weight(1f).horizontalSwipe(
                    onSwipeLeft = { if (healthRecordTab == HealthRecordTab.DAILY) healthRecordTab = HealthRecordTab.CHECKUP else moveToAdjacentScreen(state, screen, 1) { screen = it } },
                    onSwipeRight = { if (healthRecordTab == HealthRecordTab.CHECKUP) healthRecordTab = HealthRecordTab.DAILY else moveToAdjacentScreen(state, screen, -1) { screen = it } },
                )) {
                    when (healthRecordTab) {
                        HealthRecordTab.DAILY -> BodyLogScreen(PaddingValues(0.dp), bodyLogViewModel)
                        HealthRecordTab.CHECKUP -> HealthCheckupScreen(PaddingValues(0.dp), healthCheckupViewModel)
                    }
                }
            }
            Screen.AUCTION -> AuctionScreen(padding, auctionViewModel, onSwipeEdgeLeft = { moveToAdjacentScreen(state, screen, 1) { screen = it } }, onSwipeEdgeRight = { moveToAdjacentScreen(state, screen, -1) { screen = it } })
            Screen.PHONE_INSIGHT -> PhoneInsightScreen(padding, phoneInsightViewModel, onSwipeEdgeLeft = { moveToAdjacentScreen(state, screen, 1) { screen = it } }, onSwipeEdgeRight = { moveToAdjacentScreen(state, screen, -1) { screen = it } })
            Screen.PROPERTY_TAX -> PropertyTaxScreen(padding, propertyTaxViewModel, onSwipeEdgeLeft = { moveToAdjacentScreen(state, screen, 1) { screen = it } }, onSwipeEdgeRight = { moveToAdjacentScreen(state, screen, -1) { screen = it } })
            Screen.PERSPECTIVE -> PerspectiveScreen(
                padding = padding,
                viewModel = perspectiveViewModel,
                openExploreRequest = openPerspectiveRequest,
                openTopicsRequest = openPerspectiveTopicsRequest,
                sharedYoutubeUrl = sharedYoutubeUrl,
                onSharedYoutubeUrlConsumed = onSharedYoutubeUrlConsumed,
                onSwipeEdgeLeft = { moveToAdjacentScreen(state, screen, 1) { screen = it } },
                onSwipeEdgeRight = { moveToAdjacentScreen(state, screen, -1) { screen = it } },
            )
            Screen.MORE -> MoreMenuScreen(padding, onOpenSettings = { screen = Screen.SETTINGS })
            Screen.BLOCKER -> BlockerScreen(
                padding,
                viewModel,
                state,
                onEditSchedule = {
                    editingSchedule = it
                    screen = Screen.SCHEDULE
                },
                onAddSchedule = {
                    val schedule = BlockSchedule(name = "새 차단 조건")
                    viewModel.upsertSchedule(schedule)
                    editingSchedule = schedule
                    screen = Screen.SCHEDULE
                },
                requestProtectedAction = { protectedAction = it },
                onOpenApp = {
                    selectedApp = it
                    screen = Screen.APP_RULES
                },
            )
            Screen.SCHEDULE -> editingSchedule?.let { initial ->
                val latest = state.schedules.firstOrNull { it.id == initial.id } ?: initial
                ScheduleScreen(
                    padding = padding,
                    initial = latest,
                    onSave = viewModel::upsertSchedule,
                    requestProtectedAction = { protectedAction = it },
                    onBack = { screen = Screen.BLOCKER },
                )
            }
            Screen.APP_RULES -> selectedApp?.let { app ->
                AppRulesScreen(
                    padding = padding,
                    app = app,
                    state = state,
                    viewModel = viewModel,
                    requestProtectedAction = { protectedAction = it },
                    onBack = { screen = Screen.BLOCKER },
                )
            }
            Screen.SETTINGS -> SettingsScreen(
                padding,
                state,
                viewModel,
                accessibilityEnabled(),
                openAccessibilitySettings,
                requestProtectedAction = { protectedAction = it },
            )
        }
        }
        }
    }
    infoScreen?.let { selectedScreen ->
        headerFeatureInfo(selectedScreen)?.let { info ->
            HeaderFeatureInfoDialog(
                info = info,
                lastAnalysisUpdate = if (selectedScreen == Screen.PHONE_INSIGHT) {
                    phoneLatestRun?.let { latest ->
                        val timestamp = latest.finishedAt ?: latest.startedAt
                        "최근 분석 갱신: ${formatPhoneInsightTime(timestamp)}"
                    } ?: "최근 분석 갱신: 아직 분석 기록이 없습니다."
                } else null,
                onDismiss = { infoScreen = null },
            )
        }
    }
    protectedAction?.let { action ->
        PasswordGateDialog(
            hasPassword = state.hasPassword,
            onVerify = viewModel::verifyPassword,
            onDismiss = { protectedAction = null },
            onPasswordRequired = {
                protectedAction = null
                screen = Screen.SETTINGS
            },
            onSuccess = {
                protectedAction = null
                action()
            },
        )
    }
}

private fun formatPhoneInsightTime(timestamp: Long): String =
    java.time.Instant.ofEpochMilli(timestamp).atZone(java.time.ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("M월 d일 HH:mm"))

@Composable
private fun HeaderInfoButton(
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(end = 14.dp)
            .size(34.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Info,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
private fun HeaderFeatureInfoDialog(
    info: HeaderFeatureInfo,
    lastAnalysisUpdate: String?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = RoundedCornerShape(11.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .8f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
                    }
                }
                Text(
                    info.title,
                    modifier = Modifier.padding(start = 10.dp),
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 470.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        info.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item { HeaderInfoSection("기능 특징", info.features, MaterialTheme.colorScheme.primary) }
                item { HeaderInfoSection("AI 분석", info.ai, MaterialTheme.colorScheme.secondary) }
                item {
                    HeaderInfoSection(
                        title = "자동 실행 · 알림",
                        items = buildList {
                            addAll(info.schedule)
                            lastAnalysisUpdate?.let(::add)
                        },
                        accent = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("확인") }
        },
    )
}

@Composable
private fun HeaderInfoSection(
    title: String,
    items: List<String>,
    accent: Color,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = accent.copy(alpha = .075f),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Text(title, color = accent, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
            items.forEach { item ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.padding(top = 7.dp).size(5.dp).background(accent, CircleShape),
                    )
                    Text(
                        item,
                        modifier = Modifier.padding(start = 8.dp).weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun Modifier.horizontalSwipe(onSwipeLeft:()->Unit,onSwipeRight:()->Unit):Modifier=pointerInput(Unit){awaitPointerEventScope{while(true){val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial);var dx=0f;var dy=0f;while(true){val event=awaitPointerEvent(PointerEventPass.Initial);val change=event.changes.firstOrNull{it.id==down.id}?:break;if(!change.pressed){if(abs(dx)>100f&&abs(dx)>abs(dy)*1.2f){if(dx<0)onSwipeLeft() else onSwipeRight()};break};val delta=change.positionChange();dx+=delta.x;dy+=delta.y}}}}

private fun moveToAdjacentScreen(state: BlockerState, current: Screen, offset: Int, onChange: (Screen) -> Unit) {
    val tabs = state.bottomNavigationOrder.map(BottomNavigationTab::screen)
    val index = tabs.indexOf(current)
    val target = index + offset
    if (index >= 0 && target in tabs.indices) onChange(tabs[target])
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HealthRecordTabs(
    selected: HealthRecordTab,
    onSelected: (HealthRecordTab) -> Unit,
) {
    val selectedIndex = HealthRecordTab.entries.indexOf(selected)
    PrimaryTabRow(selectedTabIndex = selectedIndex) {
        HealthRecordTab.entries.forEachIndexed { index, tab ->
            Tab(
                selected = index == selectedIndex,
                onClick = { onSelected(tab) },
                text = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
            )
        }
    }
}

@Composable
private fun FloatingNavigation(selected: Screen, order: List<BottomNavigationTab>, onSelected: (Screen) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(70.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            order.map(BottomNavigationTab::screen).forEach { item ->
                val active = selected == item || (item == Screen.MORE && selected == Screen.SETTINGS)
                Column(
                    Modifier.weight(1f).clickable { onSelected(item) }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    NavIcon(item, active)
                    Text(
                        item.label,
                        modifier = Modifier.padding(top = 3.dp),
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun BottomNavigationTab.screen() = when (this) {
    BottomNavigationTab.HOME -> Screen.HOME
    BottomNavigationTab.PHONE_INSIGHT -> Screen.PHONE_INSIGHT
    BottomNavigationTab.BLOCKER -> Screen.BLOCKER
    BottomNavigationTab.BODY_LOG -> Screen.BODY_LOG
    BottomNavigationTab.AUCTION -> Screen.AUCTION
    BottomNavigationTab.PROPERTY_TAX -> Screen.PROPERTY_TAX
    BottomNavigationTab.PERSPECTIVE -> Screen.PERSPECTIVE
    BottomNavigationTab.MORE -> Screen.MORE
}

@Composable
private fun MoreMenuScreen(padding: PaddingValues, onOpenSettings: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                Modifier.fillMaxWidth().clickable(onClick = onOpenSettings),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(1.dp),
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Settings, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("설정", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        Text("시작 화면, 접근성 권한, 비밀번호를 관리하세요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(1.dp),
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).background(MaterialTheme.colorScheme.tertiary.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.tertiary)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("앱 정보", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        Text("나잘알 v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("나잘알 개인용 시스템", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun NavIcon(screen: Screen, selected: Boolean) {
    val icon = when (screen) {
        Screen.HOME -> Icons.Rounded.Home
        Screen.BLOCKER -> Icons.Rounded.Block
        Screen.BODY_LOG -> Icons.Rounded.FavoriteBorder
        Screen.AUCTION -> Icons.Rounded.Gavel
        Screen.PHONE_INSIGHT -> Icons.Rounded.NotificationsNone
        Screen.PROPERTY_TAX -> Icons.Rounded.AccountBalance
        Screen.PERSPECTIVE -> Icons.Rounded.Psychology
        Screen.MORE -> Icons.Rounded.MoreHoriz
        else -> Icons.Rounded.Settings
    }
    Box(
        Modifier.width(50.dp).height(30.dp).clip(RoundedCornerShape(15.dp))
            .background(if (selected) Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = screen.label, modifier = Modifier.size(20.dp), tint = if (selected) Color.White else Color(0xFFAAA7AE)) }
}
