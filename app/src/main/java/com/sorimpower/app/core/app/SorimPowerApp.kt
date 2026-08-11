package com.sorimpower.app.core.app

import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.sorimpower.app.feature.auction.presentation.AuctionCollectionInfoDialog
import com.sorimpower.app.feature.auction.presentation.AuctionViewModel
import com.sorimpower.app.feature.healthcheckup.presentation.HealthCheckupScreen
import com.sorimpower.app.feature.healthcheckup.presentation.HealthCheckupViewModel
import com.sorimpower.app.feature.phoneinsight.presentation.PhoneInsightScreen
import com.sorimpower.app.feature.phoneinsight.presentation.PhoneInsightViewModel
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
    HOME("홈"), BLOCKER("차단"), BODY_LOG("기록"), AUCTION("경매"), PHONE_INSIGHT("챙김"), MORE("더보기"), SCHEDULE("조건"), APP_RULES("앱별 조건"), SETTINGS("설정")
}

private enum class HealthRecordTab(val label: String) { DAILY("데일리 기록"), CHECKUP("건강검진") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SorimPowerApp(
    viewModel: BlockerViewModel,
    bodyLogViewModel: BodyLogViewModel,
    auctionViewModel: AuctionViewModel,
    healthCheckupViewModel: HealthCheckupViewModel,
    phoneInsightViewModel: PhoneInsightViewModel,
    accessibilityEnabled: () -> Boolean,
    openAccessibilitySettings: () -> Unit,
    openAuctionAnalysesRequest: Int = 0,
    openPhoneInsightRequest: Int = 0,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf(Screen.HOME) }
    var editingSchedule by remember { mutableStateOf<BlockSchedule?>(null) }
    var selectedApp by remember { mutableStateOf<InstalledApp?>(null) }
    var healthRecordTab by remember { mutableStateOf(HealthRecordTab.DAILY) }
    var protectedAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showAuctionCollectionInfo by remember { mutableStateOf(false) }
    var showPhoneInsightScheduleInfo by remember { mutableStateOf(false) }

    LaunchedEffect(state.loaded, state.startDestination) {
        if (state.loaded) {
            screen = when (state.startDestination) {
                StartDestination.HOME -> Screen.HOME
                StartDestination.APP_BLOCKER -> Screen.BLOCKER
                StartDestination.BODY_LOG -> Screen.BODY_LOG
                StartDestination.REAL_ESTATE_AUCTION -> Screen.AUCTION
                StartDestination.PHONE_INSIGHT -> Screen.PHONE_INSIGHT
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    Box(
                        Modifier.padding(start = 18.dp, end = 10.dp).size(42.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(AppCobalt, AppOrange))),
                        contentAlignment = Alignment.Center,
                    ) { Text("S", color = Color.White, fontWeight = FontWeight.Black) }
                },
                title = {
                    Column {
                        Text(when (screen) {
                            Screen.HOME -> "소림파워 전용"
                            Screen.BLOCKER -> "앱 차단"
                            Screen.BODY_LOG -> "건강 기록"
                            Screen.AUCTION -> "부동산 경매"
                            Screen.MORE -> "더보기"
                            Screen.PHONE_INSIGHT -> "AI 챙김"
                            Screen.SCHEDULE -> "조건 편집"
                            Screen.APP_RULES -> "앱별 조건"
                            Screen.SETTINGS -> "설정"
                        }, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                        if (screen == Screen.HOME) Text(
                            LocalDate.now().format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (screen == Screen.AUCTION) {
                        IconButton(onClick = { showAuctionCollectionInfo = true }) {
                            Icon(Icons.Rounded.Info, contentDescription = "법원 경매 수집 조건")
                        }
                    }
                    if (screen == Screen.PHONE_INSIGHT) {
                        IconButton(onClick = { showPhoneInsightScheduleInfo = true }) {
                            Icon(Icons.Rounded.Info, contentDescription = "AI 챙김 자동 확인 안내")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                ),
            )
        },
        bottomBar = {
            if (screen != Screen.SCHEDULE && screen != Screen.APP_RULES) FloatingNavigation(screen, state.bottomNavigationOrder) { screen = it }
        },
    ) { padding ->
        when (screen) {
            Screen.HOME -> HomeScreen(
                padding,
                state,
                accessibilityEnabled(),
                { screen = Screen.BLOCKER },
                { screen = Screen.BODY_LOG },
                { screen = Screen.AUCTION },
                { screen = Screen.PHONE_INSIGHT },
                openAccessibilitySettings,
            )
            Screen.BODY_LOG -> Column(Modifier.fillMaxSize().padding(padding)) {
                HealthRecordTabs(
                    selected = healthRecordTab,
                    onSelected = { healthRecordTab = it },
                )
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    when (healthRecordTab) {
                        HealthRecordTab.DAILY -> BodyLogScreen(PaddingValues(0.dp), bodyLogViewModel)
                        HealthRecordTab.CHECKUP -> HealthCheckupScreen(PaddingValues(0.dp), healthCheckupViewModel)
                    }
                }
            }
            Screen.AUCTION -> AuctionScreen(padding, auctionViewModel)
            Screen.PHONE_INSIGHT -> PhoneInsightScreen(padding, phoneInsightViewModel)
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
    if (showAuctionCollectionInfo) {
        AuctionCollectionInfoDialog(onDismiss = { showAuctionCollectionInfo = false })
    }
    if (showPhoneInsightScheduleInfo) {
        AlertDialog(
            onDismissRequest = { showPhoneInsightScheduleInfo = false },
            title = { Text("AI 챙김 자동 확인", fontWeight = FontWeight.Black) },
            text = { Text("매일 오전 8시 · 오후 7시 새로운 일정 확인") },
            confirmButton = { TextButton(onClick = { showPhoneInsightScheduleInfo = false }) { Text("확인") } },
        )
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
    Surface(color = Color.White, shadowElevation = 12.dp) {
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
                        color = if (active) AppCobalt else Color(0xFFA9A6AD),
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
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(1.dp),
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).background(AppCobalt.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Settings, null, tint = AppCobalt)
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
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(1.dp),
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).background(AppOrange.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Info, null, tint = AppOrange)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("앱 정보", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        Text("소림파워 v0.6.2", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = AppNavy)
                        Text("집중을 위한 개인용 시스템", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Screen.MORE -> Icons.Rounded.MoreHoriz
        else -> Icons.Rounded.Settings
    }
    Box(
        Modifier.width(50.dp).height(30.dp).clip(RoundedCornerShape(15.dp))
            .background(if (selected) Brush.horizontalGradient(listOf(AppCobalt, AppOrange)) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = screen.label, modifier = Modifier.size(20.dp), tint = if (selected) Color.White else Color(0xFFAAA7AE)) }
}
