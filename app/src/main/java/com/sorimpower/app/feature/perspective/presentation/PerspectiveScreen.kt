package com.sorimpower.app.feature.perspective.presentation

import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Explore
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
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
    var tab by remember { mutableStateOf(PerspectiveTab.HOME) }
    var manageTopics by remember { mutableStateOf(false) }
    LaunchedEffect(openExploreRequest) { if (openExploreRequest > 0) tab = PerspectiveTab.EXPLORE }
    LaunchedEffect(openTopicsRequest) {
        if (openTopicsRequest > 0) {
            tab = PerspectiveTab.RECORDS
            manageTopics = true
        }
    }

    Column(Modifier.fillMaxSize().padding(padding)) {
        PrimaryTabRow(selectedTabIndex = PerspectiveTab.entries.indexOf(tab)) {
            PerspectiveTab.entries.forEach { item ->
                Tab(selected = tab == item, onClick = { manageTopics = false; tab = item }, text = { Text(item.label, style = MaterialTheme.typography.labelSmall, maxLines = 1) })
            }
        }
        message?.let {
            Text(
                it,
                Modifier.fillMaxWidth().clickable { viewModel.clearMessage() }.background(Color(0xFFF1EEFF)).padding(horizontal = 18.dp, vertical = 10.dp),
                color = AppCobalt,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Box(
            Modifier.fillMaxWidth().weight(1f).horizontalSwipe(
                onSwipeLeft = {
                    val index = PerspectiveTab.entries.indexOf(tab)
                    if (index == PerspectiveTab.entries.lastIndex) onSwipeEdgeLeft()
                    else { manageTopics = false; tab = PerspectiveTab.entries[index + 1] }
                },
                onSwipeRight = {
                    val index = PerspectiveTab.entries.indexOf(tab)
                    if (index == 0) onSwipeEdgeRight()
                    else { manageTopics = false; tab = PerspectiveTab.entries[index - 1] }
                },
            ),
        ) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val forward = PerspectiveTab.entries.indexOf(targetState) >= PerspectiveTab.entries.indexOf(initialState)
                    if (forward) {
                        (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut())
                    } else {
                        (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it } + fadeOut())
                    }
                },
                label = "perspective_tab_transition",
            ) { currentTab ->
                when (currentTab) {
                    PerspectiveTab.HOME -> PerspectiveHome(state) { tab = it }
                    PerspectiveTab.MAP -> ThoughtMapScreen(state)
                    PerspectiveTab.EXPLORE -> ExploreScreen(state, busyVideoId, viewModel)
                    PerspectiveTab.REPORT -> ReportScreen(state)
                    PerspectiveTab.RECORDS -> if (manageTopics) {
                        TopicScreen(
                            state = state,
                            setEnabled = viewModel::setTopicEnabled,
                            updateTopic = viewModel::updateTopic,
                            acceptSuggestion = viewModel::acceptTopicSuggestion,
                            dismissSuggestion = viewModel::dismissTopicSuggestion,
                            onBack = { manageTopics = false },
                        )
                    } else RecordsScreen(state, busyVideoId, viewModel) { manageTopics = true }
                }
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
private fun PerspectiveHome(state: PerspectiveState, openTab: (PerspectiveTab) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { BrainMapCard(state) }
        item {
            SectionCard("최근 사고 확장", Icons.Rounded.Map, AppCobalt, onClick = { openTab(PerspectiveTab.MAP) }) {
                val visited = state.perspectives.filter { it.status == "visited" }.sortedBy(PerspectiveEntity::visitedAt).takeLast(4)
                if (visited.isEmpty()) EmptyDataHint("아직 탐색한 관점이 없어요. 최근 영상에서 다른 관점을 열어보세요.")
                else {
                    Text(visited.joinToString("  →  ", transform = PerspectiveEntity::label), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Black, color = AppNavy)
                    val lastVisitedAt = visited.lastOrNull()?.visitedAt
                    if (lastVisitedAt != null) Text("${relativeDay(lastVisitedAt)} 새 관점이 열렸어요", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium, color = AppCobalt)
                    Text("사고 지도 보기  →", Modifier.padding(top = 10.dp), fontWeight = FontWeight.Bold, color = AppCobalt, style = MaterialTheme.typography.bodySmall)
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
                SectionCard("생각이 넓어진 순간", Icons.Rounded.Lightbulb, AppOrange, onClick = { openTab(PerspectiveTab.REPORT) }) {
                    Text("“${moment.fromLabel}에서 ${moment.toLabel}까지 생각을 넓혔어요.”", fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodyLarge)
                    Text(formatDate(moment.occurredAt), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        state.reports.firstOrNull()?.let { report ->
            item {
                SectionCard("이번 주 정보 편식", Icons.Rounded.AutoAwesome, AppOrange, onClick = { openTab(PerspectiveTab.REPORT) }) {
                    val dominant = jsonStrings(report.dominantTopicsJson).take(3)
                    val under = jsonStrings(report.underExposedPerspectivesJson).take(3)
                    if (dominant.isNotEmpty()) MiniReportRow("많이 본 세계", dominant.joinToString(" · "), AppCobalt)
                    if (under.isNotEmpty()) MiniReportRow("덜 본 세계", under.joinToString(" · "), AppOrange)
                    if (dominant.isEmpty() && under.isEmpty()) EmptyDataHint(report.summary)
                    Text("리포트 보기  →", Modifier.padding(top = 10.dp), fontWeight = FontWeight.Bold, color = AppOrange, style = MaterialTheme.typography.bodySmall)
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
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
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
                    LinearProgressIndicator(progress = { ratio }, modifier = Modifier.weight(1f).height(8.dp), color = listOf(AppCobalt, AppOrange, AppGreen)[index % 3], trackColor = Color(0xFFEAE8EE))
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
    val mapVideo = nodes.lastOrNull { it.type == "video" }?.videoId?.let { id -> state.videos.firstOrNull { it.id == id } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("사고 확장 지도", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MapLegend(AppCobalt, "본 관점", filled = true)
                MapLegend(Color(0xFFAAA7B0), "아직 안 본 관점", filled = false)
                MapLegend(AppOrange, "시청 영상", filled = true)
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
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F7FC))) {
                if (nodes.isEmpty()) Box(Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) { Text("이 주제의 탐색 경로가 아직 없어요.") }
                else Column {
                    mapVideo?.let { video ->
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            val thumbnail = video.youtubeVideoId.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }?.let { "https://img.youtube.com/vi/$it/mqdefault.jpg" }
                            if (thumbnail != null) AsyncImage(
                                model = thumbnail,
                                contentDescription = null,
                                modifier = Modifier.size(width = 74.dp, height = 44.dp).background(Color(0xFFE5E2EA), RoundedCornerShape(9.dp)),
                            ) else Box(Modifier.size(44.dp).background(AppOrange.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PlayCircle, null, tint = AppOrange) }
                            Column(Modifier.padding(start = 10.dp)) {
                                Text("이 영상에서 시작된 탐험", style = MaterialTheme.typography.labelSmall, color = AppOrange, fontWeight = FontWeight.Bold)
                                Text(video.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    ThoughtMapCanvas(nodes, edges)
                }
            }
        }
        items(nodes.reversed(), key = ThoughtNodeEntity::id) { node ->
            Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(15.dp)).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                val nodeColor = if (node.type == "video") AppOrange else if (node.status == "visited") AppCobalt else Color(0xFFBBB8C2)
                Box(Modifier.size(12.dp).background(nodeColor, CircleShape))
                Column(Modifier.padding(start = 10.dp)) {
                    Text(node.label, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                    Text(if (node.type == "video") "시청 영상" else if (node.status == "visited") "탐색한 관점" else "추천 관점", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ThoughtMapCanvas(nodes: List<ThoughtNodeEntity>, edges: List<ThoughtEdgeEntity>) {
    Canvas(Modifier.fillMaxWidth().height(320.dp).padding(16.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = minOf(size.width, size.height) * .36f
        val positions = nodes.mapIndexed { index, node ->
            val angle = (Math.PI * 2 * index / nodes.size) - Math.PI / 2
            node.id to Offset(center.x + cos(angle).toFloat() * radius, center.y + sin(angle).toFloat() * radius)
        }.toMap()
        edges.forEach { edge ->
            val from = positions[edge.fromNodeId] ?: return@forEach
            val to = positions[edge.toNodeId] ?: return@forEach
            drawLine(if (edge.type == "selected") AppCobalt else Color(0xFFD8D5DF), from, to, strokeWidth = if (edge.type == "selected") 5f else 2.5f)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(45, 44, 52); textSize = 11.dp.toPx(); textAlign = Paint.Align.CENTER }
        nodes.forEach { node ->
            val point = positions[node.id] ?: return@forEach
            val filledColor = if (node.type == "video") AppOrange else if (node.status == "visited") AppCobalt else Color.White
            val radius = if (node.type == "video") 16.dp.toPx() else if (node.status == "visited") 13.dp.toPx() else 11.dp.toPx()
            drawCircle(filledColor, radius = radius, center = point)
            if (node.status != "visited" && node.type != "video") drawCircle(Color(0xFFAAA7B0), radius = radius, center = point, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
            drawContext.canvas.nativeCanvas.drawText(node.label.take(9), point.x, point.y + 27.dp.toPx(), paint)
        }
    }
}

@Composable
private fun ExploreScreen(state: PerspectiveState, busyVideoId: String?, viewModel: PerspectiveViewModel) {
    val context = LocalContext.current
    val mediaAccessEnabled = remember(context) { context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context) }
    val latestVideo = state.videos.firstOrNull()
    val latestPerspectives = latestVideo?.let { video -> state.perspectives.filter { it.videoId == video.id && it.status == "suggested" }.take(4) }.orEmpty()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
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
                Text("설정", color = AppCobalt, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
        if (latestVideo != null) {
            item {
                Text("방금 본 영상", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                VideoCard(latestVideo, state, busyVideoId == latestVideo.id, onAnalyze = { viewModel.deepAnalyze(latestVideo.id) }, onOpen = {
                    if (latestVideo.url.isNotBlank()) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(latestVideo.url)))
                })
            }
            item {
                Text("이 영상에서 열어볼 다른 관점", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                if (latestPerspectives.isEmpty()) Text("‘다른 관점 보기’를 누르면 이 영상에서 놓친 질문 4개를 찾아드려요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else item { EmptyStateCard("YouTube 영상을 2분 이상 시청하면 여기서 다른 관점을 발견할 수 있어요.") }
        items(latestPerspectives, key = PerspectiveEntity::id) { item ->
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF6F4FA))) {
                Column(Modifier.padding(15.dp)) {
                    Text(item.label, fontWeight = FontWeight.Black, color = AppCobalt)
                    Text(item.description, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall)
                    Text(item.representativeQuestion, Modifier.padding(top = 7.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = {
                        viewModel.explorePerspective(item.id)
                        val target = "https://www.youtube.com/results?search_query=${Uri.encode(item.searchQuery)}"
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
                    }, Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Icon(Icons.Rounded.Explore, null, Modifier.size(17.dp)); Text("이 관점 탐색", Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
        val otherSuggestions = state.perspectives.filter { it.status == "suggested" && it.videoId != latestVideo?.id }.distinctBy(PerspectiveEntity::label).take(6)
        if (otherSuggestions.isNotEmpty()) item { Text("이전 영상에서 아직 안 본 관점", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black) }
        items(otherSuggestions, key = { "older:${it.id}" }) { item ->
            Row(
                Modifier.fillMaxWidth().clickable {
                    viewModel.explorePerspective(item.id)
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(item.searchQuery)}")))
                }.background(Color.White, RoundedCornerShape(16.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).background(AppGreen, CircleShape))
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(item.label, fontWeight = FontWeight.Black)
                    Text(item.representativeQuestion, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("탐색 →", color = AppCobalt, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RecordsScreen(state: PerspectiveState, busyVideoId: String?, viewModel: PerspectiveViewModel, openTopics: () -> Unit) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("시청 기록", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    if (state.videos.isNotEmpty()) Text("최근 자동 감지된 영상 ${state.videos.size}개", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = openTopics) { Text("주제 관리") }
            }
        }
        if (state.videos.isEmpty()) item { EmptyStateCard("YouTube에서 영상을 재생하면 시청 기록이 자동으로 나타나요.") }
        items(state.videos.take(30), key = WatchedVideoEntity::id) { video ->
            VideoCard(video, state, busyVideoId == video.id, onAnalyze = { viewModel.deepAnalyze(video.id) }, onOpen = {
                if (video.url.isNotBlank()) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(video.url)))
            })
        }
    }
}

@Composable
private fun VideoCard(video: WatchedVideoEntity, state: PerspectiveState, busy: Boolean, onAnalyze: () -> Unit, onOpen: () -> Unit) {
    val analysis = state.analyses.firstOrNull { it.videoId == video.id }
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.PlayCircle, null, tint = AppOrange)
                Column(Modifier.weight(1f).padding(start = 9.dp)) {
                    Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Black)
                    Text(listOf(video.channelName, formatTime(video.watchedAt)).filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (video.url.isNotBlank()) Icon(Icons.Rounded.OpenInNew, "YouTube 열기", Modifier.clickable(onClick = onOpen), tint = AppCobalt)
            }
            analysis?.let { Text(it.mainClaim, Modifier.padding(top = 9.dp), style = MaterialTheme.typography.bodySmall, color = AppNavy) }
            if (video.url.isBlank()) Text("MediaSession 자동 감지 · 영상 ID 미제공 시 공개 메타데이터를 기준으로 분석해요.", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelSmall, color = AppOrange)
            OutlinedButton(onClick = onAnalyze, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp))
                Text(if (analysis == null) "다른 관점 보기" else "캐시된 분석 보기 · 다시 확인", Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun ReportScreen(state: PerspectiveState) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("정보 편식 리포트", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) }
        state.reports.firstOrNull()?.let { report ->
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("이번 주", color = AppCobalt, fontWeight = FontWeight.Bold)
                        Text(report.summary, Modifier.padding(top = 7.dp), style = MaterialTheme.typography.bodyMedium)
                        ReportDistribution(state)
                        ReportLabels("많이 본 세계", report.dominantTopicsJson, AppCobalt)
                        ReportLabels("실제로 탐색한 관점", report.dominantPerspectivesJson, AppGreen)
                        ReportLabels("상대적으로 덜 본 세계", report.underExposedPerspectivesJson, AppOrange)
                    }
                }
            }
        } ?: item { Text("이번 주 리포트를 만들 기록이 아직 없어요.") }
        item { Text("생각이 넓어진 순간", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black) }
        if (state.moments.isEmpty()) item { Text("서로 다른 관점을 두 번 이상 탐색하면 이곳에 순간이 기록됩니다.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(state.moments, key = { it.id }) { moment ->
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8EC))) {
                Row(Modifier.padding(15.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Lightbulb, null, tint = AppOrange)
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(moment.title, fontWeight = FontWeight.Black)
                        Text(moment.reason, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall)
                        Text(formatTime(moment.occurredAt), Modifier.padding(top = 5.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
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
        if (state.topicSuggestions.isNotEmpty()) item { Text("등록 대기", fontWeight = FontWeight.Black, color = AppOrange) }
        items(state.topicSuggestions, key = { "suggestion:${it.videoId}" }) { suggestion ->
            val video = state.videos.firstOrNull { it.id == suggestion.videoId }
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8EC))) {
                Column(Modifier.fillMaxWidth().padding(15.dp)) {
                    Text(suggestion.proposedName, fontWeight = FontWeight.Black, color = AppOrange)
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
        if (state.topics.isNotEmpty()) item { Text("등록한 주제", fontWeight = FontWeight.Black, color = AppCobalt) }
        items(state.topics, key = { it.id }) { topic ->
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
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
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
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
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
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
private fun ReportDistribution(state: PerspectiveState) {
    val from = remember(state.videos, state.videoTopics) { System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1_000 }
    val videoIds = remember(state.videos, from) {
        state.videos.filter { it.watchedAt >= from && it.watchedSec >= 120 }.mapTo(mutableSetOf(), WatchedVideoEntity::id)
    }
    val counts = state.videoTopics.filter { it.videoId in videoIds }.groupingBy { it.topicId }.eachCount()
    val values = state.topics.mapNotNull { topic -> (counts[topic.id] ?: 0).takeIf { it > 0 }?.let { topic.name to it } }.sortedByDescending { it.second }.take(4)
    val total = values.sumOf { it.second }.coerceAtLeast(1)
    if (values.isNotEmpty()) {
        Column(Modifier.padding(top = 14.dp).background(Color(0xFFF7F6FA), RoundedCornerShape(15.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            values.forEachIndexed { index, (name, count) ->
                val ratio = count.toFloat() / total
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, Modifier.size(width = 66.dp, height = 18.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(progress = { ratio }, Modifier.weight(1f).height(7.dp), color = listOf(AppCobalt, AppGreen, AppOrange)[index % 3], trackColor = Color(0xFFE5E2EA))
                    Text("${(ratio * 100).toInt()}%", Modifier.padding(start = 7.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ReportLabels(title: String, json: String, color: Color) {
    val values = remember(json) { jsonStrings(json) }
    if (values.isNotEmpty()) {
        Text(title, Modifier.padding(top = 14.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(values.joinToString(" · "), Modifier.padding(top = 3.dp), color = color, fontWeight = FontWeight.Bold)
    }
}

private fun jsonStrings(value: String): List<String> = runCatching { JSONArray(value).let { array -> List(array.length()) { array.optString(it) }.filter(String::isNotBlank) } }.getOrDefault(emptyList())
private fun formatTime(timestamp: Long): String = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M월 d일 HH:mm"))
private fun formatDate(timestamp: Long): String = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M월 d일"))
private fun relativeDay(timestamp: Long): String {
    val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = java.time.LocalDate.now()
    val days = java.time.temporal.ChronoUnit.DAYS.between(date, today).coerceAtLeast(0)
    return when (days) { 0L -> "오늘"; 1L -> "어제"; else -> "${days}일 전" }
}
