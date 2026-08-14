package com.sorimpower.app.feature.home.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    openAuction: () -> Unit,
    openPhoneInsight: () -> Unit,
    openPropertyTax: () -> Unit,
    openPerspective: () -> Unit,
    openAccessibilitySettings: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            HomeHero()
        }
        item {
            HomeSectionTitle("생활 관리", "나의 일상과 집중")
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeFeatureTile(
                    modifier = Modifier.weight(1f),
                    title = "AI 챙김",
                    description = "놓치기 쉬운 일정을 정리해요",
                    icon = Icons.Rounded.NotificationsNone,
                    accent = AppCobalt,
                    onClick = openPhoneInsight,
                )
                HomeFeatureTile(
                    modifier = Modifier.weight(1f),
                    title = "건강 기록",
                    description = "체중·식사·주사를 기록해요",
                    icon = Icons.Rounded.FavoriteBorder,
                    accent = AppOrange,
                    onClick = openBodyLog,
                )
            }
        }
        item {
            HomeSectionTitle("자산 관리", "경매와 세금 시뮬레이션")
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HomeFeatureTile(
                    modifier = Modifier.weight(1f),
                    title = "부동산 경매",
                    description = "조건에 맞는 물건을 찾아요",
                    icon = Icons.Rounded.Gavel,
                    accent = AppGreen,
                    onClick = openAuction,
                )
                HomeFeatureTile(
                    modifier = Modifier.weight(1f),
                    title = "부동산 세금",
                    description = "보유·양도 계획을 검토해요",
                    icon = Icons.Rounded.AccountBalance,
                    accent = AppCobalt,
                    onClick = openPropertyTax,
                )
            }
        }
        item {
            HomeSectionTitle("생각과 집중", "시야를 넓히고 방해를 줄여요")
        }
        item {
            HomeWideFeatureCard(
                title = "관점 확장",
                description = "최근 본 콘텐츠를 바탕으로 아직 만나지 않은 관점을 탐색해요",
                icon = Icons.Rounded.Psychology,
                accent = AppCobalt,
                onClick = openPerspective,
            )
        }
        item {
            HomeWideFeatureCard(
                title = "앱 차단",
                description = if (state.enabled) "집중 모드 실행 중" else "방해되는 앱을 조건에 맞게 차단",
                icon = Icons.Rounded.Lock,
                accent = AppOrange,
                onClick = openBlocker,
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
                            Text("연결해야 앱 차단이 동작합니다.", style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = AppOrange)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHero() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(AppCobalt, AppOrange)))
                .padding(horizontal = 22.dp, vertical = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(Color.White.copy(alpha = .18f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                }
                Text(
                    "PERSONAL DASHBOARD",
                    modifier = Modifier.padding(start = 8.dp),
                    color = Color.White.copy(alpha = .82f),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.size(13.dp))
            Text(
                "오늘의 나를 한눈에",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
            )
            Text(
                "생활, 자산, 집중을 나잘알에서 관리하세요.",
                modifier = Modifier.padding(top = 5.dp),
                color = Color.White.copy(alpha = .88f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun HomeSectionTitle(title: String, description: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        Text(
            description,
            modifier = Modifier.padding(top = 2.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun HomeFeatureTile(
    modifier: Modifier,
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    onClick: () -> Unit,
) {
    Card(
        modifier.height(156.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Box(Modifier.size(40.dp).background(accent.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = accent)
            }
            Spacer(Modifier.weight(1f))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                description,
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HomeWideFeatureCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    onClick: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).background(accent.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = accent)
            }
            Column(Modifier.weight(1f).padding(horizontal = 13.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}
