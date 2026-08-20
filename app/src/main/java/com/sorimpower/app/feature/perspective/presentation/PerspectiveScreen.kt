package com.sorimpower.app.feature.perspective.presentation

import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sorimpower.app.core.ui.AppCobalt
import com.sorimpower.app.core.ui.AppGreen
import com.sorimpower.app.core.ui.AppOrange
import com.sorimpower.app.feature.perspective.data.*
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val MEANINGFUL_WATCH_SECONDS = 300L
private enum class YoutubeTab(val label: String) { BRAIN("뇌 지도"), CHANGE("관심 변화") }
private enum class BrainPeriod(val label: String, val days: Long) { WEEK("이번 주", 7), MONTH("이번 달", 31), YEAR("올해", 366) }
private enum class ChangePeriod(val label: String) { MONTH("월별"), YEAR("연도별") }
private data class TopicExposure(val topicId: String, val label: String, val count: Int, val percentage: Int)
private data class ChangePeriodData(val label: String, val videos: List<WatchedVideoEntity>, val exposures: List<TopicExposure>)
private data class PeriodTopicSelection(val periodLabel: String, val topic: TopicExposure, val videos: List<WatchedVideoEntity>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerspectiveScreen(
    padding: PaddingValues,
    viewModel: PerspectiveViewModel,
    openExploreRequest: Int = 0,
    openTopicsRequest: Int = 0,
    onSwipeEdgeLeft: () -> Unit = {},
    onSwipeEdgeRight: () -> Unit = {},
    sharedYoutubeUrl: String? = null,
    onSharedYoutubeUrlConsumed: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busyVideoId by viewModel.busyVideoId.collectAsStateWithLifecycle()
    val interestComment by viewModel.interestComment.collectAsStateWithLifecycle()
    val interestCommentLoading by viewModel.interestCommentLoading.collectAsStateWithLifecycle()
    val interestProfile by viewModel.interestProfile.collectAsStateWithLifecycle()
    val crossTopicVideos by viewModel.crossTopicVideos.collectAsStateWithLifecycle()
    val crossTopicLoading by viewModel.crossTopicLoading.collectAsStateWithLifecycle()
    val watchedVideoPlayback by viewModel.watchedVideoPlayback.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(YoutubeTab.BRAIN) }
    var selectedTopicId by remember { mutableStateOf<String?>(null) }
    var showTopicSuggestion by remember { mutableStateOf(false) }
    var showDirectLinkInput by remember { mutableStateOf(false) }
    var showInterestProfile by remember { mutableStateOf(false) }
    val nestedHorizontalGesture = remember { mutableStateOf(false) }
    LaunchedEffect(openExploreRequest, openTopicsRequest) {
        if (openExploreRequest > 0 || openTopicsRequest > 0) { tab = YoutubeTab.BRAIN; selectedTopicId = null }
        if (openTopicsRequest > 0) showTopicSuggestion = true
    }
    BackHandler(selectedTopicId != null) { selectedTopicId = null }

    Column(Modifier.fillMaxSize().padding(padding)) {
        if (selectedTopicId == null) {
            PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                YoutubeTab.entries.forEach { item ->
                    Tab(selected = tab == item, onClick = { tab = item }, text = { Text(item.label, fontWeight = FontWeight.Bold) })
                }
            }
        }
        message?.let {
            Text(it, Modifier.fillMaxWidth().clickable { viewModel.clearMessage() }.background(MaterialTheme.colorScheme.primaryContainer).padding(12.dp), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
        }
        Box(Modifier.weight(1f).horizontalSwipe(
            nestedGesture = nestedHorizontalGesture,
            onSwipeLeft = {
                if (selectedTopicId == null) {
                    if (tab == YoutubeTab.BRAIN) tab = YoutubeTab.CHANGE else onSwipeEdgeLeft()
                }
            },
            onSwipeRight = {
                if (selectedTopicId == null) {
                    if (tab == YoutubeTab.CHANGE) tab = YoutubeTab.BRAIN else onSwipeEdgeRight()
                }
            },
        )) {
            selectedTopicId?.let {
                TopicThoughtMap(
                    state = state,
                    topicId = it,
                    onBack = { selectedTopicId = null },
                    onRecommendationOpen = viewModel::markRecommendedPerspectiveOpened,
                    onDeepAnalyze = viewModel::deepAnalyze,
                    busyVideoId = busyVideoId,
                )
            }
                ?: if (tab == YoutubeTab.BRAIN) BrainMapScreen(
                    state = state,
                    interestComment = interestComment,
                    interestCommentLoading = interestCommentLoading,
                    interestProfile = interestProfile,
                    onLoadInterestComment = viewModel::loadInterestComment,
                    onEditInterestProfile = { showInterestProfile = true },
                    crossTopicVideos = crossTopicVideos,
                    crossTopicLoading = crossTopicLoading,
                    onLoadCrossTopicVideos = viewModel::loadCrossTopicVideos,
                    onRecommendationPointerDown = { nestedHorizontalGesture.value = true },
                    onDirectLinkInput = { showDirectLinkInput = true },
                    onTopicClick = { selectedTopicId = it },
                ) else InterestChangeScreen(state, watchedVideoPlayback, viewModel::resolveWatchedVideo)
        }
    }
    if (showDirectLinkInput) {
        DirectYoutubeUrlDialog(
            busy = busyVideoId == "shared",
            onDismiss = { showDirectLinkInput = false },
            onAnalyze = { url -> viewModel.analyzeSharedUrl(url); showDirectLinkInput = false },
        )
    }
    if (showInterestProfile) {
        InterestProfileDialog(
            profile = interestProfile,
            onDismiss = { showInterestProfile = false },
            onSave = { profile ->
                viewModel.saveInterestProfile(profile)
                showInterestProfile = false
            },
        )
    }
    sharedYoutubeUrl?.let { url ->
        SharedYoutubeDialog(
            url = url,
            busy = busyVideoId == "shared",
            onDismiss = onSharedYoutubeUrlConsumed,
            onAnalyze = { viewModel.analyzeSharedUrl(url); onSharedYoutubeUrlConsumed() },
        )
    }
    if (showTopicSuggestion) {
        val suggestion = state.topicSuggestions.firstOrNull()
        if (suggestion != null) {
            TopicSuggestionDialog(
                suggestion = suggestion,
                video = state.videos.firstOrNull { it.id == suggestion.videoId },
                onAccept = {
                    viewModel.acceptTopicSuggestion(suggestion.videoId)
                    showTopicSuggestion = false
                },
                onDismiss = {
                    viewModel.dismissTopicSuggestion(suggestion.videoId)
                    showTopicSuggestion = false
                },
            )
        } else if (state.loaded) {
            LaunchedEffect(Unit) { showTopicSuggestion = false }
        }
    }
}

@Composable
private fun DirectYoutubeUrlDialog(busy: Boolean, onDismiss: () -> Unit, onAnalyze: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    val valid = remember(url) { Regex("(?:youtube\\.com/(?:watch\\?v=|shorts/)|youtu\\.be/)[A-Za-z0-9_-]{11}", RegexOption.IGNORE_CASE).containsMatchIn(url.trim()) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("YouTube 링크 직접 분석", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("영상 주소를 붙여 넣으면 주제와 구체적인 관점을 분석해요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("YouTube 영상 링크") },
                    placeholder = { Text("https://youtu.be/...") },
                    leadingIcon = { Icon(Icons.Rounded.Link, null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    isError = url.isNotBlank() && !valid,
                    supportingText = if (url.isNotBlank() && !valid) ({ Text("올바른 YouTube 영상 링크를 입력해 주세요.") }) else null,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onAnalyze(url.trim()) }, enabled = valid && !busy) {
                if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(17.dp))
                Text("분석하기", Modifier.padding(start = 7.dp))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("취소") } },
    )
}

@Composable
private fun TopicSuggestionDialog(
    suggestion: TopicSuggestionEntity,
    video: WatchedVideoEntity?,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("‘${suggestion.proposedName}’ 주제로 정리할까요?", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                video?.title?.takeIf(String::isNotBlank)?.let { Text(it, fontWeight = FontWeight.Bold) }
                suggestion.description.takeIf(String::isNotBlank)?.let { Text(it) }
                Text("등록하면 이 영상이 뇌 지도와 관심 변화에 반영돼요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = onAccept) { Text("주제 등록") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("건너뛰기") } },
    )
}

@Composable
private fun SharedYoutubeDialog(url: String, busy: Boolean, onDismiss: () -> Unit, onAnalyze: () -> Unit) {
    val videoId = remember(url) { Regex("(?:v=|youtu\\.be/|shorts/)([A-Za-z0-9_-]{11})").find(url)?.groupValues?.getOrNull(1) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("이 영상의 다른 관점을 찾아볼까요?", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                videoId?.let { AsyncImage(model = "https://i.ytimg.com/vi/$it/hqdefault.jpg", contentDescription = "공유한 YouTube 영상", modifier = Modifier.fillMaxWidth().height(180.dp)) }
                Text("공유한 YouTube 영상", fontWeight = FontWeight.Bold)
                Text("분석하면 구체적인 관점과 다음 질문이 생각 지도에 추가돼요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = onAnalyze, enabled = !busy) {
                if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(17.dp))
                Text("분석하기", Modifier.padding(start = 7.dp))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("취소") } },
    )
}

@Composable
private fun BrainMapScreen(
    state: PerspectiveState,
    interestComment: InterestAiComment?,
    interestCommentLoading: Boolean,
    interestProfile: InterestProfile,
    onLoadInterestComment: (Long, Int, Boolean) -> Unit,
    onEditInterestProfile: () -> Unit,
    crossTopicVideos: List<CrossTopicVideoRecommendation>,
    crossTopicLoading: Boolean,
    onLoadCrossTopicVideos: (List<String>, Boolean) -> Unit,
    onRecommendationPointerDown: () -> Unit,
    onDirectLinkInput: () -> Unit,
    onTopicClick: (String) -> Unit,
) {
    var period by remember { mutableStateOf(BrainPeriod.MONTH) }
    val exposures = remember(state.videos, state.videoTopics, state.topics, period) { state.topicExposure(period.days) }
    val dataVersion = remember(exposures) { exposures.fold(0) { acc, item -> acc * 31 + item.topicId.hashCode() + item.count } }
    LaunchedEffect(period, dataVersion) {
        if (exposures.isNotEmpty()) onLoadInterestComment(period.days, dataVersion, false)
    }
    LaunchedEffect(exposures) {
        onLoadCrossTopicVideos(exposures.map(TopicExposure::label), false)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("나의 관심 지도", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    TextButton(onClick = onDirectLinkInput) {
                        Icon(Icons.Rounded.AddLink, null, Modifier.size(18.dp))
                        Text("링크 분석", Modifier.padding(start = 5.dp), fontWeight = FontWeight.Bold)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    BrainPeriod.entries.forEach { item -> FilterChip(selected = period == item, onClick = { period = item }, label = { Text(item.label) }, modifier = Modifier.padding(start = 5.dp)) }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFD)), shape = RoundedCornerShape(28.dp), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (exposures.isEmpty()) EmptyBrainMap() else BrainCanvas(exposures.take(7), onTopicClick)
                    Text(
                        if (exposures.isEmpty()) "5분 이상 본 영상부터 관심 지도에 반영돼요." else "${period.label} ${exposures.sumOf { it.count }}개 영상을 의미 있게 시청했어요.",
                        Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (exposures.isNotEmpty()) Text("영역을 눌러 생각 지도를 확인해보세요.", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (exposures.isNotEmpty()) item {
            AiInterestCommentCard(
                comment = interestComment,
                loading = interestCommentLoading,
                profile = interestProfile,
                onRefresh = { onLoadInterestComment(period.days, dataVersion, true) },
                onEditProfile = onEditInterestProfile,
            )
        }
        item { CrossTopicCard(crossTopicVideos, crossTopicLoading, onRecommendationPointerDown) { onLoadCrossTopicVideos(exposures.map(TopicExposure::label), true) } }
    }
}

@Composable
private fun AiInterestCommentCard(
    comment: InterestAiComment?,
    loading: Boolean,
    profile: InterestProfile,
    onRefresh: () -> Unit,
    onEditProfile: () -> Unit,
) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F1FF))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = Color(0xFFE2DAFF)) {
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.padding(8.dp).size(18.dp), tint = Color(0xFF6652B8))
                }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text("나의 관심 주제 분석", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                    Text("영상 기록으로 읽은 요즘의 흥미 포인트예요", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onRefresh, enabled = !loading) { Icon(Icons.Rounded.Refresh, "다시 분석") }
            }
            TextButton(onClick = onEditProfile, contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.Rounded.Tune, null, Modifier.size(15.dp))
                Text(if (profile.isConfigured) "맞춤 기준 수정" else "연령대·성별로 더 맞춰보기", Modifier.padding(start = 5.dp), style = MaterialTheme.typography.labelMedium)
            }
            when {
                loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("관심 흐름을 분석하고 있어요…", Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodySmall)
                }
                comment != null -> {
                    Text(comment.headline, fontWeight = FontWeight.Black, color = Color(0xFF55429E))
                    Text(comment.summary, style = MaterialTheme.typography.bodyMedium)
                    comment.observations.forEach { Text("✦ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                else -> Text("분석 코멘트를 준비하고 있어요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InterestProfileDialog(profile: InterestProfile, onDismiss: () -> Unit, onSave: (InterestProfile) -> Unit) {
    var ageGroup by remember(profile) { mutableStateOf(profile.ageGroup) }
    var gender by remember(profile) { mutableStateOf(profile.gender) }
    var lifeInterests by remember(profile) { mutableStateOf(profile.lifeInterests) }
    var viewingPurpose by remember(profile) { mutableStateOf(profile.viewingPurpose) }
    var analysisTone by remember(profile) { mutableStateOf(profile.analysisTone) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("관심 분석 맞춤 기준", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("선택한 기준은 이 카드의 가벼운 관심 해석에만 보조적으로 쓰고, 기기에만 저장해요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("연령대", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("10대", "20대", "30대", "40대", "50대+").forEach { option ->
                        FilterChip(selected = ageGroup == option, onClick = { ageGroup = if (ageGroup == option) "" else option }, label = { Text(option) })
                    }
                }
                Text("성별", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("여성", "남성", "직접 밝히지 않음").forEach { option ->
                        FilterChip(selected = gender == option, onClick = { gender = if (gender == option) "" else option }, label = { Text(option) })
                    }
                }
                Text("생활 관심사 · 복수 선택", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("커리어·공부", "생활·집", "건강·운동", "돈·소비", "취미·여행").forEach { option ->
                        FilterChip(selected = option in lifeInterests, onClick = {
                            lifeInterests = if (option in lifeInterests) lifeInterests - option else lifeInterests + option
                        }, label = { Text(option) })
                    }
                }
                Text("영상을 보는 목적", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("실용 정보 찾기", "새것 배우기", "휴식·재미").forEach { option ->
                        FilterChip(selected = viewingPurpose == option, onClick = { viewingPurpose = if (viewingPurpose == option) "" else option }, label = { Text(option) })
                    }
                }
                Text("분석 말투", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("친구처럼 재밌게", "현실적인 분석", "짧고 핵심만").forEach { option ->
                        FilterChip(selected = analysisTone == option, onClick = { analysisTone = if (analysisTone == option) "" else option }, label = { Text(option) })
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(InterestProfile(ageGroup, gender, lifeInterests, viewingPurpose, analysisTone)) }) { Text("저장하고 다시 분석") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun BrainCanvas(exposures: List<TopicExposure>, onTopicClick: (String) -> Unit) {
    val visible = exposures.take(7)
    val colors = listOf(Color(0xFFE5F3EE), Color(0xFFEEEAFB), Color(0xFFF9E8EF), Color(0xFFF8EFD9), Color(0xFFE5F0F8), Color(0xFFF0EAF7), Color(0xFFE8F4F1))
    val outline = Color(0xFF64748B)
    val brainScale = .96f
    // 관심 영역을 아래·왼쪽으로 옮겨 뒤통수 외곽선과의 여백을 유지한다.
    val brainPivotX = 1.65f
    val brainPivotY = .10f
    fun layout(count: Int): List<FloatArray> = when (count) {
        1 -> listOf(floatArrayOf(.27f, .09f, .92f, .71f))
        2 -> listOf(floatArrayOf(.27f, .09f, .59f, .71f), floatArrayOf(.59f, .09f, .92f, .71f))
        3 -> listOf(floatArrayOf(.27f, .09f, .59f, .40f), floatArrayOf(.59f, .09f, .92f, .40f), floatArrayOf(.27f, .40f, .92f, .71f))
        4 -> listOf(floatArrayOf(.27f, .09f, .59f, .40f), floatArrayOf(.59f, .09f, .92f, .40f), floatArrayOf(.27f, .40f, .58f, .71f), floatArrayOf(.58f, .40f, .92f, .71f))
        5 -> listOf(floatArrayOf(.27f, .09f, .49f, .40f), floatArrayOf(.49f, .09f, .71f, .40f), floatArrayOf(.71f, .09f, .92f, .40f), floatArrayOf(.27f, .40f, .59f, .71f), floatArrayOf(.59f, .40f, .92f, .71f))
        6 -> listOf(floatArrayOf(.27f, .09f, .49f, .40f), floatArrayOf(.49f, .09f, .71f, .40f), floatArrayOf(.71f, .09f, .92f, .40f), floatArrayOf(.27f, .40f, .49f, .71f), floatArrayOf(.49f, .40f, .71f, .71f), floatArrayOf(.71f, .40f, .92f, .71f))
        else -> listOf(floatArrayOf(.27f, .09f, .49f, .31f), floatArrayOf(.49f, .09f, .71f, .31f), floatArrayOf(.71f, .09f, .92f, .31f), floatArrayOf(.27f, .31f, .59f, .51f), floatArrayOf(.59f, .31f, .92f, .51f), floatArrayOf(.27f, .51f, .59f, .71f), floatArrayOf(.59f, .51f, .92f, .71f))
    }
    val cells = layout(visible.size)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().height(330.dp).pointerInput(visible) {
            detectTapGestures { tap ->
                val rawNx = tap.x / size.width
                val rawNy = tap.y / size.height
                val nx = (rawNx - brainPivotX) / brainScale + brainPivotX
                val ny = (rawNy - brainPivotY) / brainScale + brainPivotY
                if (visible.size == 4) {
                    val centerDx = (nx - .585f) / .155f
                    val centerDy = (ny - .415f) / .12f
                    val index = when {
                        centerDx * centerDx + centerDy * centerDy <= 1f -> 0
                        ny < .385f && nx < .59f -> 1
                        ny < .385f -> 2
                        else -> 3
                    }
                    onTopicClick(visible[index].topicId)
                    return@detectTapGestures
                }
                cells.forEachIndexed { index, cell ->
                    if (nx in cell[0]..cell[2] && ny in cell[1]..cell[3]) {
                        onTopicClick(visible[index].topicId)
                        return@detectTapGestures
                    }
                }
            }
        }) {
            val head = Path().apply {
                moveTo(size.width * .39f, size.height * .97f)
                cubicTo(size.width * .39f, size.height * .87f, size.width * .35f, size.height * .79f, size.width * .30f, size.height * .78f)
                cubicTo(size.width * .20f, size.height * .80f, size.width * .17f, size.height * .72f, size.width * .18f, size.height * .64f)
                lineTo(size.width * .18f, size.height * .57f)
                cubicTo(size.width * .14f, size.height * .56f, size.width * .12f, size.height * .53f, size.width * .16f, size.height * .50f)
                cubicTo(size.width * .11f, size.height * .49f, size.width * .09f, size.height * .46f, size.width * .12f, size.height * .43f)
                lineTo(size.width * .21f, size.height * .32f)
                cubicTo(size.width * .21f, size.height * .15f, size.width * .35f, size.height * .05f, size.width * .58f, size.height * .04f)
                cubicTo(size.width * .82f, size.height * .03f, size.width * .95f, size.height * .15f, size.width * .96f, size.height * .36f)
                cubicTo(size.width * .98f, size.height * .55f, size.width * .90f, size.height * .66f, size.width * .82f, size.height * .72f)
                cubicTo(size.width * .75f, size.height * .78f, size.width * .74f, size.height * .87f, size.width * .77f, size.height * .96f)
            }
            val brain = Path().apply {
                moveTo(size.width * .25f, size.height * .35f)
                cubicTo(size.width * .24f, size.height * .18f, size.width * .38f, size.height * .08f, size.width * .58f, size.height * .08f)
                cubicTo(size.width * .80f, size.height * .07f, size.width * .92f, size.height * .18f, size.width * .92f, size.height * .36f)
                cubicTo(size.width * .93f, size.height * .52f, size.width * .85f, size.height * .68f, size.width * .67f, size.height * .72f)
                cubicTo(size.width * .50f, size.height * .74f, size.width * .35f, size.height * .60f, size.width * .29f, size.height * .49f)
                cubicTo(size.width * .25f, size.height * .45f, size.width * .23f, size.height * .40f, size.width * .25f, size.height * .35f)
                close()
            }
            drawPath(head, outline, style = Stroke(2.2.dp.toPx()))
            scale(brainScale, pivot = Offset(size.width * brainPivotX, size.height * brainPivotY)) {
                clipPath(brain) {
                val separator = Color.White.copy(alpha = .96f)
                if (visible.size == 4) {
                    drawRect(colors[3], topLeft = Offset(size.width * .24f, size.height * .07f), size = Size(size.width * .71f, size.height * .67f))
                    val upperLeft = Path().apply {
                        moveTo(size.width * .23f, size.height * .07f)
                        lineTo(size.width * .60f, size.height * .07f)
                        cubicTo(size.width * .58f, size.height * .18f, size.width * .61f, size.height * .28f, size.width * .55f, size.height * .39f)
                        cubicTo(size.width * .42f, size.height * .37f, size.width * .31f, size.height * .42f, size.width * .23f, size.height * .39f)
                        close()
                    }
                    val upperRight = Path().apply {
                        moveTo(size.width * .60f, size.height * .07f)
                        lineTo(size.width * .95f, size.height * .07f)
                        lineTo(size.width * .95f, size.height * .43f)
                        cubicTo(size.width * .82f, size.height * .40f, size.width * .73f, size.height * .37f, size.width * .63f, size.height * .39f)
                        cubicTo(size.width * .58f, size.height * .27f, size.width * .62f, size.height * .17f, size.width * .60f, size.height * .07f)
                        close()
                    }
                    val center = Path().apply {
                        moveTo(size.width * .49f, size.height * .30f)
                        cubicTo(size.width * .56f, size.height * .26f, size.width * .68f, size.height * .28f, size.width * .73f, size.height * .34f)
                        cubicTo(size.width * .78f, size.height * .40f, size.width * .74f, size.height * .50f, size.width * .67f, size.height * .53f)
                        cubicTo(size.width * .58f, size.height * .57f, size.width * .45f, size.height * .53f, size.width * .43f, size.height * .45f)
                        cubicTo(size.width * .40f, size.height * .38f, size.width * .43f, size.height * .33f, size.width * .49f, size.height * .30f)
                        close()
                    }
                    drawPath(upperLeft, colors[1])
                    drawPath(upperRight, colors[2])
                    drawPath(upperLeft, separator, style = Stroke(5.dp.toPx()))
                    drawPath(upperRight, separator, style = Stroke(5.dp.toPx()))
                    drawPath(center, colors[0])
                    drawPath(center, separator, style = Stroke(5.dp.toPx()))
                } else {
                    cells.forEachIndexed { index, cell ->
                        drawRect(
                            color = colors[index],
                            topLeft = Offset(size.width * cell[0], size.height * cell[1]),
                            size = Size(size.width * (cell[2] - cell[0]), size.height * (cell[3] - cell[1])),
                        )
                    }
                }
                fun horizontal(y: Float) {
                    val path = Path().apply {
                        moveTo(size.width * .24f, size.height * y)
                        cubicTo(size.width * .42f, size.height * (y - .018f), size.width * .70f, size.height * (y + .018f), size.width * .94f, size.height * y)
                    }
                    drawPath(path, separator, style = Stroke(5.dp.toPx()))
                }
                fun vertical(x: Float, top: Float, bottom: Float, bend: Float) {
                    val path = Path().apply {
                        moveTo(size.width * x, size.height * top)
                        cubicTo(size.width * (x + bend), size.height * (top + (bottom - top) * .32f), size.width * (x - bend), size.height * (top + (bottom - top) * .68f), size.width * x, size.height * bottom)
                    }
                    drawPath(path, separator, style = Stroke(5.dp.toPx()))
                }
                when (visible.size) {
                    2 -> vertical(.59f, .07f, .73f, .025f)
                    3 -> { horizontal(.40f); vertical(.59f, .07f, .41f, .02f) }
                    4 -> Unit
                    5 -> { horizontal(.40f); vertical(.49f, .07f, .41f, .015f); vertical(.71f, .07f, .41f, -.015f); vertical(.59f, .39f, .73f, .02f) }
                    6 -> { horizontal(.40f); vertical(.49f, .07f, .73f, .015f); vertical(.71f, .07f, .73f, -.015f) }
                    7 -> { horizontal(.31f); horizontal(.51f); vertical(.49f, .07f, .32f, .012f); vertical(.71f, .07f, .32f, -.012f); vertical(.59f, .30f, .73f, .018f) }
                }
                }
                drawPath(brain, outline, style = Stroke(1.8.dp.toPx()))
                visible.forEachIndexed { index, exposure ->
                    val cell = cells[index]
                    val specialCenters = listOf(.585f to .415f, .405f to .205f, .745f to .205f, .645f to .615f)
                    val center = if (visible.size == 4) {
                        val point = specialCenters[index]
                        Offset(size.width * point.first, size.height * point.second)
                    } else Offset(size.width * (cell[0] + cell[2]) / 2f, size.height * (cell[1] + cell[3]) / 2f)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.rgb(44, 45, 52)
                        textAlign = Paint.Align.CENTER
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                        textSize = (if (visible.size <= 4) 12.dp else 10.dp).toPx()
                    }
                    drawContext.canvas.nativeCanvas.drawText(exposure.label.take(if (visible.size <= 4) 6 else 4), center.x, center.y - 2.dp.toPx(), paint)
                    paint.textSize = 9.dp.toPx()
                    drawContext.canvas.nativeCanvas.drawText("${exposure.percentage}%", center.x, center.y + 13.dp.toPx(), paint)
                }
            }
        }
        visible.chunked(2).forEach { rowItems ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                rowItems.forEach { exposure ->
                    val index = visible.indexOf(exposure)
                    Row(Modifier.weight(1f).clickable { onTopicClick(exposure.topicId) }, verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(colors[index], CircleShape))
                        Text(exposure.label, Modifier.weight(1f).padding(start = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("${exposure.percentage}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EmptyBrainMap() {
    Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Psychology, null, Modifier.size(112.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = .22f))
            Text("아직 그려진 관심사가 없어요", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CrossTopicCard(recommendations: List<CrossTopicVideoRecommendation>, loading: Boolean, onHorizontalPointerDown: () -> Unit, onRefresh: () -> Unit) {
    val context = LocalContext.current
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F7F2))) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Spa, null, tint = AppGreen)
                Text("요즘 안 보던 쪽으로", Modifier.weight(1f).padding(start = 8.dp), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onRefresh, enabled = !loading) { Icon(Icons.Rounded.Refresh, "추천 새로고침") }
            }
            Text("검색 화면이 아니라, 바로 볼 수 있는 실제 영상이에요.", Modifier.padding(top = 3.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                loading -> Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("추천 영상을 고르고 있어요…", Modifier.padding(start = 9.dp), style = MaterialTheme.typography.bodySmall)
                }
                recommendations.isEmpty() -> Text("추천 영상을 찾지 못했어요. 새로고침해 주세요.", Modifier.padding(top = 14.dp), style = MaterialTheme.typography.bodySmall)
                else -> LazyRow(
                    Modifier.padding(top = 12.dp).markNestedHorizontalGesture(onHorizontalPointerDown),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(recommendations, key = { "${it.topic}:${it.video.videoId}" }) { recommendation ->
                        Card(
                            modifier = Modifier.width(220.dp).clickable { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(recommendation.video.url))) },
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                        ) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                AsyncImage(model = recommendation.video.thumbnailUrl, contentDescription = "${recommendation.video.title} 썸네일", modifier = Modifier.fillMaxWidth().height(108.dp))
                                Text(recommendation.topic, style = MaterialTheme.typography.labelSmall, color = AppGreen, fontWeight = FontWeight.Bold)
                                Text(recommendation.video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Text(recommendation.video.channelName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicThoughtMap(
    state: PerspectiveState,
    topicId: String,
    onBack: () -> Unit,
    onRecommendationOpen: (String) -> Unit,
    onDeepAnalyze: (String, Boolean) -> Unit,
    busyVideoId: String?,
) {
    val topic = state.topics.firstOrNull { it.id == topicId }
    val nodes = state.nodes.filter { it.topicId == topicId && it.type == "perspective" }
    val linkedVideoIds = state.videoTopics.filter { it.topicId == topicId }.mapTo(hashSetOf()) { it.videoId }
    val sourceVideos = state.videos.filter { it.id in linkedVideoIds }.sortedByDescending { it.watchedAt }
    var selectedNode by remember(topicId) { mutableStateOf<ThoughtNodeEntity?>(null) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.Rounded.ArrowBack, "뇌 지도로") }
            Column { Text("${topic?.name ?: "기타"} 생각 지도", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black); Text("관점을 눌러 근거와 다음 질문을 확인하세요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (nodes.isEmpty()) Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.AccountTree, null, Modifier.size(50.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = .48f))
                Text(
                    if (sourceVideos.isEmpty()) "이 주제에 연결된 시청 영상을 찾지 못했어요."
                    else "시청 기록은 있지만, 아직 생각 지도를 만들지 않았어요.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                sourceVideos.firstOrNull()?.let { video ->
                    Text("‘${video.title.take(32)}’에서 시작해볼까요?", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = { onDeepAnalyze(video.id, false) },
                        enabled = busyVideoId == null,
                    ) {
                        if (busyVideoId == video.id) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
                        Text(if (busyVideoId == video.id) "생각 지도 만드는 중…" else "이 영상으로 생각 지도 만들기", Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        else {
            ThoughtCanvas(topic?.name ?: "기타", nodes) { selectedNode = it }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { LegendDot(AppGreen, "내가 본 관점"); Spacer(Modifier.size(20.dp)); LegendDot(Color.Gray, "추천 관점") }
        }
    }
    selectedNode?.let { node -> PerspectiveDialog(node, state.perspectives.firstOrNull { it.id == node.perspectiveId }, state, { selectedNode = null }, onRecommendationOpen) }
}

@Composable
private fun ThoughtCanvas(topicName: String, nodes: List<ThoughtNodeEntity>, onNodeClick: (ThoughtNodeEntity) -> Unit) {
    val visible = nodes.take(8)
    val lineColor = MaterialTheme.colorScheme.outline
    val primary = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(460.dp).padding(12.dp).pointerInput(visible) {
        detectTapGestures { tap ->
            val center = Offset(size.width / 2f, size.height / 2f)
            visible.forEachIndexed { index, node ->
                val angle = Math.PI * 2 * index / visible.size - Math.PI / 2
                val point = center + Offset((cos(angle) * size.width * .34).toFloat(), (sin(angle) * size.height * .34).toFloat())
                if ((tap - point).getDistance() <= 52.dp.toPx()) onNodeClick(node)
            }
        }
    }) {
        val center = Offset(size.width / 2f, size.height / 2f)
        visible.forEachIndexed { index, node ->
            val angle = Math.PI * 2 * index / visible.size - Math.PI / 2
            val point = center + Offset((cos(angle) * size.width * .34).toFloat(), (sin(angle) * size.height * .34).toFloat())
            drawLine(lineColor.copy(alpha = .28f), center, point, 3f)
            val visited = node.status == "visited"
            drawCircle(if (visited) AppGreen else Color(0xFFF7F7F7), 46.dp.toPx(), point)
            drawCircle(if (visited) AppGreen else Color.Gray, 46.dp.toPx(), point, style = Stroke(3f))
            drawContext.canvas.nativeCanvas.drawText(node.label.take(9), point.x, point.y + 5.dp.toPx(), Paint().apply { color = if (visited) android.graphics.Color.WHITE else android.graphics.Color.DKGRAY; textAlign = Paint.Align.CENTER; isAntiAlias = true; typeface = android.graphics.Typeface.DEFAULT_BOLD; textSize = 12.dp.toPx() })
        }
        drawCircle(primary, 55.dp.toPx(), center)
        drawContext.canvas.nativeCanvas.drawText(topicName.take(9), center.x, center.y + 6.dp.toPx(), Paint().apply { color = android.graphics.Color.WHITE; textAlign = Paint.Align.CENTER; isAntiAlias = true; typeface = android.graphics.Typeface.DEFAULT_BOLD; textSize = 14.dp.toPx() })
    }
}

@Composable
private fun PerspectiveDialog(node: ThoughtNodeEntity, perspective: PerspectiveEntity?, state: PerspectiveState, onDismiss: () -> Unit, onRecommendationOpen: (String) -> Unit) {
    val context = LocalContext.current
    val sourceVideo = perspective?.let { p -> state.videos.firstOrNull { it.id == p.videoId } }
    val recommended = perspective?.let { p -> state.recommendedVideos.firstOrNull { it.perspectiveId == p.id } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(node.label, fontWeight = FontWeight.Black) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            sourceVideo?.let { Text("내가 이 관점을 접한 영상\n${it.title}\n${it.channelName}", style = MaterialTheme.typography.bodySmall) }
            perspective?.description?.takeIf(String::isNotBlank)?.let { Text("이 관점의 핵심", fontWeight = FontWeight.Bold); Text(it) }
            perspective?.representativeQuestion?.takeIf(String::isNotBlank)?.let { Text("다음에 볼 만한 질문", fontWeight = FontWeight.Bold); Text("○ $it", color = MaterialTheme.colorScheme.primary) }
            recommended?.let { video ->
                Card(Modifier.fillMaxWidth().clickable { perspective?.id?.let(onRecommendationOpen); context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(video.url))) }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold); Text(video.channelName, style = MaterialTheme.typography.labelSmall) }; Icon(Icons.Rounded.OpenInNew, null) }
                }
            }
        }
    }, confirmButton = { TextButton(onDismiss) { Text("닫기") } })
}

@Composable
private fun LegendDot(color: Color, label: String) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(11.dp).background(color, CircleShape)); Text(label, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelSmall) } }

@Composable
private fun InterestChangeScreen(
    state: PerspectiveState,
    watchedVideoPlayback: Map<String, WatchedVideoPlayback>,
    onResolveVideo: (WatchedVideoEntity) -> Unit,
) {
    var mode by remember { mutableStateOf(ChangePeriod.MONTH) }
    var selectedTopic by remember { mutableStateOf<PeriodTopicSelection?>(null) }
    val periods = remember(state.videos, state.videoTopics, state.topics, mode) { state.changePeriods(mode) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("관심 변화", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black); Text("많이 본 주제가 어떻게 달라졌는지 비교해요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { ChangePeriod.entries.forEach { item -> FilterChip(selected = mode == item, onClick = { mode = item }, label = { Text(item.label) }) } }
            }
        }
        if (periods.isEmpty()) item { Card { Text("5분 이상 본 영상이 쌓이면 관심 변화를 보여드릴게요.", Modifier.padding(22.dp), textAlign = TextAlign.Center) } }
        items(periods, key = ChangePeriodData::label) { period ->
            PeriodCard(period.label, period.exposures) { topic ->
                val linkedVideoIds = state.videoTopics.filter { it.topicId == topic.topicId }.mapTo(hashSetOf(), VideoTopicEntity::videoId)
                selectedTopic = PeriodTopicSelection(period.label, topic, period.videos.filter { it.id in linkedVideoIds })
            }
        }
    }
    selectedTopic?.let { selection ->
        TopicVideoListDialog(selection, watchedVideoPlayback, onResolveVideo) { selectedTopic = null }
    }
}

@Composable
private fun PeriodCard(label: String, exposures: List<TopicExposure>, onTopicClick: (TopicExposure) -> Unit) {
    val colors = listOf(AppGreen, AppCobalt, AppOrange, Color(0xFF8C6BC5))
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
            exposures.take(5).forEachIndexed { index, item ->
                Row(Modifier.fillMaxWidth().clickable { onTopicClick(item) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.label, Modifier.width(76.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(progress = { item.percentage / 100f }, Modifier.weight(1f).height(9.dp), color = colors[index % colors.size], trackColor = MaterialTheme.colorScheme.surfaceVariant)
                    Text("${item.percentage}%", Modifier.padding(start = 9.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TopicVideoListDialog(
    selection: PeriodTopicSelection,
    watchedVideoPlayback: Map<String, WatchedVideoPlayback>,
    onResolveVideo: (WatchedVideoEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val formatter = remember { DateTimeFormatter.ofPattern("M월 d일") }
    LaunchedEffect(selection.videos) { selection.videos.forEach(onResolveVideo) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(selection.topic.label, fontWeight = FontWeight.Black)
                Text("${selection.periodLabel} · ${selection.videos.size}개 영상", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            if (selection.videos.isEmpty()) Text("연결된 시청 영상을 찾지 못했어요.")
            else LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(selection.videos, key = WatchedVideoEntity::id) { video ->
                    val playback = watchedVideoPlayback[video.id]
                    Card(
                        Modifier.fillMaxWidth().clickable {
                            playback?.url?.let { target ->
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)))
                            }
                        },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.width(108.dp).height(64.dp).padding(end = 10.dp)
                                    .background(Color(0xFFE7E1EA), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                AsyncImage(
                                    model = playback?.thumbnailUrl,
                                    contentDescription = "${video.title} 썸네일",
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                                if (playback == null) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                                Text(video.channelName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                                val date = Instant.ofEpochMilli(video.watchedAt).atZone(ZoneId.systemDefault()).toLocalDate().format(formatter)
                                Text("$date · ${video.watchedSec / 60}분 시청", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(
                                Icons.Rounded.PlayCircle,
                                null,
                                tint = if (playback == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

private fun PerspectiveState.topicExposure(days: Long, now: Long = System.currentTimeMillis()) =
    exposureFor(videos.filter { it.source != "share" && it.watchedAt >= now - days * 86_400_000L && it.watchedSec >= MEANINGFUL_WATCH_SECONDS })

private fun PerspectiveState.exposureFor(periodVideos: List<WatchedVideoEntity>): List<TopicExposure> {
    val ids = periodVideos.mapTo(hashSetOf(), WatchedVideoEntity::id)
    val links = videoTopics.filter { it.videoId in ids }
    val total = links.size.coerceAtLeast(1)
    return links.groupBy { it.topicId }.map { (topicId, topicLinks) -> TopicExposure(topicId, topicName(topicId), topicLinks.size, (topicLinks.size * 100f / total).toInt()) }.sortedByDescending(TopicExposure::percentage)
}

private fun PerspectiveState.changePeriods(mode: ChangePeriod): List<ChangePeriodData> {
    val zone = ZoneId.systemDefault(); val meaningful = videos.filter { it.source != "share" && it.watchedSec >= MEANINGFUL_WATCH_SECONDS }
    return when (mode) {
        ChangePeriod.MONTH -> meaningful.groupBy { YearMonth.from(Instant.ofEpochMilli(it.watchedAt).atZone(zone)) }.toSortedMap(compareByDescending { it }).entries.take(12).map { (month, items) -> ChangePeriodData("${month.year}년 ${month.monthValue}월", items, exposureFor(items)) }
        ChangePeriod.YEAR -> meaningful.groupBy { Instant.ofEpochMilli(it.watchedAt).atZone(zone).year }.toSortedMap(compareByDescending { it }).map { (year, items) -> ChangePeriodData("${year}년", items, exposureFor(items)) }
    }
}

private fun Modifier.horizontalSwipe(nestedGesture: MutableState<Boolean>, onSwipeLeft: () -> Unit, onSwipeRight: () -> Unit): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var dx = 0f
            var dy = 0f
            while (true) {
                // 자식 LazyRow가 가로 드래그를 소비한 뒤 확인해야 영상 카드 스크롤이 탭 전환으로 오인되지 않는다.
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    if (!nestedGesture.value && abs(dx) > 100f && abs(dx) > abs(dy) * 1.2f) {
                        if (dx < 0) onSwipeLeft() else onSwipeRight()
                    }
                    nestedGesture.value = false
                    break
                }
                val delta = change.positionChange()
                dx += delta.x
                dy += delta.y
            }
        }
    }
}

private fun Modifier.markNestedHorizontalGesture(onPointerDown: () -> Unit): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            onPointerDown()
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.none { it.pressed }) break
            }
        }
    }
}
