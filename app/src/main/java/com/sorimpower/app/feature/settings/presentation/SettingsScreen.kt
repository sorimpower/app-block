package com.sorimpower.app.feature.settings.presentation

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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Security
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
internal fun SettingsScreen(
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
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Card(
                Modifier.fillMaxWidth().clickable(onClick = openAccessibilitySettings),
                colors = CardDefaults.cardColors(
                    containerColor = if (accessibilityEnabled) Color(0xFFF2E7FC) else Color.White,
                ),
                shape = RoundedCornerShape(22.dp),
                elevation = CardDefaults.cardElevation(1.dp),
            ) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(if (accessibilityEnabled) AppCobalt else AppOrange), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Security, contentDescription = null, tint = Color.White, modifier = Modifier.size(23.dp))
                    }
                    Column(Modifier.padding(start = 14.dp)) {
                        Text("접근성 권한", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        Text(if (accessibilityEnabled) "연결됨 · 차단 기능 사용 가능" else "탭하여 차단 서비스를 연결하세요", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(22.dp), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(Modifier.padding(18.dp)) {
                    SectionTitle("설정 보호 비밀번호", "차단 실행·해제 설정을 변경할 때 사용")
                    if (state.hasPassword) Text("비밀번호가 설정되어 있어요.", Modifier.padding(top = 8.dp), color = AppCobalt, fontWeight = FontWeight.Bold)
                    PasswordField("새 숫자 비밀번호 (4~12자리)", password) { password = it }
                    PasswordField("비밀번호 확인", confirmation) { confirmation = it }
                    Button(
                        onClick = {
                            val changePassword = { viewModel.setPassword(password); password = ""; confirmation = "" }
                            if (state.hasPassword) requestProtectedAction(changePassword) else changePassword()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = validPassword,
                    ) { Text(if (state.hasPassword) "비밀번호 변경" else "비밀번호 설정") }
                    if (state.hasPassword) OutlinedButton(
                        onClick = { requestProtectedAction { viewModel.clearPassword() } },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("비밀번호 제거") }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(22.dp), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(Modifier.padding(18.dp)) {
                    SectionTitle("시작 화면", "앱을 열었을 때 먼저 표시할 기능")
                    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DestinationCard(Icons.Rounded.Home, "홈", "전체 기능을 한눈에 보기", state.startDestination == StartDestination.HOME) { viewModel.setStartDestination(StartDestination.HOME) }
                        DestinationCard(Icons.Rounded.Block, "앱 차단", "차단 설정으로 바로 시작", state.startDestination == StartDestination.APP_BLOCKER) { viewModel.setStartDestination(StartDestination.APP_BLOCKER) }
                        DestinationCard(Icons.Rounded.FavoriteBorder, "건강 기록", "체중과 식사 기록으로 바로 시작", state.startDestination == StartDestination.BODY_LOG) { viewModel.setStartDestination(StartDestination.BODY_LOG) }
                        DestinationCard(Icons.Rounded.Gavel, "부동산 경매", "관심 조건 경매 목록으로 바로 시작", state.startDestination == StartDestination.REAL_ESTATE_AUCTION) { viewModel.setStartDestination(StartDestination.REAL_ESTATE_AUCTION) }
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("소림파워 v0.6.0", fontWeight = FontWeight.Black)
                Text("집중을 위한 개인용 시스템", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
private fun DestinationCard(icon: ImageVector, title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFFF2E7FC) else MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(if (selected) AppCobalt else Color.White), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.padding(start = 14.dp)) {
                Text(title, fontWeight = FontWeight.Black)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}


@Composable
private fun SectionTitle(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
