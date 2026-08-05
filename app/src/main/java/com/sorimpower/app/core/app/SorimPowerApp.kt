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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Settings
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
    HOME("홈"), BLOCKER("차단"), BODY_LOG("기록"), SCHEDULE("조건"), APP_RULES("앱별 조건"), SETTINGS("설정")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SorimPowerApp(
    viewModel: BlockerViewModel,
    bodyLogViewModel: BodyLogViewModel,
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
            screen = when (state.startDestination) {
                StartDestination.HOME -> Screen.HOME
                StartDestination.APP_BLOCKER -> Screen.BLOCKER
                StartDestination.BODY_LOG -> Screen.BODY_LOG
            }
            initialDestinationApplied = true
        }
    }

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
                            Screen.HOME -> "SORIM POWER"
                            Screen.BLOCKER -> "App Blocker"
                            Screen.BODY_LOG -> "Body Log"
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
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
                { screen = Screen.BODY_LOG },
                openAccessibilitySettings,
            )
            Screen.BODY_LOG -> BodyLogScreen(padding, bodyLogViewModel)
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
    Surface(color = Color.White, shadowElevation = 12.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(70.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(Screen.HOME, Screen.BLOCKER, Screen.BODY_LOG, Screen.SETTINGS).forEach { item ->
                val active = selected == item
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

@Composable
private fun NavIcon(screen: Screen, selected: Boolean) {
    val icon = when (screen) {
        Screen.HOME -> Icons.Rounded.Home
        Screen.BLOCKER -> Icons.Rounded.Block
        Screen.BODY_LOG -> Icons.Rounded.FavoriteBorder
        else -> Icons.Rounded.Settings
    }
    Box(
        Modifier.width(50.dp).height(30.dp).clip(RoundedCornerShape(15.dp))
            .background(if (selected) Brush.horizontalGradient(listOf(AppCobalt, AppOrange)) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = screen.label, modifier = Modifier.size(20.dp), tint = if (selected) Color.White else Color(0xFFAAA7AE)) }
}
