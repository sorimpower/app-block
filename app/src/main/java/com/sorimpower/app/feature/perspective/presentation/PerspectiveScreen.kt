package com.sorimpower.app.feature.perspective.presentation

import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.provider.Settings
import android.text.Html
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.app.NotificationManagerCompat
import coil.compose.AsyncImage
import com.sorimpower.app.core.ui.AppCobalt
import com.sorimpower.app.core.ui.AppGreen
import com.sorimpower.app.core.ui.AppNavy
import com.sorimpower.app.core.ui.AppOrange
import com.sorimpower.app.feature.perspective.data.PerspectiveEntity
import com.sorimpower.app.feature.perspective.data.PerspectiveState
import com.sorimpower.app.feature.perspective.data.PerspectiveTopicEntity
import com.sorimpower.app.feature.perspective.data.ExpansionMomentEntity
import com.sorimpower.app.feature.perspective.data.ThoughtEdgeEntity
import com.sorimpower.app.feature.perspective.data.ThoughtNodeEntity
import com.sorimpower.app.feature.perspective.data.WatchedVideoEntity
import org.json.JSONArray
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.abs
import kotlin.math.sin

private enum class PerspectiveTab(val label: String) { HOME("홈"), MAP("사고 지도"), EXPLORE("탐색"), REPORT("리포트"), RECORDS("기록") }
private enum class PerspectivePage { HOME, DETAIL, HISTORY, MAP, REPORT }
private enum class BrainPeriod(val label: String, val days: Long) { NOW("현재", 7), MONTH("1개월", 30), QUARTER("3개월", 90), YEAR("1년", 365) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerspectiveScreen(
    padding: PaddingValues,
    viewModel: PerspectiveViewModel,
    openExploreRequest: Int = 0,
    openTopicsRequest: Int = 0,
    onSwipeEdgeLeft: () -> Unit = {},
    onSwipeEdgeRight: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busyVideoId by viewModel.busyVideoId.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var page by remember { mutableStateOf(PerspectivePage.HOME) }
    var selectedVideoId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(openExploreRequest) {
        if (openExploreRequest > 0) {
            selectedVideoId = state.videos.firstOrNull()?.id
            page = if (selectedVideoId == null) PerspectivePage.HISTORY else PerspectivePage.DETAIL
        }
    }
    LaunchedEffect(openTopicsRequest) { if (openTopicsRequest > 0) page = PerspectivePage.HISTORY }
    BackHandler(enabled = page != PerspectivePage.HOME) {
        page = PerspectivePage.HOME
        selectedVideoId = null
    }

    Column(Modifier.fillMaxSize().padding(padding)) {
        if (page != PerspectivePage.HOME) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { page = PerspectivePage.HOME }) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "유튜브 분석 홈으로")
                }
                Text(
                    when (page) {
                        PerspectivePage.DETAIL -> "다른 관점"
                        PerspectivePage.HISTORY -> "시청 기록"
                        PerspectivePage.MAP -> "생각 흐름"
                        PerspectivePage.REPORT -> "시청 요약"
                        PerspectivePage.HOME -> ""
                    },
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        message?.let {
            Text(
                it,
                Modifier.fillMaxWidth().clickable { viewModel.clearMessage() }.background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 18.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Box(
            Modifier.fillMaxWidth().weight(1f).horizontalSwipe(
                onSwipeLeft = onSwipeEdgeLeft,
                onSwipeRight = onSwipeEdgeRight,
            ),
        ) {
            when (page) {
                PerspectivePage.HOME -> YoutubeMvpHome(
                    state = state,
                    busyVideoId = busyVideoId,
                    onOpenHistory = { page = PerspectivePage.HISTORY },
                    onOpenVideo = { video, startAnalysis ->
                        selectedVideoId = video.id
                        page = PerspectivePage.DETAIL
                        if (startAnalysis) viewModel.deepAnalyze(video.id)
                    },
                    onOpenMap = { page = PerspectivePage.MAP },
                    onOpenReport = { page = PerspectivePage.REPORT },
                )
                PerspectivePage.DETAIL -> YoutubePerspectiveDetail(
                    video = state.videos.firstOrNull { it.id == selectedVideoId },
                    state = state,
                    busy = busyVideoId == selectedVideoId,
                    onAnalyze = { selectedVideoId?.let(viewModel::deepAnalyze) },
                    onRecommendationOpen = viewModel::markRecommendedPerspectiveOpened,
                )
                PerspectivePage.HISTORY -> YoutubeHistoryScreen(
                    state = state,
                    busyVideoId = busyVideoId,
                    viewModel = viewModel,
                    onOpenVideo = { video, startAnalysis ->
                        selectedVideoId = video.id
                        page = PerspectivePage.DETAIL
                        if (startAnalysis) viewModel.deepAnalyze(video.id)
                    },
                )
                PerspectivePage.MAP -> ThoughtMapScreen(state)
                PerspectivePage.REPORT -> ReportScreen(state)
            }
        }
    }
}

private fun Modifier.horizontalSwipe(
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var dx = 0f
            var dy = 0f
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    if (abs(dx) > 100f && abs(dx) > abs(dy) * 1.2f) {
                        if (dx < 0) onSwipeLeft() else onSwipeRight()
                    }
                    break
                }
                val delta = change.positionChange()
                dx += delta.x
                dy += delta.y
            }
        }
    }
}

@Composable
private fun YoutubeMvpHome(
    state: PerspectiveState,
    busyVideoId: String?,
    onOpenHistory: () -> Unit,
    onOpenVideo: (WatchedVideoEntity, Boolean) -> Unit,
    onOpenMap: () -> Unit,
    onOpenReport: () -> Unit,
) {
    val latestVideo = state.videos.firstOrNull()
    val recentVideos = state.videos
        .distinctBy { "${it.title.trim().lowercase()}|${it.channelName.trim().lowercase()}" }
        .filter { it.id != latestVideo?.id }
        .take(3)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("다른 관점", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text("내가 본 영상, 다른 쪽에서도 볼까?", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    modifier = Modifier.size(40.dp).clickable(onClick = onOpenHistory),
                    shape = CircleShape,
                    color = Color(0xFFF1EEFA),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.History, contentDescription = "전체 시청 기록", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
                    }
                }
            }
        }
        item {
            if (latestVideo == null) {
                EmptyStateCard("YouTube에서 영상을 2분 이상 시청하면 방금 본 영상이 여기에 나타나요.")
            } else {
                LatestVideoCard(
                    video = latestVideo,
                    busy = busyVideoId == latestVideo.id,
                    onAnalyze = { onOpenVideo(latestVideo, true) },
                    onOpen = { onOpenVideo(latestVideo, false) },
                )
            }
        }
        if (recentVideos.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("최근에 본 영상", modifier = Modifier.weight(1f), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onOpenHistory) { Text("전체 기록") }
                }
            }
            items(recentVideos, key = WatchedVideoEntity::id) { video ->
                CompactVideoRow(video = video, onClick = { onOpenVideo(video, false) })
            }
        }
        if (state.moments.isNotEmpty()) {
            item {
                InsightLinkCard(
                    title = "최근 여러 관점을 탐색했어요",
                    description = "내가 어떻게 생각을 넓혔는지 한눈에 볼 수 있어요.",
                    label = "생각 흐름 보기",
                    color = MaterialTheme.colorScheme.primary,
                    onClick = onOpenMap,
                )
            }
        }
        state.reports.firstOrNull()?.let { report ->
            item {
                InsightLinkCard(
                    title = "최근 시청 내용 요약",
                    description = report.summary.take(88),
                    label = "시청 요약 보기",
                    color = MaterialTheme.colorScheme.primary,
                    onClick = onOpenReport,
                )
            }
        }
    }
}

@Composable
private fun LatestVideoCard(
    video: WatchedVideoEntity,
    busy: Boolean,
    onAnalyze: () -> Unit,
    onOpen: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("방금 본 영상", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
            Row(Modifier.padding(top = 11.dp).fillMaxWidth().clickable(onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
                YoutubeThumbnail(video, Modifier.size(width = 106.dp, height = 62.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Black)
                    Text(video.channelName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(onClick = onAnalyze, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                else Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(17.dp))
                Text("다른 관점 보기", Modifier.padding(start = 7.dp))
            }
        }
    }
}

@Composable
private fun YoutubePerspectiveDetail(
    video: WatchedVideoEntity?,
    state: PerspectiveState,
    busy: Boolean,
    onAnalyze: () -> Unit,
    onRecommendationOpen: (String) -> Unit,
) {
    if (video == null) {
        EmptyStateCard("선택한 시청 기록을 찾지 못했어요.")
        return
    }
    val context = LocalContext.current
    val analysis = state.analyses.firstOrNull { it.videoId == video.id }
    val perspectives = state.perspectives.filter { it.videoId == video.id && it.status != "visited" }.take(3)
    var selectedPerspectiveId by remember(video.id) { mutableStateOf<String?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    YoutubeThumbnail(video, Modifier.size(width = 94.dp, height = 56.dp))
                    Column(Modifier.weight(1f).padding(start = 11.dp)) {
                        Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Black)
                        Text(video.channelName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (analysis == null) {
            item {
                EmptyStateCard("이 영상의 핵심 주장과 다른 질문을 정리해드릴게요.")
            }
            item {
                Button(onClick = onAnalyze, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    else Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(17.dp))
                    Text("다른 관점 보기", Modifier.padding(start = 7.dp))
                }
            }
        } else {
            item {
                Text("이 영상의 핵심 주장", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                Text(analysis.mainClaim, Modifier.padding(top = 7.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
            item { Text("이쪽에서도 한번 볼래요?", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium) }
            if (perspectives.isEmpty()) item { EmptyStateCard("다른 관점을 정리하지 못했어요. 다시 시도해 주세요.") }
            items(perspectives, key = PerspectiveEntity::id) { perspective ->
                val selected = selectedPerspectiveId == perspective.id
                val recommendations = state.recommendedVideos.filter { it.perspectiveId == perspective.id }.take(1)
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(15.dp)) {
                        Text(perspective.label, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        Text(perspective.description, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall)
                        Text(perspective.representativeQuestion, Modifier.padding(top = 8.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = {
                            selectedPerspectiveId = perspective.id
                        }, modifier = Modifier.align(Alignment.End)) {
                            Text(if (selected) "추천 콘텐츠 보기" else "살펴보기 →")
                        }
                        if (selected) {
                            if (recommendations.isEmpty()) {
                                Text("이 관점에 맞는 YouTube 영상을 찾고 있어요.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Text("추천 영상", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                recommendations.forEach { recommended ->
                                    RecommendedVideoRow(recommended) {
                                        onRecommendationOpen(perspective.id)
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(recommended.url)))
                                    }
                                }
                                Text(
                                    "이 추천 영상을 2분 이상 시청하면 생각 흐름에 반영돼요.",
                                    modifier = Modifier.padding(top = 7.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YoutubeHistoryScreen(
    state: PerspectiveState,
    busyVideoId: String?,
    viewModel: PerspectiveViewModel,
    onOpenVideo: (WatchedVideoEntity, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val mediaAccessEnabled = remember(context) { context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context) }
    val videos = state.videos.distinctBy { "${it.title.trim().lowercase()}|${it.channelName.trim().lowercase()}" }
    var videoToDelete by remember { mutableStateOf<WatchedVideoEntity?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().clickable { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                    .background(if (mediaAccessEnabled) AppGreen.copy(alpha = .10f) else AppOrange.copy(alpha = .10f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 13.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).background(if (mediaAccessEnabled) AppGreen else AppOrange, CircleShape))
                Text(if (mediaAccessEnabled) "YouTube 자동 감지 켜짐" else "YouTube 자동 감지 권한이 필요해요", Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Text("설정", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
        if (videos.isEmpty()) item { EmptyStateCard("YouTube에서 영상을 2분 이상 시청하면 이곳에 기록돼요.") }
        items(videos.take(30), key = WatchedVideoEntity::id) { video ->
            HistoryVideoRow(
                video = video,
                busy = busyVideoId == video.id,
                onOpen = { onOpenVideo(video, false) },
                onAnalyze = { onOpenVideo(video, true) },
                onDelete = { videoToDelete = video },
            )
        }
    }
    videoToDelete?.let { video ->
        AlertDialog(
            onDismissRequest = { videoToDelete = null },
            title = { Text("시청 기록 삭제") },
            text = { Text("이 영상의 분석 결과와 연결된 탐색 관점도 함께 삭제됩니다.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteWatchRecord(video.id); videoToDelete = null }) { Text("삭제", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { videoToDelete = null }) { Text("취소") } },
        )
    }
}

@Composable
private fun CompactVideoRow(video: WatchedVideoEntity, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YoutubeThumbnail(video, Modifier.size(width = 68.dp, height = 42.dp))
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(video.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            Text(video.channelName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.PlayCircle, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun HistoryVideoRow(
    video: WatchedVideoEntity,
    busy: Boolean,
    onOpen: () -> Unit,
    onAnalyze: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
                YoutubeThumbnail(video, Modifier.size(width = 76.dp, height = 46.dp))
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    Text(listOf(video.channelName, formatTime(video.watchedAt)).filter(String::isNotBlank).joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "시청 기록 삭제", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            TextButton(onClick = onAnalyze, enabled = !busy, modifier = Modifier.align(Alignment.End)) {
                if (busy) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(15.dp))
                Text("다른 관점 보기", Modifier.padding(start = 5.dp))
            }
        }
    }
}

@Composable
private fun YoutubeThumbnail(video: WatchedVideoEntity, modifier: Modifier) {
    val thumbnail = video.youtubeVideoId.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }?.let { "https://img.youtube.com/vi/$it/mqdefault.jpg" }
    if (thumbnail != null) {
        AsyncImage(model = thumbnail, contentDescription = null, modifier = modifier.background(Color(0xFFE8E4EC), RoundedCornerShape(9.dp)))
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.tertiary.copy(alpha = .12f), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.PlayCircle, null, tint = MaterialTheme.colorScheme.tertiary)
        }
    }
}

@Composable
private fun InsightLinkCard(title: String, description: String, label: String, color: Color, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = .08f))) {
        Column(Modifier.padding(15.dp)) {
            Text(title, fontWeight = FontWeight.Black, color = color)
            Text(description, Modifier.padding(top = 4.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(label + " →", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun PerspectiveHome(state: PerspectiveState, openTab: (PerspectiveTab) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { BrainMapCard(state) }
        item {
            SectionCard("최근 사고 확장", Icons.Rounded.Map, MaterialTheme.colorScheme.primary, onClick = { openTab(PerspectiveTab.MAP) }) {
                val visited = state.perspectives.filter { it.status == "visited" }.sortedBy(PerspectiveEntity::visitedAt).takeLast(4)
                if (visited.isEmpty()) EmptyDataHint("아직 탐색한 관점이 없어요. 최근 영상에서 다른 관점을 열어보세요.")
                else {
                    Text(visited.joinToString("  →  ", transform = PerspectiveEntity::label), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                    val lastVisitedAt = visited.lastOrNull()?.visitedAt
                    if (lastVisitedAt != null) Text("${relativeDay(lastVisitedAt)} 새 관점이 열렸어요", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text("사고 지도 보기  →", Modifier.padding(top = 10.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            SectionCard("내가 안 본 세상", Icons.Rounded.Public, AppGreen, onClick = { openTab(PerspectiveTab.EXPLORE) }) {
                val missing = state.perspectives.filter { it.status == "suggested" }.distinctBy(PerspectiveEntity::label).take(3)
                if (missing.isEmpty()) EmptyDataHint("영상을 분석하면 아직 접하지 않은 관점이 나타나요.")
                else {
                    val sourceVideo = state.videos.firstOrNull { video -> missing.any { it.videoId == video.id } }
                    Text(
                        sourceVideo?.let { "‘${it.title.take(24)}${if (it.title.length > 24) "…" else ""}’에서 거의 접하지 않은 관점" }
                            ?: "최근 영상에서 거의 접하지 않은 관점",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TopicPills(missing.map(PerspectiveEntity::label), AppGreen)
                    Text("하나 탐색해보기  →", Modifier.padding(top = 10.dp), fontWeight = FontWeight.Bold, color = AppGreen, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        state.moments.firstOrNull()?.let { moment ->
            item {
                SectionCard("생각이 넓어진 순간", Icons.Rounded.Lightbulb, MaterialTheme.colorScheme.tertiary, onClick = { openTab(PerspectiveTab.REPORT) }) {
                    Text("“${moment.fromLabel}에서 ${moment.toLabel}까지 생각을 넓혔어요.”", fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyLarge)
                    Text(formatDate(moment.occurredAt), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        state.reports.firstOrNull()?.let { report ->
            item {
                SectionCard("이번 주 시청 요약", Icons.Rounded.AutoAwesome, MaterialTheme.colorScheme.primary, onClick = { openTab(PerspectiveTab.REPORT) }) {
                    val dominant = jsonStrings(report.dominantTopicsJson).take(3)
                    val under = jsonStrings(report.underExposedPerspectivesJson).take(3)
                    if (dominant.isNotEmpty()) MiniReportRow("자주 본 주제", dominant.joinToString(" · "), MaterialTheme.colorScheme.primary)
                    if (under.isNotEmpty()) MiniReportRow("아직 보지 않은 관점", under.joinToString(" · "), MaterialTheme.colorScheme.primary)
                    if (dominant.isEmpty() && under.isEmpty()) EmptyDataHint(report.summary)
                    Text("시청 요약 보기  →", Modifier.padding(top = 10.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun BrainMapCard(state: PerspectiveState) {
    var period by remember { mutableStateOf(BrainPeriod.NOW) }
    val from = remember(period) { System.currentTimeMillis() - period.days * 24 * 60 * 60 * 1_000L }
    val meaningfulVideoIds = remember(state.videos, from) {
        state.videos.filter { video ->
            video.watchedAt >= from && video.watchedSec >= 120
        }.mapTo(mutableSetOf(), WatchedVideoEntity::id)
    }
    val counts = state.videoTopics.filter { it.videoId in meaningfulVideoIds }.groupingBy { it.topicId }.eachCount()
    val values = state.topics.filter { it.enabled }.map { it to (counts[it.id] ?: 0) }.filter { it.second > 0 }.sortedByDescending { it.second }.take(6)
    val total = values.sumOf { it.second }.coerceAtLeast(1)
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text("🧠 최근 내 관심", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                BrainPeriod.entries.forEach { item -> FilterChip(selected = period == item, onClick = { period = item }, label = { Text(item.label, style = MaterialTheme.typography.labelSmall) }) }
            }
            if (values.isEmpty()) EmptyDataHint("YouTube를 시청하면 최근 관심 주제가 여기에 쌓여요.")
            else values.forEachIndexed { index, (topic, count) ->
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    val ratio = count.toFloat() / total
                    Text(topic.name, Modifier.size(width = 68.dp, height = 20.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(progress = { ratio }, modifier = Modifier.weight(1f).height(8.dp), color = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, AppGreen)[index % 3], trackColor = MaterialTheme.colorScheme.surfaceVariant)
                    Text("${(ratio * 100).toInt()}%", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ThoughtMapScreen(state: PerspectiveState) {
    var selectedTopicId by remember(state.topics) { mutableStateOf(state.topics.firstOrNull { it.enabled }?.id) }
    val nodes = state.nodes.filter { it.topicId == selectedTopicId }.takeLast(14)
    val edges = state.edges.filter { it.topicId == selectedTopicId }
    val orderedNodes = nodes.sortedBy { node ->
        when {
            node.type == "video" -> node.videoId?.let { id -> state.videos.firstOrNull { it.id == id }?.watchedAt } ?: node.createdAt
            node.status == "visited" -> node.perspectiveId?.let { id -> state.perspectives.firstOrNull { it.id == id }?.visitedAt } ?: node.createdAt
            else -> Long.MAX_VALUE
        }
    }
    val seenOrder = orderedNodes.filter { it.type == "video" || it.status == "visited" }.mapIndexed { index, node -> node.id to index + 1 }.toMap()
    val mapVideo = orderedNodes.lastOrNull { it.type == "video" }?.videoId?.let { id -> state.videos.firstOrNull { it.id == id } }
    var selectedNode by remember { mutableStateOf<ThoughtNodeEntity?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("생각 흐름 지도", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text("초록색은 실제로 본 내용, 회색은 아직 보지 않은 관점이에요.", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MapLegend(AppGreen, "본 영상·탐색 완료", filled = true)
                MapLegend(Color(0xFF9A989F), "아직 안 봄", filled = false)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                state.topics.filter { it.enabled }.take(5).forEach { topic ->
                    FilterChip(selected = selectedTopicId == topic.id, onClick = { selectedTopicId = topic.id }, label = { Text(topic.name) })
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                if (nodes.isEmpty()) Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) { Text("이 주제의 탐색 경로가 아직 없어요.") }
                else Column {
                    mapVideo?.let { video ->
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            val thumbnail = video.youtubeVideoId.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }?.let { "https://img.youtube.com/vi/$it/mqdefault.jpg" }
                            if (thumbnail != null) AsyncImage(
                                model = thumbnail,
                                contentDescription = null,
                                modifier = Modifier.size(width = 74.dp, height = 44.dp).background(Color(0xFFE5E2EA), RoundedCornerShape(9.dp)),
                            ) else Box(Modifier.size(44.dp).background(AppGreen.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PlayCircle, null, tint = AppGreen) }
                            Column(Modifier.padding(start = 10.dp)) {
                                Text("이 영상에서 시작", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                Text(video.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    ThoughtMapCanvas(orderedNodes, edges, onNodeClick = { selectedNode = it })
                }
            }
        }
        if (orderedNodes.isNotEmpty()) item { Text("시청·탐색 순서", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black) }
        items(orderedNodes, key = ThoughtNodeEntity::id) { node ->
            Row(Modifier.fillMaxWidth().clickable { selectedNode = node }.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(15.dp)).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                val nodeColor = if (node.type == "video" || node.status == "visited") AppGreen else Color(0xFFAAA8AE)
                Box(Modifier.size(26.dp).background(nodeColor, CircleShape), contentAlignment = Alignment.Center) {
                    Text(seenOrder[node.id]?.toString() ?: "–", color = Color.White, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelSmall)
                }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(node.label, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                    Text(if (node.type == "video") "시청 영상" else if (node.status == "visited") "탐색한 관점" else "추천 관점", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("영상 보기", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    selectedNode?.let { node -> NodeVideoListDialog(node, state, onDismiss = { selectedNode = null }) }
}

@Composable
private fun ThoughtMapCanvas(nodes: List<ThoughtNodeEntity>, edges: List<ThoughtEdgeEntity>, onNodeClick: (ThoughtNodeEntity) -> Unit) {
    Canvas(Modifier.fillMaxWidth().height(320.dp).padding(16.dp).pointerInput(nodes) {
        detectTapGestures { tap ->
            if (nodes.isEmpty()) return@detectTapGestures
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = minOf(size.width, size.height) * .36f
            val nearest = nodes.mapIndexed { index, node ->
                val angle = (Math.PI * 2 * index / nodes.size) - Math.PI / 2
                val point = Offset(center.x + cos(angle).toFloat() * radius, center.y + sin(angle).toFloat() * radius)
                val dx = tap.x - point.x
                val dy = tap.y - point.y
                node to kotlin.math.sqrt(dx * dx + dy * dy)
            }.minByOrNull { it.second }
            if (nearest != null && nearest.second <= 30.dp.toPx()) onNodeClick(nearest.first)
        }
    }) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = minOf(size.width, size.height) * .36f
        val positions = nodes.mapIndexed { index, node ->
            val angle = (Math.PI * 2 * index / nodes.size) - Math.PI / 2
            node.id to Offset(center.x + cos(angle).toFloat() * radius, center.y + sin(angle).toFloat() * radius)
        }.toMap()
        edges.forEach { edge ->
            val from = positions[edge.fromNodeId] ?: return@forEach
            val to = positions[edge.toNodeId] ?: return@forEach
            drawLine(if (edge.type == "selected") AppGreen else Color(0xFFD4D3D7), from, to, strokeWidth = if (edge.type == "selected") 5f else 2.5f)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(45, 44, 52); textSize = 11.dp.toPx(); textAlign = Paint.Align.CENTER }
        val orderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = 10.dp.toPx(); textAlign = Paint.Align.CENTER; isFakeBoldText = true }
        val seenOrder = nodes.filter { it.type == "video" || it.status == "visited" }.mapIndexed { index, node -> node.id to index + 1 }.toMap()
        nodes.forEach { node ->
            val point = positions[node.id] ?: return@forEach
            val filledColor = if (node.type == "video" || node.status == "visited") AppGreen else Color(0xFFE3E2E5)
            val radius = if (node.type == "video") 16.dp.toPx() else if (node.status == "visited") 13.dp.toPx() else 11.dp.toPx()
            drawCircle(filledColor, radius = radius, center = point)
            if (node.status != "visited" && node.type != "video") drawCircle(Color(0xFF99979E), radius = radius, center = point, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
            seenOrder[node.id]?.let { order -> drawContext.canvas.nativeCanvas.drawText(order.toString(), point.x, point.y + 3.5.dp.toPx(), orderPaint) }
            drawContext.canvas.nativeCanvas.drawText(node.label.take(9), point.x, point.y + 27.dp.toPx(), paint)
        }
    }
}

private data class NodeVideoLink(
    val id: String,
    val title: String,
    val channel: String,
    val thumbnailUrl: String,
    val url: String,
    val role: String,
    val watchOrder: Int?,
)

@Composable
private fun NodeVideoListDialog(node: ThoughtNodeEntity, state: PerspectiveState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val watchedInOrder = state.videos.filter { it.watchedSec >= 120 }.distinctBy(WatchedVideoEntity::id).sortedBy(WatchedVideoEntity::watchedAt)
    val watchOrder = watchedInOrder.mapIndexed { index, video -> video.id to index + 1 }.toMap()
    val links = buildList {
        if (node.type == "video") {
            node.videoId?.let { id -> state.videos.firstOrNull { it.id == id } }?.let { video ->
                add(video.toNodeVideoLink("2분 이상 시청한 영상", watchOrder[video.id]))
            }
        } else {
            val perspective = node.perspectiveId?.let { id -> state.perspectives.firstOrNull { it.id == id } }
            perspective?.let { item ->
                state.videos.firstOrNull { it.id == item.videoId }?.let { source ->
                    add(source.toNodeVideoLink("이 관점이 나온 영상", watchOrder[source.id]))
                }
                state.recommendedVideos.filter { it.perspectiveId == item.id }.take(2).forEach { recommended ->
                    val watched = state.videos.firstOrNull { video ->
                        video.watchedSec >= 120 && (video.youtubeVideoId == recommended.youtubeId() || (video.title == recommended.title && video.channelName == recommended.channelName))
                    }
                    if (watched != null) {
                        add(watched.toNodeVideoLink("추천 후 2분 이상 시청", watchOrder[watched.id]))
                    } else {
                        add(NodeVideoLink(recommended.id, recommended.title, recommended.channelName, recommended.thumbnailUrl, recommended.url, "추천 영상 · 아직 미시청", null))
                    }
                }
            }
        }
    }.distinctBy(NodeVideoLink::id)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(node.label, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Black)
                Text("연결된 영상", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            if (links.isEmpty()) {
                Text("연결된 영상 기록이 아직 없어요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 430.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(links, key = NodeVideoLink::id) { link ->
                        Column(
                            Modifier.fillMaxWidth().clickable(enabled = link.url.isNotBlank()) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)))
                            }.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(13.dp)).padding(9.dp),
                        ) {
                            Text(
                                link.title.readableYouTubeText(),
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Row(
                                Modifier.fillMaxWidth().padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (link.thumbnailUrl.isNotBlank()) AsyncImage(link.thumbnailUrl, null, Modifier.size(width = 76.dp, height = 45.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)))
                                else Box(Modifier.size(width = 76.dp, height = 45.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PlayCircle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                Column(Modifier.weight(1f).padding(start = 9.dp)) {
                                    if (link.channel.isNotBlank()) Text(
                                        link.channel.readableYouTubeText(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(buildString {
                                    link.watchOrder?.let { append("시청 ${it}번째 · ") }
                                    append(link.role)
                                    }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

@Suppress("DEPRECATION")
private fun String.readableYouTubeText(): String = Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY)
    .toString()
    .replace(Regex("\\s+"), " ")
    .trim()

private fun WatchedVideoEntity.toNodeVideoLink(role: String, watchOrder: Int?): NodeVideoLink = NodeVideoLink(
    id = id,
    title = title,
    channel = channelName,
    thumbnailUrl = youtubeVideoId.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }?.let { "https://img.youtube.com/vi/$it/mqdefault.jpg" }.orEmpty(),
    url = url,
    role = role,
    watchOrder = watchOrder,
)

@Composable
private fun ExploreScreen(state: PerspectiveState, busyVideoId: String?, viewModel: PerspectiveViewModel) {
    val context = LocalContext.current
    val latestVideo = state.videos.firstOrNull()
    val latestPerspectives = latestVideo?.let { video -> state.perspectives.filter { it.videoId == video.id && it.status == "suggested" }.take(4) }.orEmpty()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        if (latestVideo != null) {
            item {
                Text("최근 분석에서 열어볼 다른 관점", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                if (latestPerspectives.isEmpty()) Text("시청 기록에서 ‘다른 관점 보기’를 실행하면 이곳에 탐색할 질문이 나타나요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else item { EmptyStateCard("YouTube 영상을 2분 이상 시청하면 여기서 다른 관점을 발견할 수 있어요.") }
        items(latestPerspectives, key = PerspectiveEntity::id) { item ->
            val recommendations = state.recommendedVideos.filter { it.perspectiveId == item.id }
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(15.dp)) {
                    Text(item.label, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    Text(item.description, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall)
                    Text(item.representativeQuestion, Modifier.padding(top = 7.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    if (recommendations.isEmpty()) {
                        Text("추천 영상을 찾는 중이거나 공개 검색 결과가 없어요.", Modifier.padding(top = 9.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text("이 관점의 추천 영상", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        recommendations.forEach { video ->
                            RecommendedVideoRow(video) {
                                viewModel.explorePerspective(item.id)
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(video.url)))
                            }
                        }
                    }
                }
            }
        }
        val otherSuggestions = state.perspectives.filter { it.status == "suggested" && it.videoId != latestVideo?.id }.distinctBy(PerspectiveEntity::label).take(6)
        if (otherSuggestions.isNotEmpty()) item { Text("이전 영상에서 아직 안 본 관점", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black) }
        items(otherSuggestions, key = { "older:${it.id}" }) { item ->
            val recommendation = state.recommendedVideos.firstOrNull { video -> video.perspectiveId == item.id }
            Row(
                Modifier.fillMaxWidth().clickable {
                    viewModel.explorePerspective(item.id)
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(recommendation?.url ?: "https://www.youtube.com/results?search_query=${Uri.encode(item.searchQuery)}")))
                }.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).background(AppGreen, CircleShape))
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(item.label, fontWeight = FontWeight.Black)
                    Text(item.representativeQuestion, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("탐색 →", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RecommendedVideoRow(video: com.sorimpower.app.feature.perspective.data.PerspectiveRecommendedVideoEntity, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).clickable(onClick = onClick).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (video.thumbnailUrl.isNotBlank()) AsyncImage(model = video.thumbnailUrl, contentDescription = null, modifier = Modifier.size(52.dp))
        Column(Modifier.weight(1f).padding(start = if (video.thumbnailUrl.isBlank()) 0.dp else 9.dp)) {
            Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            Text(video.channelName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Rounded.PlayCircle, null, Modifier.padding(start = 6.dp).size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
    }
}

@Composable
private fun RecordsScreen(state: PerspectiveState, busyVideoId: String?, viewModel: PerspectiveViewModel, openTopics: () -> Unit) {
    val context = LocalContext.current
    val mediaAccessEnabled = remember(context) { context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context) }
    // 과거 버전에서 URL을 뒤늦게 찾으며 남은 중복 행도 한 장으로 보이게 한다.
    val visibleVideos = state.videos.distinctBy { "${it.title.trim().lowercase()}|${it.channelName.trim().lowercase()}" }
    var videoToDelete by remember { mutableStateOf<WatchedVideoEntity?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("시청 기록", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    if (visibleVideos.isNotEmpty()) Text("최근 자동 감지된 영상 ${visibleVideos.size}개", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = openTopics) { Text("주제 관리") }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().clickable { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }.background(
                    if (mediaAccessEnabled) AppGreen.copy(alpha = .10f) else AppOrange.copy(alpha = .10f),
                    RoundedCornerShape(14.dp),
                ).padding(horizontal = 13.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).background(if (mediaAccessEnabled) AppGreen else AppOrange, CircleShape))
                Text(if (mediaAccessEnabled) "YouTube 자동 감지 켜짐" else "YouTube 자동 감지 권한이 필요해요", Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Text("설정", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
        if (visibleVideos.isEmpty()) item { EmptyStateCard("YouTube에서 영상을 재생하면 시청 기록이 자동으로 나타나요.") }
        items(visibleVideos.take(30), key = WatchedVideoEntity::id) { video ->
            VideoCard(video, state, busyVideoId == video.id, onAnalyze = { viewModel.deepAnalyze(video.id) }, onPremiumAnalyze = { viewModel.deepAnalyze(video.id, premiumVideo = true) }, onOpen = {
                if (video.url.isNotBlank()) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(video.url)))
            }, onDelete = { videoToDelete = video })
        }
    }
    videoToDelete?.let { video ->
        AlertDialog(
            onDismissRequest = { videoToDelete = null },
            title = { Text("시청 기록 삭제") },
            text = { Text("이 영상의 분석 결과와 연결된 탐색 관점도 함께 삭제됩니다.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteWatchRecord(video.id); videoToDelete = null }) { Text("삭제", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { videoToDelete = null }) { Text("취소") } },
        )
    }
}

@Composable
private fun VideoCard(video: WatchedVideoEntity, state: PerspectiveState, busy: Boolean, onAnalyze: () -> Unit, onPremiumAnalyze: () -> Unit, onOpen: () -> Unit, onDelete: (() -> Unit)? = null) {
    val analysis = state.analyses.firstOrNull { it.videoId == video.id }
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.PlayCircle, null, tint = MaterialTheme.colorScheme.tertiary)
                Column(Modifier.weight(1f).padding(start = 9.dp)) {
                    Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Black)
                    Text(listOf(video.channelName, formatTime(video.watchedAt)).filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (video.url.isNotBlank()) Icon(Icons.Rounded.OpenInNew, "YouTube 열기", Modifier.clickable(onClick = onOpen), tint = MaterialTheme.colorScheme.primary)
                if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "시청 기록 삭제", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            analysis?.let {
                Text(it.mainClaim, Modifier.padding(top = 9.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                if (it.analysisBasis.isNotBlank()) Text("근거: ${it.analysisBasis}", Modifier.padding(top = 5.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onAnalyze, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp))
                Text(if (analysis == null) "다른 관점 보기 · Terra" else "공개 정보 분석 다시 보기 · Terra", Modifier.padding(start = 6.dp))
            }
            TextButton(onClick = onPremiumAnalyze, enabled = !busy, modifier = Modifier.align(Alignment.End)) {
                Text("영상 정밀 분석 · Gemini 3.5 Flash", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ReportScreen(state: PerspectiveState) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("이번 주 시청 요약", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text("이번 주에 본 영상과 탐색 범위", Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.reports.firstOrNull()?.let { report ->
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(18.dp)) {
                        ReportOverview(state)
                        ReportDistribution(state)
                        ReportTagGroup("자주 본 주제", report.dominantTopicsJson, filled = true)
                        ReportTagGroup("아직 보지 않은 관점", report.underExposedPerspectivesJson, filled = false)
                    }
                }
            }
        } ?: item { Text("이번 주 리포트를 만들 기록이 아직 없어요.") }
        item {
            Text("다른 관점으로 이어 본 기록", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            Text("추천 영상을 2분 이상 시청했을 때만 경로가 완성돼요.", Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.moments.isEmpty()) item { EmptyStateCard("원래 본 영상 → 다른 관점 → 추천 영상의 시청 경로가 아직 없어요.") }
        items(state.moments, key = { it.id }) { moment ->
            ExpansionJourneyCard(moment, state)
        }
    }
}

@Composable
private fun TopicScreen(
    state: PerspectiveState,
    setEnabled: (String, Boolean) -> Unit,
    updateTopic: (String, String, String, () -> Unit) -> Unit,
    acceptSuggestion: (String) -> Unit,
    dismissSuggestion: (String) -> Unit,
    onBack: () -> Unit,
) {
    var editingTopic by remember { mutableStateOf<PerspectiveTopicEntity?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("주제 관리", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text("등록한 주제와 새 제안을 관리해요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onBack) { Text("기록으로") }
            }
        }
        if (state.topicSuggestions.isNotEmpty()) item { Text("등록 대기", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.tertiary) }
        items(state.topicSuggestions, key = { "suggestion:${it.videoId}" }) { suggestion ->
            val video = state.videos.firstOrNull { it.id == suggestion.videoId }
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .45f))) {
                Column(Modifier.fillMaxWidth().padding(15.dp)) {
                    Text(suggestion.proposedName, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.tertiary)
                    if (suggestion.description.isNotBlank()) Text(suggestion.description, Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall)
                    video?.let { Text(it.title, Modifier.padding(top = 6.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ acceptSuggestion(suggestion.videoId) }, Modifier.weight(1f)) { Text("주제 등록") }
                        OutlinedButton({ dismissSuggestion(suggestion.videoId) }, Modifier.weight(1f)) { Text("건너뛰기") }
                    }
                }
            }
        }
        if (state.topics.isEmpty()) item { Text("아직 등록한 주제가 없어요. 의미 있게 시청한 영상에서 새 주제를 제안해 드릴게요.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(state.topics, key = { it.id }) { topic ->
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(topic.name, fontWeight = FontWeight.Black); Text(topic.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    TextButton(onClick = { editingTopic = topic }) { Text("수정") }
                    Switch(topic.enabled, { setEnabled(topic.id, it) })
                }
            }
        }
    }
    editingTopic?.let { topic ->
        var name by remember(topic.id) { mutableStateOf(topic.name) }
        var description by remember(topic.id) { mutableStateOf(topic.description) }
        AlertDialog(
            onDismissRequest = { editingTopic = null },
            title = { Text("주제 수정", fontWeight = FontWeight.Black) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(30) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("주제 이름") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it.take(100) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("설명") },
                        minLines = 2,
                        maxLines = 3,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { updateTopic(topic.id, name, description) { editingTopic = null } },
                    enabled = name.isNotBlank(),
                ) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { editingTopic = null }) { Text("취소") } },
        )
    }
}

@Composable
private fun SectionCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, onClick: () -> Unit, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(36.dp).background(accent.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = accent, modifier = Modifier.size(19.dp)) }; Text(title, Modifier.padding(start = 10.dp), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium) }
            Column(Modifier.padding(top = 12.dp)) { content() }
        }
    }
}

@Composable
private fun FlowLabels(labels: List<String>, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) { labels.forEach { Text("• $it", color = color, fontWeight = FontWeight.Bold) } }
}

@Composable
private fun TopicPills(labels: List<String>, color: Color) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        labels.take(3).forEach { label ->
            Box(
                Modifier.weight(1f).background(color.copy(alpha = .11f), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, color = color, fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun MiniReportRow(label: String, value: String, color: Color) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.padding(top = 2.dp), fontWeight = FontWeight.Black, color = color)
    }
}

@Composable
private fun EmptyDataHint(text: String) {
    Text(text, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun EmptyStateCard(text: String) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Text(text, Modifier.fillMaxWidth().padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MapLegend(color: Color, label: String, filled: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(9.dp).then(
                if (filled) Modifier.background(color, CircleShape)
                else Modifier.background(color.copy(alpha = .24f), CircleShape),
            ),
        )
        Text(label, Modifier.padding(start = 5.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReportOverview(state: PerspectiveState) {
    val from = remember { System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1_000 }
    val watchedCount = state.videos.count { it.watchedAt >= from && it.watchedSec >= 120 }
    val exploredCount = state.perspectives.count { it.status == "visited" && (it.visitedAt ?: 0L) >= from }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ReportMetric("2분 이상 본 영상", watchedCount, Modifier.weight(1f))
        ReportMetric("확장해서 본 관점", exploredCount, Modifier.weight(1f))
    }
}

@Composable
private fun ReportMetric(label: String, value: Int, modifier: Modifier = Modifier) {
    Column(
        modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp)).padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text("${value}개", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReportDistribution(state: PerspectiveState) {
    val from = remember(state.videos, state.videoTopics) { System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1_000 }
    val videoIds = remember(state.videos, from) {
        state.videos.filter { it.watchedAt >= from && it.watchedSec >= 120 }.mapTo(mutableSetOf(), WatchedVideoEntity::id)
    }
    val counts = state.videoTopics.filter { it.videoId in videoIds }.groupingBy { it.topicId }.eachCount()
    val values = state.topics.mapNotNull { topic -> (counts[topic.id] ?: 0).takeIf { it > 0 }?.let { topic.name to it } }.sortedByDescending { it.second }.take(4)
    val total = values.sumOf { it.second }.coerceAtLeast(1)
    if (values.isNotEmpty()) {
        Column(Modifier.padding(top = 14.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(15.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("시청 주제 비중", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            values.forEach { (name, count) ->
                val ratio = count.toFloat() / total
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, Modifier.size(width = 66.dp, height = 18.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(progress = { ratio }, Modifier.weight(1f).height(7.dp), color = AppGreen, trackColor = Color(0xFFDEDEE1))
                    Text("${(ratio * 100).toInt()}%", Modifier.padding(start = 7.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ReportTagGroup(title: String, json: String, filled: Boolean) {
    val values = remember(json) { jsonStrings(json) }
    if (values.isNotEmpty()) {
        Text(title, Modifier.padding(top = 14.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            values.take(3).forEach { value ->
                Box(
                    Modifier.weight(1f).background(
                        if (filled) Color(0xFFE7F4EC) else Color(0xFFF0F0F2),
                        RoundedCornerShape(12.dp),
                    ).padding(horizontal = 8.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(value, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun ExpansionJourneyCard(moment: ExpansionMomentEntity, state: PerspectiveState) {
    val perspective = state.perspectives
        .filter { it.topicId == moment.topicId && it.label == moment.toLabel && it.status == "visited" }
        .maxByOrNull { it.visitedAt ?: 0L }
    val sourceVideo = perspective?.let { item -> state.videos.firstOrNull { it.id == item.videoId } }
    val watchedRecommendation = perspective?.let { item ->
        val recommendations = state.recommendedVideos.filter { it.perspectiveId == item.id }
        state.videos.filter { it.watchedSec >= 120 }.firstOrNull { watched ->
            recommendations.any { recommended ->
                recommended.youtubeId() == watched.youtubeVideoId ||
                    (recommended.title == watched.title && recommended.channelName == watched.channelName)
            }
        }
    }
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(15.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(formatDate(moment.occurredAt), Modifier.align(Alignment.Start), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            JourneyVideoStep("처음 본 영상", sourceVideo)
            Text("↓", style = MaterialTheme.typography.titleLarge, color = Color(0xFF8B8990))
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(12.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("선택한 관점", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(moment.toLabel, Modifier.padding(top = 2.dp), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            Text("↓", style = MaterialTheme.typography.titleLarge, color = Color(0xFF8B8990))
            JourneyVideoStep("2분 이상 본 추천 영상", watchedRecommendation)
        }
    }
}

@Composable
private fun JourneyVideoStep(label: String, video: WatchedVideoEntity?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (video != null) YoutubeThumbnail(video, Modifier.size(width = 84.dp, height = 50.dp))
        else Box(Modifier.size(width = 84.dp, height = 50.dp).background(Color(0xFFE5E5E8), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PlayCircle, null, tint = Color(0xFF99979E)) }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(video?.title ?: "연결된 영상을 찾지 못했어요", maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private fun com.sorimpower.app.feature.perspective.data.PerspectiveRecommendedVideoEntity.youtubeId(): String =
    Uri.parse(url).getQueryParameter("v").orEmpty()

private fun jsonStrings(value: String): List<String> = runCatching { JSONArray(value).let { array -> List(array.length()) { array.optString(it) }.filter(String::isNotBlank) } }.getOrDefault(emptyList())
private fun formatTime(timestamp: Long): String = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M월 d일 HH:mm"))
private fun formatDate(timestamp: Long): String = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M월 d일"))
private fun relativeDay(timestamp: Long): String {
    val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = java.time.LocalDate.now()
    val days = java.time.temporal.ChronoUnit.DAYS.between(date, today).coerceAtLeast(0)
    return when (days) { 0L -> "오늘"; 1L -> "어제"; else -> "${days}일 전" }
}
