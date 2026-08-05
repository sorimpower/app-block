package com.sorimpower.app

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sorimpower.app.data.BlockerState
import com.sorimpower.app.data.StartDestination
import com.sorimpower.app.feature.blocker.domain.BlockSchedule
import com.sorimpower.app.feature.blocker.domain.RepeatCycle
import com.sorimpower.app.feature.blocker.domain.ScheduleAction
import com.sorimpower.app.feature.blocker.presentation.BlockerViewModel
import com.sorimpower.app.feature.blocker.presentation.InstalledApp
import com.sorimpower.app.ui.AppCobalt
import com.sorimpower.app.ui.AppLilac
import com.sorimpower.app.ui.AppNavy
import com.sorimpower.app.ui.AppOrange
import com.sorimpower.app.ui.SorimPowerTheme
import java.time.DayOfWeek

class MainActivity : ComponentActivity() {
    private val viewModel: BlockerViewModel by viewModels()

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SorimPowerTheme {
                SorimPowerApp(viewModel, ::isAccessibilityServiceEnabled, ::openAccessibilitySettings)
            }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabled?.let {
            TextUtils.SimpleStringSplitter(':').apply { setString(it) }.any { name ->
                ComponentName(this, com.sorimpower.app.feature.blocker.service.AppBlockAccessibilityService::class.java)
                    .flattenToString() == name
            }
        } == true
    }

    private fun openAccessibilitySettings() = startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}

private enum class Screen(val label: String) {
    HOME("홈"), BLOCKER("차단"), SCHEDULE("조건"), APP_RULES("앱별 조건"), SETTINGS("설정")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SorimPowerApp(
    viewModel: BlockerViewModel,
    accessibilityEnabled: () -> Boolean,
    openAccessibilitySettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf(Screen.HOME) }
    var editingSchedule by remember { mutableStateOf<BlockSchedule?>(null) }
    var selectedApp by remember { mutableStateOf<InstalledApp?>(null) }
    var protectedAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var initialDestinationApplied by remember { mutableStateOf(false) }

    LaunchedEffect(state.loaded, state.startDestination) {
        if (state.loaded && !initialDestinationApplied) {
            screen = if (state.startDestination == StartDestination.APP_BLOCKER) Screen.BLOCKER else Screen.HOME
            initialDestinationApplied = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (screen) {
                            Screen.HOME -> "SORIM POWER"
                            Screen.BLOCKER -> "App Blocker"
                            Screen.SCHEDULE -> "조건 편집"
                            Screen.APP_RULES -> "앱별 조건"
                            Screen.SETTINGS -> "설정"
                        },
                        fontWeight = FontWeight.Black,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = .9f),
                ),
            )
        },
        bottomBar = {
            if (screen != Screen.SCHEDULE && screen != Screen.APP_RULES) FloatingNavigation(screen) { screen = it }
        },
    ) { padding ->
        when (screen) {
            Screen.HOME -> HomeScreen(
                padding,
                state,
                accessibilityEnabled(),
                { screen = Screen.BLOCKER },
                openAccessibilitySettings,
            )
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

@Composable
private fun FloatingNavigation(selected: Screen, onSelected: (Screen) -> Unit) {
    Box(
        Modifier.fillMaxWidth().background(Color.Transparent).padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)),
            color = MaterialTheme.colorScheme.surface.copy(alpha = .94f),
            shadowElevation = 12.dp,
            tonalElevation = 3.dp,
        ) {
            NavigationBar(containerColor = Color.Transparent) {
                listOf(Screen.HOME, Screen.BLOCKER, Screen.SETTINGS).forEach { item ->
                    NavigationBarItem(
                        selected = selected == item,
                        onClick = { onSelected(item) },
                        icon = { NavOrb(selected == item) },
                        label = { Text(item.label, fontWeight = if (selected == item) FontWeight.Bold else FontWeight.Normal) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NavOrb(selected: Boolean) {
    val size by animateDpAsState(if (selected) 14.dp else 7.dp, label = "nav-size")
    val color by animateColorAsState(if (selected) AppCobalt else MaterialTheme.colorScheme.outline, label = "nav-color")
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

@Composable
private fun HomeScreen(
    padding: PaddingValues,
    state: BlockerState,
    serviceEnabled: Boolean,
    openBlocker: () -> Unit,
    openAccessibilitySettings: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp))
                    .background(Brush.linearGradient(listOf(AppNavy, Color(0xFF283C8D), AppCobalt)))
                    .padding(26.dp),
            ) {
                Text("FOCUS SYSTEM", color = AppOrange, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(12.dp))
                Text(
                    if (state.enabled) "집중할 준비는\n이미 끝났어요." else "주의력을 다시\n내 것으로.",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = openBlocker) { Text(if (state.enabled) "차단 설정 보기" else "집중 모드 시작") }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("${state.blockedPackages.size}", "차단 앱", Modifier.weight(1f), AppOrange)
                MetricCard("${state.activeScheduleCount}", "활성 조건", Modifier.weight(1f), AppLilac)
            }
        }
        if (!serviceEnabled) {
            item {
                Card(
                    Modifier.fillMaxWidth().clickable(onClick = openAccessibilitySettings),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("권한 연결 필요", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        Text("접근성 서비스를 켜야 차단이 시작됩니다.")
                    }
                }
            }
        }
        item { SectionTitle("내 기능", "자주 쓰는 기능을 크게 배치했어요") }
        item {
            Card(
                Modifier.fillMaxWidth().clickable(onClick = openBlocker),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(4.dp),
            ) {
                Row(Modifier.padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(58.dp).clip(RoundedCornerShape(20.dp)).background(AppCobalt),
                        contentAlignment = Alignment.Center,
                    ) { Text("01", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) }
                    Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                        Text("App Blocker", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                        Text(if (state.enabled) "집중 모드 실행 중" else "나만의 사용 규칙 만들기", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("›", style = MaterialTheme.typography.headlineMedium, color = AppCobalt)
                }
            }
        }
    }
}

@Composable
private fun MetricCard(value: String, label: String, modifier: Modifier, accent: Color) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
            Text(value, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BlockerScreen(
    padding: PaddingValues,
    viewModel: BlockerViewModel,
    state: BlockerState,
    onEditSchedule: (BlockSchedule) -> Unit,
    onAddSchedule: () -> Unit,
    requestProtectedAction: (() -> Unit) -> Unit,
    onOpenApp: (InstalledApp) -> Unit,
) {
    var message by remember(state.blockMessage) { mutableStateOf(state.blockMessage) }
    var query by remember { mutableStateOf("") }
    val filteredApps = remember(query, viewModel.apps, state.blockedPackages) {
        val keyword = query.trim().lowercase()
        val matches = if (keyword.isBlank()) viewModel.apps else viewModel.apps.filter {
            it.label.lowercase().contains(keyword) || it.packageName.lowercase().contains(keyword)
        }
        matches.sortedWith(
            compareByDescending<InstalledApp> { it.packageName in state.blockedPackages }
                .thenBy { it.label.lowercase() },
        )
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = AppNavy), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("MASTER SWITCH", color = AppOrange, fontWeight = FontWeight.Black)
                        Text(
                            if (state.enabled) "차단 실행 중" else "차단 대기 중",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                        )
                        Text("앱 ${state.blockedPackages.size}개 · 조건 ${state.activeScheduleCount}개", color = Color.White.copy(alpha = .7f))
                    }
                    Switch(
                        checked = state.enabled,
                        onCheckedChange = { enabled ->
                            requestProtectedAction { viewModel.setEnabled(enabled) }
                        },
                    )
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(24.dp),
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    FeatureSectionTitle("01", "차단 조건", "조건은 앱별 화면에서 적용하거나 해제해요", Modifier.weight(1f))
                    Button(onClick = onAddSchedule) { Text("+ 추가") }
                }
            }
        }
        if (state.schedules.isEmpty()) {
            item { EmptyCard("아직 조건이 없어요. 조건을 추가해야 차단됩니다.") }
        } else {
            items(state.schedules, key = BlockSchedule::id) { schedule ->
                ScheduleCard(
                    schedule = schedule,
                    onClick = { onEditSchedule(schedule) },
                    onDelete = { requestProtectedAction { viewModel.deleteSchedule(schedule.id) } },
                )
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(Modifier.padding(20.dp)) {
                    FeatureSectionTitle("02", "차단 메시지", "차단 화면에서 가장 크게 보여줄 중요한 문장")
                    OutlinedTextField(
                        value = message,
                        onValueChange = { message = it.take(120) },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        minLines = 3,
                        supportingText = { Text("${message.length}/120") },
                    )
                    Button(
                        onClick = { viewModel.setBlockMessage(message) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = message.isNotBlank() && message.trim() != state.blockMessage,
                    ) { Text("메시지 저장") }
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(Modifier.padding(20.dp)) {
                    FeatureSectionTitle("03", "차단할 앱", "앱을 켠 뒤, 탭해서 적용할 조건을 선택하세요")
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        label = { Text("앱 검색") },
                        placeholder = { Text("예: YouTube") },
                        singleLine = true,
                    )
                    Text("검색 결과 ${filteredApps.size}개", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        items(filteredApps, key = { it.packageName }) { app ->
            Card(
                Modifier.fillMaxWidth().clickable { onOpenApp(app) },
                colors = CardDefaults.cardColors(
                    containerColor = if (app.packageName in state.blockedPackages) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                ),
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(app.label, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (app.packageName in state.blockedPackages) {
                                "적용 조건 ${state.appScheduleIds[app.packageName].orEmpty().size}개 · 탭하여 설정"
                            } else {
                                app.packageName
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    Switch(
                        checked = app.packageName in state.blockedPackages,
                        onCheckedChange = { blocked ->
                            requestProtectedAction { viewModel.setBlocked(app.packageName, blocked) }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleCard(
    schedule: BlockSchedule,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(schedule.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Text(
                        if (schedule.action == ScheduleAction.BLOCK) "차단 실행 조건" else "차단 해제 조건",
                        color = if (schedule.action == ScheduleAction.BLOCK) AppCobalt else MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(scheduleSummary(schedule), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = onDelete) { Text("삭제") }
                Spacer(Modifier.size(8.dp))
                Button(onClick = onClick) { Text("편집") }
            }
        }
    }
}

@Composable
private fun FeatureSectionTitle(
    number: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(AppNavy),
            contentAlignment = Alignment.Center,
        ) { Text(number, color = Color.White, fontWeight = FontWeight.Black) }
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ScheduleScreen(
    padding: PaddingValues,
    initial: BlockSchedule,
    onSave: (BlockSchedule) -> Unit,
    requestProtectedAction: (() -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var draft by remember(initial.id) { mutableStateOf(initial) }
    fun update(transform: (BlockSchedule) -> BlockSchedule) {
        val updated = transform(draft)
        draft = updated
        onSave(updated)
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { OutlinedButton(onClick = onBack) { Text("‹ 조건 목록") } }
        item {
            OutlinedTextField(
                value = draft.name,
                onValueChange = { update { current -> current.copy(name = it.take(30)) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("조건 이름") },
                singleLine = true,
            )
        }
        item {
            SectionTitle("조건 동작", "해제 조건은 같은 시간의 차단 조건보다 우선합니다")
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScheduleAction.entries.forEach { action ->
                    FilterChip(
                        selected = draft.action == action,
                        onClick = {
                            if (draft.action != action) {
                                requestProtectedAction { update { it.copy(action = action) } }
                            }
                        },
                        label = { Text(if (action == ScheduleAction.BLOCK) "차단 실행" else "차단 해제") },
                    )
                }
            }
        }
        item {
            SectionTitle("요일", "이 조건을 적용할 요일")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = 8.dp)) {
                items(DayOfWeek.entries) { day ->
                    FilterChip(
                        selected = day in draft.weekdays,
                        onClick = {
                            update { current ->
                                val days = current.weekdays.toMutableSet().apply {
                                    if (day in this && size > 1) remove(day) else add(day)
                                }
                                current.copy(weekdays = days)
                            }
                        },
                        label = { Text(dayLabel(day)) },
                    )
                }
            }
        }
        item { HorizontalDivider() }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("시간", "자정을 넘기는 범위도 지원", Modifier.weight(1f))
                Switch(checked = draft.timeEnabled, onCheckedChange = { value -> update { it.copy(timeEnabled = value) } })
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TimeButton("시작", draft.startMinute, Modifier.weight(1f)) {
                    showTimePicker(context, draft.startMinute) { minute -> update { it.copy(startMinute = minute) } }
                }
                TimeButton("종료", draft.endMinute, Modifier.weight(1f)) {
                    showTimePicker(context, draft.endMinute) { minute -> update { it.copy(endMinute = minute) } }
                }
            }
        }
        item { HorizontalDivider() }
        item {
            SectionTitle("반복 주기", "오늘을 기준으로 반복")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = 8.dp)) {
                items(RepeatCycle.entries) { cycle ->
                    FilterChip(
                        selected = draft.repeatCycle == cycle,
                        onClick = { update { it.copy(repeatCycle = cycle, anchorEpochDay = java.time.LocalDate.now().toEpochDay()) } },
                        label = { Text(repeatLabel(cycle)) },
                    )
                }
            }
        }
        item { HorizontalDivider() }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("매월 특정 날짜", "선택 날짜에만 조건 적용", Modifier.weight(1f))
                Switch(checked = draft.monthlyDateEnabled, onCheckedChange = { value -> update { it.copy(monthlyDateEnabled = value) } })
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = 8.dp)) {
                items((1..31).toList()) { day ->
                    FilterChip(
                        selected = day in draft.monthlyDays,
                        onClick = {
                            update { current ->
                                val days = current.monthlyDays.toMutableSet().apply {
                                    if (day in this) remove(day) else add(day)
                                }
                                current.copy(monthlyDays = days)
                            }
                        },
                        enabled = draft.monthlyDateEnabled,
                        label = { Text("${day}일") },
                    )
                }
            }
            if (draft.monthlyDateEnabled && draft.monthlyDays.isEmpty()) {
                Text("날짜를 하나 이상 선택하세요.", color = MaterialTheme.colorScheme.error)
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = AppNavy), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("PREVIEW", color = AppOrange, fontWeight = FontWeight.Black)
                    Text(scheduleSummary(draft), color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("한 조건 안의 항목은 모두 만족할 때 적용됩니다.", color = Color.White.copy(alpha = .7f))
                }
            }
        }
    }
}

@Composable
private fun TimeButton(label: String, minute: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(vertical = 14.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(formatMinute(minute), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun AppRulesScreen(
    padding: PaddingValues,
    app: InstalledApp,
    state: BlockerState,
    viewModel: BlockerViewModel,
    requestProtectedAction: (() -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    val blocked = app.packageName in state.blockedPackages
    val assignedIds = state.appScheduleIds[app.packageName].orEmpty()
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { OutlinedButton(onClick = onBack) { Text("‹ 앱 목록") } }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppNavy)) {
                Row(Modifier.padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("APP POLICY", color = AppOrange, fontWeight = FontWeight.Black)
                        Text(app.label, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Text(app.packageName, color = Color.White.copy(alpha = .65f), maxLines = 1)
                    }
                    Switch(
                        checked = blocked,
                        onCheckedChange = { value ->
                            requestProtectedAction { viewModel.setBlocked(app.packageName, value) }
                        },
                    )
                }
            }
        }
        item {
            SectionTitle("이 앱에 적용할 조건", "앱마다 서로 다른 조건 조합을 사용할 수 있어요")
            if (!blocked) {
                Text("먼저 앱 차단을 켜주세요.", Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.error)
            } else if (assignedIds.isEmpty()) {
                Text("선택된 조건이 없어 현재는 차단되지 않습니다.", Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.tertiary)
            }
        }
        if (state.schedules.isEmpty()) {
            item { EmptyCard("공통 차단 조건을 먼저 만들어 주세요.") }
        } else {
            items(state.schedules, key = BlockSchedule::id) { schedule ->
                val assigned = schedule.id in assignedIds
                Card(
                    Modifier.fillMaxWidth().clickable(enabled = blocked) {
                        requestProtectedAction {
                            val updated = if (assigned) assignedIds - schedule.id else assignedIds + schedule.id
                            viewModel.setAppSchedules(app.packageName, updated)
                        }
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = if (assigned) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(schedule.name, fontWeight = FontWeight.Black)
                            Text(
                                if (schedule.action == ScheduleAction.BLOCK) "차단 실행 · ${scheduleSummary(schedule)}" else "차단 해제 · ${scheduleSummary(schedule)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                            )
                        }
                        Switch(
                            checked = assigned,
                            onCheckedChange = { value ->
                                requestProtectedAction {
                                    val updated = if (value) assignedIds + schedule.id else assignedIds - schedule.id
                                    viewModel.setAppSchedules(app.packageName, updated)
                                }
                            },
                            enabled = blocked,
                        )
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Text(
                    "이 앱에 선택한 해제 조건과 차단 조건이 동시에 맞으면 해제 조건이 우선합니다.",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun PasswordGateDialog(
    hasPassword: Boolean,
    onVerify: (String, (Boolean) -> Unit) -> Unit,
    onDismiss: () -> Unit,
    onPasswordRequired: () -> Unit,
    onSuccess: () -> Unit,
) {
    if (!hasPassword) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("비밀번호가 필요해요", fontWeight = FontWeight.Black) },
            text = { Text("차단 실행·해제 설정을 변경하려면 먼저 숫자 비밀번호를 설정해야 합니다.") },
            confirmButton = { Button(onClick = onPasswordRequired) { Text("설정으로 이동") } },
            dismissButton = { OutlinedButton(onClick = onDismiss) { Text("취소") } },
        )
        return
    }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("비밀번호 확인", fontWeight = FontWeight.Black) },
        text = {
            Column {
                Text("차단 설정을 변경하려면 비밀번호를 입력하세요.")
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.filter(Char::isDigit).take(12); error = false },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    label = { Text("숫자 비밀번호") },
                    singleLine = true,
                    isError = error,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    supportingText = { if (error) Text("비밀번호가 맞지 않아요.") },
                )
            }
        },
        confirmButton = {
            Button(
                enabled = password.length >= 4 && !checking,
                onClick = {
                    checking = true
                    onVerify(password) { correct ->
                        checking = false
                        if (correct) onSuccess() else {
                            error = true
                            password = ""
                        }
                    }
                },
            ) { Text(if (checking) "확인 중" else "확인") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun SettingsScreen(
    padding: PaddingValues,
    state: BlockerState,
    viewModel: BlockerViewModel,
    accessibilityEnabled: Boolean,
    openAccessibilitySettings: () -> Unit,
    requestProtectedAction: (() -> Unit) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val validPassword = password.length in 4..12 && password == confirmation && password.all(Char::isDigit)
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Card(
                Modifier.fillMaxWidth().clickable(onClick = openAccessibilitySettings),
                colors = CardDefaults.cardColors(
                    containerColor = if (accessibilityEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                ),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("접근성 권한", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Text(if (accessibilityEnabled) "연결됨 · 차단 기능 사용 가능" else "탭하여 차단 서비스를 연결하세요")
                }
            }
        }
        item {
            SectionTitle("설정 보호 비밀번호", "차단 실행·해제 설정을 변경할 때 사용")
            if (state.hasPassword) {
                Text("비밀번호가 설정되어 있어요.", Modifier.padding(top = 8.dp), color = AppCobalt, fontWeight = FontWeight.Bold)
            }
            PasswordField("새 숫자 비밀번호 (4~12자리)", password) { password = it }
            PasswordField("비밀번호 확인", confirmation) { confirmation = it }
            Button(
                onClick = {
                    val changePassword = {
                        viewModel.setPassword(password)
                        password = ""
                        confirmation = ""
                    }
                    if (state.hasPassword) requestProtectedAction(changePassword) else changePassword()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = validPassword,
            ) { Text(if (state.hasPassword) "비밀번호 변경" else "비밀번호 설정") }
            if (state.hasPassword) {
                OutlinedButton(
                    onClick = { requestProtectedAction { viewModel.clearPassword() } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("비밀번호 제거") }
            }
        }
        item { HorizontalDivider() }
        item {
            SectionTitle("시작 화면", "앱을 열었을 때 먼저 표시할 기능")
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DestinationCard("기능 홈", "전체 기능을 한눈에 보기", state.startDestination == StartDestination.HOME) {
                    viewModel.setStartDestination(StartDestination.HOME)
                }
                DestinationCard("App Blocker", "차단 설정으로 바로 시작", state.startDestination == StartDestination.APP_BLOCKER) {
                    viewModel.setStartDestination(StartDestination.APP_BLOCKER)
                }
            }
        }
        item {
            Text("소림파워 v0.5.0", fontWeight = FontWeight.Black)
            Text("집중을 위한 개인용 시스템", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PasswordField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(12)) },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
    )
}

@Composable
private fun DestinationCard(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(18.dp).clip(CircleShape).background(if (selected) AppCobalt else MaterialTheme.colorScheme.surfaceVariant))
            Column(Modifier.padding(start = 14.dp)) {
                Text(title, fontWeight = FontWeight.Black)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EmptyCard(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Text(message, Modifier.fillMaxWidth().padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun showTimePicker(context: android.content.Context, initialMinute: Int, onSelected: (Int) -> Unit) {
    TimePickerDialog(
        context,
        { _, hour, minute -> onSelected(hour * 60 + minute) },
        initialMinute / 60,
        initialMinute % 60,
        true,
    ).show()
}

private fun formatMinute(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)

private fun dayLabel(day: DayOfWeek) = when (day) {
    DayOfWeek.MONDAY -> "월"
    DayOfWeek.TUESDAY -> "화"
    DayOfWeek.WEDNESDAY -> "수"
    DayOfWeek.THURSDAY -> "목"
    DayOfWeek.FRIDAY -> "금"
    DayOfWeek.SATURDAY -> "토"
    DayOfWeek.SUNDAY -> "일"
}

private fun repeatLabel(cycle: RepeatCycle) = when (cycle) {
    RepeatCycle.EVERY_WEEK -> "매주"
    RepeatCycle.EVERY_TWO_WEEKS -> "2주마다"
    RepeatCycle.EVERY_MONTH -> "매월"
}

private fun scheduleSummary(schedule: BlockSchedule): String {
    val days = if (schedule.weekdays.size == 7) "매일" else DayOfWeek.entries
        .filter { it in schedule.weekdays }.joinToString("·", transform = ::dayLabel)
    val time = if (schedule.timeEnabled) "${formatMinute(schedule.startMinute)}~${formatMinute(schedule.endMinute)}" else "하루 종일"
    val dates = if (schedule.monthlyDateEnabled) {
        if (schedule.monthlyDays.isEmpty()) "날짜 미선택" else schedule.monthlyDays.sorted().joinToString(",", postfix = "일")
    } else null
    return listOfNotNull(days, time, repeatLabel(schedule.repeatCycle), dates).joinToString(" · ")
}
