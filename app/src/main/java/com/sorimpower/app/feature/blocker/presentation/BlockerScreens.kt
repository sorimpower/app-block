package com.sorimpower.app.feature.blocker.presentation

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
import androidx.compose.foundation.Image
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import com.sorimpower.app.feature.blocker.data.BlockerState
import com.sorimpower.app.feature.blocker.data.StartDestination
import com.sorimpower.app.feature.blocker.domain.BlockSchedule
import com.sorimpower.app.feature.blocker.domain.RepeatCycle
import com.sorimpower.app.feature.blocker.domain.ScheduleAction
import com.sorimpower.app.feature.blocker.presentation.BlockerViewModel
import com.sorimpower.app.feature.blocker.presentation.InstalledApp
import com.sorimpower.app.feature.bodylog.presentation.BodyLogScreen
import com.sorimpower.app.feature.bodylog.presentation.BodyLogViewModel
import com.sorimpower.app.core.ui.AppCobalt
import com.sorimpower.app.core.ui.AppLilac
import com.sorimpower.app.core.ui.AppNavy
import com.sorimpower.app.core.ui.AppOrange
import com.sorimpower.app.core.ui.SorimPowerTheme
import java.time.DayOfWeek


@Composable
internal fun BlockerScreen(
    padding: PaddingValues,
    viewModel: BlockerViewModel,
    state: BlockerState,
    onEditSchedule: (BlockSchedule) -> Unit,
    onAddSchedule: () -> Unit,
    requestProtectedAction: (() -> Unit) -> Unit,
    onOpenApp: (InstalledApp) -> Unit,
) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val appsLoading by viewModel.appsLoading.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.loadApps() }
    var message by remember(state.blockMessage) { mutableStateOf(state.blockMessage) }
    var query by remember { mutableStateOf("") }
    val filteredApps = remember(query, apps, state.blockedPackages) {
        val keyword = query.trim().lowercase()
        val matches = if (keyword.isBlank()) apps else apps.filter {
            it.label.lowercase().contains(keyword) || it.packageName.lowercase().contains(keyword)
        }
        matches.sortedWith(
            compareByDescending<InstalledApp> { it.packageName in state.blockedPackages }
                .thenBy { it.label.lowercase() },
        )
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                elevation = CardDefaults.cardElevation(2.dp),
            ) {
                Row(
                    Modifier.padding(10.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF7C2AE8), Color(0xFFB623E6), Color(0xFFE72A99))))
                        .padding(22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("APP BLOCKER", color = Color.White.copy(alpha = .76f), fontWeight = FontWeight.Bold)
                        Text(if (state.enabled) "차단 실행 중" else "차단 대기 중", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Text("앱 ${state.blockedPackages.size}개 · 조건 ${state.activeScheduleCount}개", color = Color.White.copy(alpha = .78f))
                    }
                    CleanSwitch(checked = state.enabled, onCheckedChange = { enabled -> requestProtectedAction { viewModel.setEnabled(enabled) } })
                }
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(22.dp),
                elevation = CardDefaults.cardElevation(1.dp),
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    FeatureSectionTitle(Icons.Rounded.Tune, "차단 조건", "앱마다 필요한 조건을 선택해요", Modifier.weight(1f))
                    Button(onClick = onAddSchedule) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Text("추가", Modifier.padding(start = 4.dp))
                    }
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
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(22.dp),
                elevation = CardDefaults.cardElevation(1.dp),
            ) {
                Column(Modifier.padding(20.dp)) {
                    FeatureSectionTitle(Icons.Rounded.ChatBubbleOutline, "차단 메시지", "차단 화면에서 가장 크게 보여줄 중요한 문장")
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
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(22.dp),
                elevation = CardDefaults.cardElevation(1.dp),
            ) {
                Column(Modifier.padding(20.dp)) {
                    FeatureSectionTitle(Icons.Rounded.Apps, "차단할 앱", "앱을 켠 뒤 적용할 조건을 선택하세요")
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        label = { Text("앱 검색") },
                        placeholder = { Text("예: YouTube") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        singleLine = true,
                    )
                    Text(
                        if (appsLoading) "앱 목록 불러오는 중..." else "검색 결과 ${filteredApps.size}개",
                        Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        items(filteredApps, key = { it.packageName }) { app ->
            Card(
                Modifier.fillMaxWidth().clickable { onOpenApp(app) },
                colors = CardDefaults.cardColors(
                    containerColor = if (app.packageName in state.blockedPackages) Color(0xFFF2E7FC) else Color.White,
                ),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(1.dp),
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    val appIcon = remember(app.packageName) { app.icon.toBitmap(96, 96).asImageBitmap() }
                    Image(appIcon, contentDescription = null, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
                    Column(Modifier.weight(1f).padding(start = 13.dp)) {
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
                    CleanSwitch(
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(1.dp),
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
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(AppCobalt, AppOrange))),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) }
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CleanSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = AppCobalt,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color(0xFFD0CCD5),
            uncheckedTrackColor = Color(0xFFECE9EF),
            uncheckedBorderColor = Color.Transparent,
            disabledUncheckedTrackColor = Color(0xFFECE9EF),
            disabledUncheckedBorderColor = Color.Transparent,
        ),
    )
}

@Composable
internal fun ScheduleScreen(
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
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
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
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(Modifier.fillMaxWidth().padding(8.dp).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(AppCobalt, AppOrange))).padding(20.dp)) {
                    Text("PREVIEW", color = Color.White.copy(alpha = .75f), fontWeight = FontWeight.Black)
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
internal fun AppRulesScreen(
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
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { OutlinedButton(onClick = onBack) { Text("‹ 앱 목록") } }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(26.dp), elevation = CardDefaults.cardElevation(2.dp)) {
                Row(Modifier.fillMaxWidth().padding(10.dp).clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Color(0xFF7C2AE8), Color(0xFFB623E6), Color(0xFFE72A99)))).padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
                    val appIcon = remember(app.packageName) { app.icon.toBitmap(96, 96).asImageBitmap() }
                    Image(appIcon, contentDescription = null, modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)))
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text("APP POLICY", color = Color.White.copy(alpha = .75f), fontWeight = FontWeight.Black)
                        Text(app.label, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Text(app.packageName, color = Color.White.copy(alpha = .65f), maxLines = 1)
                    }
                    CleanSwitch(
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
                        containerColor = if (assigned) Color(0xFFF2E7FC) else Color.White,
                    ),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(1.dp),
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
                        CleanSwitch(
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
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(1.dp)) {
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
internal fun PasswordGateDialog(
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
private fun EmptyCard(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(1.dp)) {
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
