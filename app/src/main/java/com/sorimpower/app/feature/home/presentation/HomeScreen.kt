package com.sorimpower.app.feature.home.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sorimpower.app.core.ui.AppCobalt
import com.sorimpower.app.core.ui.AppGreen
import com.sorimpower.app.core.ui.AppOrange
import com.sorimpower.app.feature.blocker.data.BlockerState

@Composable
internal fun HomeScreen(
    padding: PaddingValues,
    state: BlockerState,
    serviceEnabled: Boolean,
    openBlocker: () -> Unit,
    openBodyLog: () -> Unit,
    openAccessibilitySettings: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("내 기능", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text("오늘 필요한 도구를 바로 열어보세요", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("2개 기능", color = AppCobalt, fontWeight = FontWeight.Bold)
            }
        }
        item {
            FeatureCard(
                title = "App Blocker",
                description = if (state.enabled) "집중 모드 실행 중" else "방해되는 앱을 조건에 맞게 차단",
                badge = if (state.enabled) "ACTIVE" else "READY",
                icon = Icons.Rounded.Lock,
                accent = AppCobalt,
                onClick = openBlocker,
            )
        }
        item {
            FeatureCard(
                title = "Body Log",
                description = "체중과 오늘 먹은 음식을 한곳에 기록",
                badge = "NEW",
                icon = Icons.Rounded.FavoriteBorder,
                accent = AppOrange,
                onClick = openBodyLog,
            )
        }
        if (!serviceEnabled) {
            item {
                Card(
                    Modifier.fillMaxWidth().clickable(onClick = openAccessibilitySettings),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0F7)),
                ) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).background(AppOrange.copy(alpha = .13f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Security, null, tint = AppOrange)
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            Text("접근성 권한이 필요해요", fontWeight = FontWeight.Black)
                            Text("연결해야 App Blocker가 동작합니다.", style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = AppOrange)
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(
    title: String,
    description: String,
    badge: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    onClick: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).background(accent.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = accent)
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Text(
                        badge,
                        Modifier.padding(start = 8.dp).background(if (badge == "ACTIVE") AppGreen.copy(alpha = .13f) else accent.copy(alpha = .1f), RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
                        color = if (badge == "ACTIVE") AppGreen else accent,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}
