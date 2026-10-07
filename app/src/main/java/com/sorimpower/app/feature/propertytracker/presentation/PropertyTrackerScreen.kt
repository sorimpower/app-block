package com.sorimpower.app.feature.propertytracker.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sorimpower.app.feature.propertytracker.data.AddPropertyTarget
import com.sorimpower.app.feature.propertytracker.data.NaverLandArea
import com.sorimpower.app.feature.propertytracker.data.NaverLandComplex
import com.sorimpower.app.feature.propertytracker.data.NaverLandProvider
import com.sorimpower.app.feature.propertytracker.data.PropertyActualTradeEntity
import com.sorimpower.app.feature.propertytracker.data.PropertyAskingSnapshotEntity
import com.sorimpower.app.feature.propertytracker.data.PropertyListingEntity
import com.sorimpower.app.feature.propertytracker.data.PropertyListingGroup
import com.sorimpower.app.feature.propertytracker.data.PropertyTrackerRepository
import com.sorimpower.app.feature.propertytracker.data.PropertyWatchTargetEntity
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToLong
import kotlinx.coroutines.launch

private enum class PropertyTab(val label: String) {
    WATCH("관심"), MOVE("갈아타기"), COMPARE("비교")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertyTrackerScreen(
    padding: PaddingValues,
    viewModel: PropertyTrackerViewModel,
    onSwipeEdgeLeft: () -> Unit = {},
    onSwipeEdgeRight: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val aiState by viewModel.aiState.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState { PropertyTab.entries.size }
    val coroutineScope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<PropertyWatchTargetEntity?>(null) }

    Box(Modifier.fillMaxSize().padding(padding)) {
        Column(Modifier.fillMaxSize()) {
            if (syncing) LinearProgressIndicator(Modifier.fillMaxWidth())
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                PropertyTab.entries.forEachIndexed { index, item ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(item.label, maxLines = 1) },
                    )
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().propertyEdgeSwipe(
                    currentPage = pagerState.currentPage,
                    pageCount = PropertyTab.entries.size,
                    onSwipeEdgeLeft = onSwipeEdgeLeft,
                    onSwipeEdgeRight = onSwipeEdgeRight,
                ),
                beyondViewportPageCount = 1,
            ) { page ->
                when (PropertyTab.entries[page]) {
                    PropertyTab.WATCH -> WatchTab(
                        state = state,
                        syncing = syncing,
                        onAdd = { showAdd = true },
                        onSync = viewModel::syncNow,
                        onCurrent = viewModel::setCurrentHome,
                        onMove = viewModel::setMoveTarget,
                        onCompare = viewModel::setCompareSelected,
                        onDelete = { deleteTarget = it },
                        onAnalyze = viewModel::analyzeListings,
                    )
                    PropertyTab.MOVE -> MoveTab(state)
                    PropertyTab.COMPARE -> CompareTab(
                        state = state,
                        onSelect = viewModel::setCompareSelected,
                        analyzing = aiState.loading,
                        onAnalyze = viewModel::analyzeComparison,
                    )
                }
            }
        }
        message?.let {
            Snackbar(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                action = { TextButton(onClick = viewModel::clearMessage) { Text("확인") } },
            ) { Text(it) }
        }
    }
    if (showAdd) AddTargetDialog(viewModel = viewModel, onDismiss = { showAdd = false }) {
        viewModel.addTarget(it)
        showAdd = false
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("관심 단지 삭제") },
            text = { Text("${target.apartmentName}의 저장된 호가·실거래·변화 기록도 함께 삭제합니다.") },
            confirmButton = {
                Button(onClick = { viewModel.deleteTarget(target.id); deleteTarget = null }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("취소") } },
        )
    }
    if (aiState.loading || aiState.content != null) {
        PropertyAiAnalysisDialog(aiState, viewModel::closeAiAnalysis)
    }
}

@Composable
private fun WatchTab(
    state: PropertyTrackerUiState,
    syncing: Boolean,
    onAdd: () -> Unit,
    onSync: () -> Unit,
    onCurrent: (String, Boolean) -> Unit,
    onMove: (String, Boolean) -> Unit,
    onCompare: (String, Boolean) -> Unit,
    onDelete: (PropertyWatchTargetEntity) -> Unit,
    onAnalyze: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${LocalDate.now().format(DateTimeFormatter.ofPattern("M월 d일"))} 오전 8시 기준",
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                IconButton(onClick = onSync, enabled = !syncing) {
                    if (syncing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Refresh, "지금 동기화")
                }
                Button(onClick = onAdd) { Icon(Icons.Rounded.Add, null); Text("단지") }
            }
        }
        if (state.targets.isEmpty()) {
            item {
                EmptyCard(
                    title = "아직 관심 단지가 없어요",
                    body = "네이버 부동산 단지 URL과 전용면적을 등록하면 오늘부터 호가 이력이 쌓입니다.",
                )
            }
        }
        items(state.targets, key = PropertyWatchTargetEntity::id) { target ->
            TargetCard(
                target,
                state.listingsFor(target.id),
                state.snapshotsFor(target.id),
                state.tradesFor(target.id),
                onCurrent,
                onMove,
                onCompare,
                onDelete,
                onAnalyze,
            )
        }
    }
}

@Composable
private fun TargetCard(
    target: PropertyWatchTargetEntity,
    all: List<PropertyListingEntity>,
    snapshots: List<PropertyAskingSnapshotEntity>,
    trades: List<PropertyActualTradeEntity>,
    onCurrent: (String, Boolean) -> Unit,
    onMove: (String, Boolean) -> Unit,
    onCompare: (String, Boolean) -> Unit,
    onDelete: (PropertyWatchTargetEntity) -> Unit,
    onAnalyze: (String) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    var expandedSection by remember(target.id) { mutableStateOf<String?>(null) }
    val groupedListings = remember(all) { PropertyTrackerRepository.groupDuplicateListings(all) }
    val activeGroups = groupedListings.filter { it.representative.status == "ACTIVE" }
    val sortedPrices = activeGroups.map { it.representative.priceKrw }.sorted()
    val lowestPrice = sortedPrices.firstOrNull() ?: 0L
    val highestPrice = sortedPrices.lastOrNull() ?: 0L
    val activeOriginalCount = all.count { it.status == "ACTIVE" }
    val recentTradeCount = trades.count { trade ->
        parseTradeDate(trade.tradeDate)?.let { it >= LocalDate.now().minusMonths(12) } == true
    }
    val syncMessage = if (target.lastSyncStatus == "SUCCESS") {
        buildString {
            append("호가 ${activeGroups.size}개")
            if (activeOriginalCount > activeGroups.size) append(" · 중개사 등록 ${activeOriginalCount}건")
            target.lastSyncMessage.substringAfter("실거래", missingDelimiterValue = "")
                .takeIf(String::isNotBlank)
                ?.let { append(" · 실거래$it") }
        }
    } else {
        target.lastSyncMessage
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(target.apartmentName, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                    Text(
                        targetAreaLabel(target) + " · 단지 ${target.complexNo}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onDelete(target) }) { Icon(Icons.Rounded.DeleteOutline, "삭제") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric(
                    label = "최저 호가",
                    value = formatWon(lowestPrice),
                    modifier = Modifier.weight(1f),
                    containerColor = Color(0xFFE7F7EF),
                    valueColor = Color(0xFF167A50),
                )
                Metric(
                    label = "최고 호가",
                    value = formatWon(highestPrice),
                    modifier = Modifier.weight(1f),
                    containerColor = Color(0xFFFFEEE6),
                    valueColor = Color(0xFFB64F24),
                )
            }
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .32f),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("매물·매매 수", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text(
                            "매물 ${activeGroups.size} · 1년 매매 $recentTradeCount",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MarketActivityChart(
                        targets = listOf(target),
                        snapshotsByTarget = mapOf(target.id to snapshots),
                        tradesByTarget = mapOf(target.id to trades),
                        modifier = Modifier.fillMaxWidth().height(116.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = target.isCurrentHome,
                    onClick = { onCurrent(target.id, !target.isCurrentHome) },
                    label = { Text("현재 집") },
                    leadingIcon = { Icon(Icons.Rounded.Home, null, Modifier.size(17.dp)) },
                )
                FilterChip(
                    selected = target.isMoveTarget,
                    onClick = { onMove(target.id, !target.isMoveTarget) },
                    label = { Text("갈아타기") },
                )
                FilterChip(
                    selected = target.isCompareSelected,
                    onClick = { onCompare(target.id, !target.isCompareSelected) },
                    label = { Text("비교") },
                )
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = syncColor(target.lastSyncStatus).copy(alpha = .1f),
            ) {
                Text(
                    syncMessage,
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = syncColor(target.lastSyncStatus),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = { expandedSection = if (expandedSection == "listings") null else "listings" }) {
                    Text(if (expandedSection == "listings") "매물 접기" else "매물 보기")
                }
                TextButton(onClick = { expandedSection = if (expandedSection == "trades") null else "trades" }) {
                    Text(if (expandedSection == "trades") "현황 접기" else "매매 현황")
                }
                TextButton(onClick = { onAnalyze(target.id) }) { Text("AI 추천") }
            }
            if (expandedSection == "listings") {
                groupedListings.sortedWith(
                    compareBy<PropertyListingGroup> { it.representative.status != "ACTIVE" }
                        .thenBy { it.representative.priceKrw },
                ).take(30).forEach { group ->
                        ListingRow(group.representative, group.listings.size) {
                            runCatching { uriHandler.openUri(group.representative.sourceUrl) }
                        }
                    }
            }
            if (expandedSection == "trades") {
                if (trades.isEmpty()) {
                    Text(
                        emptyActualText(target),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    trades.sortedByDescending(PropertyActualTradeEntity::tradeDate).take(30).forEach { trade ->
                        ActualTradeRow(trade)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActualTradeRow(trade: PropertyActualTradeEntity) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(Color(0xFFF08A35), CircleShape))
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(formatWon(trade.priceKrw), fontWeight = FontWeight.Bold)
            Text(
                "${formatTradeDate(trade.tradeDate)} · ${trade.floor.ifBlank { "층 미상" }}층 · 전용 ${formatArea(trade.exclusiveAreaSqm)}㎡",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ListingRow(listing: PropertyListingEntity, duplicateCount: Int, open: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = open).padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(8.dp).background(
                if (listing.status == "ACTIVE") Color(0xFF31A56A) else Color(0xFF9A929B),
                CircleShape,
            ),
        )
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(
                "${listing.priceText.ifBlank { formatWon(listing.priceKrw) }} · ${listing.buildingName} ${listing.floorInfo}",
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (listing.status == "REMOVED") {
                    "네이버에서 제거됨"
                } else {
                    buildList {
                        if (duplicateCount > 1) add("동일 매물 · 중개사 등록 ${duplicateCount}건")
                        addAll(listOf(listing.direction, listing.tags, listing.description).filter(String::isNotBlank))
                    }.joinToString(" · ")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun MoveTab(state: PropertyTrackerUiState) {
    val uriHandler = LocalUriHandler.current
    val current = state.targets.firstOrNull(PropertyWatchTargetEntity::isCurrentHome)
    val targets = state.targets.filter(PropertyWatchTargetEntity::isMoveTarget)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("내 집과 목표 단지의 갭", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text("최저·최고 호가와 최근 실거래 범위를 같은 기준으로 비교합니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (current == null || targets.isEmpty()) {
            item { EmptyCard("선택이 더 필요해요", "관심 탭에서 현재 집 1곳과 갈아타기 후보를 최대 5곳 선택해 주세요.") }
        } else {
            val currentAsking = askingRange(state, current.id)
            val currentActual = actualRange(state, current.id)
            item {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("현재 집 · ${current.apartmentName}", fontWeight = FontWeight.Black)
                        Text("호가 ${formatPriceRange(currentAsking)}")
                        Text("최근 실거래 ${formatPriceRange(currentActual, emptyActualText(current))}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            items(targets, key = PropertyWatchTargetEntity::id) { target ->
                val asking = askingRange(state, target.id)
                val actual = actualRange(state, target.id)
                val targetGroups = PropertyTrackerRepository.groupDuplicateListings(state.activeListingsFor(target.id))
                val lowestListing = targetGroups.minByOrNull { it.representative.priceKrw }?.representative
                val highestListing = targetGroups.maxByOrNull { it.representative.priceKrw }?.representative
                val openLowest = lowestListing?.sourceUrl?.takeIf(String::isNotBlank)?.let { url ->
                    { uriHandler.openUri(url) }
                }
                val openHighest = highestListing?.sourceUrl?.takeIf(String::isNotBlank)?.let { url ->
                    { uriHandler.openUri(url) }
                }
                Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.SwapHoriz, null, tint = MaterialTheme.colorScheme.primary)
                            Text(target.apartmentName, Modifier.padding(start = 8.dp), fontWeight = FontWeight.Black)
                        }
                        Text("호가 ${formatPriceRange(asking)}", style = MaterialTheme.typography.bodySmall)
                        if (asking.isAvailable && currentAsking.isAvailable) {
                            GapRow("최저 호가 갭", asking.min - currentAsking.min, openLowest)
                            GapRow("최고 호가 갭", asking.max - currentAsking.max, openHighest)
                        }
                        if (actual.isAvailable && currentActual.isAvailable) {
                            GapRow("실거래 최저 갭", actual.min - currentActual.min)
                            GapRow("실거래 최고 갭", actual.max - currentActual.max)
                        } else {
                            Text(
                                if (hasActualTradeSyncFailure(current) || hasActualTradeSyncFailure(target)) "국토부 실거래 조회가 지연되고 있습니다."
                                else "최근 1년 비교 가능한 신고 거래가 없습니다.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GapRow(label: String, gap: Long, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            when {
                gap > 0 -> "+${formatWon(gap)}"
                gap < 0 -> formatWon(gap)
                else -> "0원"
            },
            fontWeight = FontWeight.Black,
            color = if (gap > 0) MaterialTheme.colorScheme.error else Color(0xFF24865B),
        )
        if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Rounded.OpenInNew,
                "매물 열기",
                Modifier.padding(start = 5.dp).size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun CompareTab(
    state: PropertyTrackerUiState,
    onSelect: (String, Boolean) -> Unit,
    analyzing: Boolean,
    onAnalyze: () -> Unit,
) {
    val selected = state.targets.filter(PropertyWatchTargetEntity::isCompareSelected)
    val targetColors = state.targets.mapIndexed { index, target -> target.id to comparisonColor(index) }.toMap()
    var period by remember { mutableStateOf(ComparePeriod.YEAR) }
    var priceMode by remember { mutableStateOf(ComparePriceMode.ACTUAL_RANGE) }
    var activityTargetId by remember(selected) { mutableStateOf(selected.firstOrNull()?.id) }
    LaunchedEffect(selected.map(PropertyWatchTargetEntity::id)) {
        if (activityTargetId !in selected.map(PropertyWatchTargetEntity::id)) {
            activityTargetId = selected.firstOrNull()?.id
        }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("단지 가격 비교", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text("실거래 흐름과 현재 호가 범위를 한눈에 비교해요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            CompareTargetSelector(
                allTargets = state.targets,
                selected = selected,
                colorsByTarget = targetColors,
                onSelect = onSelect,
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                ComparePeriod.entries.forEach { option ->
                    FilterChip(
                        selected = period == option,
                        onClick = { period = option },
                        label = { Text(option.label) },
                    )
                }
            }
        }
        if (selected.size < 2) {
            item { EmptyCard("비교 단지를 2곳 이상 골라 주세요", "상단 카드에서 관심 단지를 추가하면 가격 흐름을 비교할 수 있어요.") }
        } else {
            item {
                ComparisonTimelineChart(
                    targets = selected,
                    state = state,
                    colorsByTarget = targetColors,
                    period = period,
                    mode = priceMode,
                    onModeChange = { priceMode = it },
                    modifier = Modifier.fillMaxWidth().height(430.dp),
                )
            }
            item {
                MarketActivitySection(
                    targets = selected,
                    state = state,
                    colorsByTarget = targetColors,
                    selectedTargetId = activityTargetId ?: selected.first().id,
                    onSelectTarget = { activityTargetId = it },
                    period = period,
                )
            }
            item { CompareSummaryPager(selected, state, targetColors) }
            item {
                Button(onClick = onAnalyze, enabled = !analyzing, modifier = Modifier.fillMaxWidth()) {
                    if (analyzing) CircularProgressIndicator(Modifier.padding(end = 8.dp).size(18.dp), strokeWidth = 2.dp)
                    Text(if (analyzing) "갈아타기 분석 중" else "AI로 갈아타기 분석")
                }
            }
            item {
                val delayed = selected.filter(::hasActualTradeSyncFailure)
                val latestSync = selected.mapNotNull(PropertyWatchTargetEntity::lastSyncAt).maxOrNull() ?: 0L
                Text(
                    buildString {
                        if (latestSync > 0) append("업데이트 ${formatSyncTime(latestSync)}")
                        if (delayed.isNotEmpty()) {
                            if (isNotEmpty()) append(" · ")
                            append("실거래 업데이트 지연 ${delayed.size}곳")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (delayed.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun CompareTargetSelector(
    allTargets: List<PropertyWatchTargetEntity>,
    selected: List<PropertyWatchTargetEntity>,
    colorsByTarget: Map<String, Color>,
    onSelect: (String, Boolean) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("비교 단지 (${selected.size}/4)", Modifier.weight(1f), fontWeight = FontWeight.Black)
                Box {
                    TextButton(onClick = { menuOpen = true }, enabled = selected.size < 4 && selected.size < allTargets.size) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(17.dp))
                        Text("단지 추가")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        allTargets.filterNot { it.isCompareSelected }.forEach { target ->
                            DropdownMenuItem(
                                text = { Text(target.apartmentName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                onClick = { onSelect(target.id, true); menuOpen = false },
                            )
                        }
                    }
                }
            }
            if (selected.isEmpty()) {
                Text("관심 단지 중 2~4곳을 선택해 주세요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            selected.chunked(2).forEach { rowTargets ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowTargets.forEach { target ->
                        val color = colorsByTarget[target.id] ?: MaterialTheme.colorScheme.primary
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(15.dp),
                            color = color.copy(alpha = .08f),
                            border = BorderStroke(1.dp, color.copy(alpha = .32f)),
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(10.dp).background(color, CircleShape))
                                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                                    Text(target.apartmentName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                    Text(targetAreaLabel(target), maxLines = 1, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { onSelect(target.id, false) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Rounded.Close, "비교에서 제외", Modifier.size(17.dp))
                                }
                            }
                        }
                    }
                    if (rowTargets.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PropertyAiAnalysisDialog(state: PropertyAiUiState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!state.loading) onDismiss() },
        title = { Text(state.title.ifBlank { "부동산 AI 분석" }, fontWeight = FontWeight.Black) },
        text = {
            if (state.loading) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text("저장된 매물과 실거래를 분석하고 있어요.", Modifier.padding(start = 12.dp))
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().height(500.dp)) {
                    item {
                        Text(
                            state.content.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!state.loading) TextButton(onClick = onDismiss) { Text("확인") }
        },
    )
}

@Composable
private fun AddTargetDialog(
    viewModel: PropertyTrackerViewModel,
    onDismiss: () -> Unit,
    onAdd: (AddPropertyTarget) -> Unit,
) {
    val searchResults by viewModel.complexSearchResults.collectAsStateWithLifecycle()
    val selectedDetail by viewModel.selectedComplexDetail.collectAsStateWithLifecycle()
    val searching by viewModel.searchingComplex.collectAsStateWithLifecycle()
    val searchMessage by viewModel.complexSearchMessage.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var complexInput by remember { mutableStateOf("") }
    var area by remember { mutableStateOf("") }
    var areaNo by remember { mutableStateOf("") }
    var selectedAreaNos by remember { mutableStateOf<Set<String>>(emptySet()) }
    var lawdCd by remember { mutableStateOf("") }
    var selectedComplexNo by remember { mutableStateOf<String?>(null) }
    var manualMode by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose(viewModel::clearComplexSearch) }
    LaunchedEffect(selectedDetail) {
        selectedDetail?.let { detail ->
            name = detail.complex.complexName.ifBlank { name }
            lawdCd = detail.complex.cortarNo.take(5).filter(Char::isDigit).ifBlank { lawdCd }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("관심 단지·평형 등록") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!manualMode) {
                    item {
                        Text(
                            "단지명을 검색하면 주소와 평형을 자동으로 채워요.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                query,
                                { query = it },
                                modifier = Modifier.weight(1f),
                                label = { Text("단지명 또는 주소") },
                                placeholder = { Text("예: 래미안 원베일리") },
                                singleLine = true,
                            )
                            Button(
                                onClick = { viewModel.searchComplexes(query) },
                                enabled = query.trim().length >= 2 && !searching,
                            ) { Text("검색") }
                        }
                    }
                    if (searching) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    searchMessage?.let { message ->
                        item { Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    }
                    if (selectedComplexNo == null) {
                        items(searchResults, key = NaverLandComplex::complexNo) { complex ->
                            ComplexSearchResult(
                                complex = complex,
                                onClick = {
                                    selectedComplexNo = complex.complexNo
                                    name = complex.complexName
                                    complexInput = complex.complexNo
                                    lawdCd = complex.cortarNo.take(5).filter(Char::isDigit)
                                    viewModel.selectComplex(complex)
                                },
                            )
                        }
                    } else {
                        item {
                            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f)) {
                                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                    Text(name, fontWeight = FontWeight.Black)
                                    selectedDetail?.complex?.address?.takeIf(String::isNotBlank)?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    TextButton(onClick = {
                                        selectedComplexNo = null
                                        area = ""
                                        areaNo = ""
                                        selectedAreaNos = emptySet()
                                    }) { Text("다른 단지 선택") }
                                }
                            }
                        }
                        selectedDetail?.let { detail ->
                            if (detail.areas.isEmpty()) {
                                item { Text("자동으로 확인된 평형이 없습니다. 아래 직접 입력을 이용해 주세요.", style = MaterialTheme.typography.bodySmall) }
                            } else {
                                item {
                                    Column {
                                        Text("전용면적·타입 선택", fontWeight = FontWeight.Bold)
                                        Text(
                                            "59A/59B처럼 전용면적이 비슷한 타입은 여러 개 선택할 수 있어요.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                items(detail.areas, key = { "${it.areaNo}:${it.exclusiveAreaSqm}" }) { option ->
                                    AreaOption(
                                        option = option,
                                        selected = option.areaNo in selectedAreaNos,
                                        onClick = {
                                            selectedAreaNos = toggleSimilarAreaSelection(selectedAreaNos, option, detail.areas)
                                            val selectedAreas = detail.areas.filter { it.areaNo in selectedAreaNos }
                                            areaNo = selectedAreas.joinToString(",", transform = NaverLandArea::areaNo)
                                            area = selectedAreas
                                                .map(NaverLandArea::exclusiveAreaSqm)
                                                .average()
                                                .takeUnless(Double::isNaN)
                                                ?.let { "%.2f".format(Locale.US, it).trimEnd('0').trimEnd('.') }
                                                .orEmpty()
                                        },
                                    )
                                }
                            }
                        }
                    }
                    item { TextButton(onClick = { manualMode = true }) { Text("URL·단지번호로 직접 입력") } }
                } else {
                    item { OutlinedTextField(name, { name = it }, label = { Text("아파트 이름") }, singleLine = true) }
                    item { OutlinedTextField(complexInput, { complexInput = it }, label = { Text("네이버 단지 URL 또는 단지번호") }, singleLine = true) }
                    item {
                        OutlinedTextField(
                            area, { area = it }, label = { Text("전용면적(㎡)") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                    item {
                        OutlinedTextField(
                            areaNo, { areaNo = it.filter { char -> char.isDigit() || char == ',' } },
                            label = { Text("네이버 평형번호(선택·쉼표 구분)") },
                            supportingText = { Text("여러 타입은 1,2처럼 입력하고, 비우면 전용면적으로 골라냅니다.") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                    item {
                        OutlinedTextField(
                            lawdCd, { lawdCd = it.filter(Char::isDigit).take(5) },
                            label = { Text("법정동 코드 5자리(선택)") },
                            supportingText = { Text("입력하면 국토부 실거래가도 함께 조회합니다.") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                    item { TextButton(onClick = { manualMode = false }) { Text("단지명으로 검색") } }
                }
                item {
                    Text(
                        "단지 검색과 호가는 네이버의 비공식 웹 응답을 사용해 일시 제한되거나 형식이 바뀔 수 있습니다. 검색 실패 시 직접 입력할 수 있어요.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAdd(AddPropertyTarget(name, complexInput, areaNo, area.toDoubleOrNull() ?: 0.0, lawdCd))
                },
                enabled = name.isNotBlank() && complexInput.isNotBlank() && (area.toDoubleOrNull() ?: 0.0) > 0,
            ) { Text("등록") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun ComplexSearchResult(complex: NaverLandComplex, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(complex.complexName.ifBlank { "단지 ${complex.complexNo}" }, fontWeight = FontWeight.Black)
            val details = buildList {
                complex.address.takeIf(String::isNotBlank)?.let(::add)
                complex.totalHouseholdCount?.let { add("${it}세대") }
                complex.useApproveYmd.takeIf(String::isNotBlank)?.let { add("사용승인 $it") }
            }
            if (details.isNotEmpty()) Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AreaOption(option: NaverLandArea, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        label = {
            Column(Modifier.padding(vertical = 3.dp)) {
                Text(
                    buildString {
                        if (option.pyeongName.isNotBlank()) append("${option.pyeongName}평 · ")
                        append("전용 ${formatArea(option.exclusiveAreaSqm)}㎡")
                    },
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    buildList {
                        option.supplyAreaSqm?.let { add("공급 ${formatArea(it)}㎡") }
                        option.householdCount?.let { add("${it}세대") }
                    }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun Metric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = containerColor) {
        Column(Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.Black, color = valueColor, maxLines = 1)
        }
    }
}

@Composable
private fun EmptyCard(title: String, body: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(title, fontWeight = FontWeight.Black)
            Text(body, Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ComparisonTimelineChart(
    targets: List<PropertyWatchTargetEntity>,
    state: PropertyTrackerUiState,
    colorsByTarget: Map<String, Color>,
    period: ComparePeriod,
    mode: ComparePriceMode,
    onModeChange: (ComparePriceMode) -> Unit,
    modifier: Modifier,
) {
    val today = LocalDate.now()
    val earliest = targets.flatMap { target ->
        state.tradesFor(target.id).mapNotNull { parseTradeDate(it.tradeDate) } +
            state.snapshotsFor(target.id).map { LocalDate.ofEpochDay(it.epochDay) }
    }.minOrNull()
    val startDate = period.startDate(today, earliest)
    val points = remember(targets, state.trades, state.snapshots, period, mode, startDate) {
        buildComparePricePoints(targets, state, period, mode, startDate, today)
    }
    var selectedDate by remember(period, mode) { mutableStateOf<LocalDate?>(null) }
    var hiddenIds by remember(targets.map(PropertyWatchTargetEntity::id)) { mutableStateOf(emptySet<String>()) }
    val visiblePoints = points.filterNot { it.targetId in hiddenIds }
    val values = visiblePoints.flatMap { listOf(it.minValue, it.maxValue) }
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Surface(modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("가격 흐름", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                ComparePriceMode.entries.forEach { option ->
                    FilterChip(selected = mode == option, onClick = { onModeChange(option) }, label = { Text(option.label) })
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (values.isEmpty()) {
                    Text(
                        if (mode == ComparePriceMode.ASKING_GAP) "호가 중간값과 직전 3개월 실거래가 모두 있어야 계산할 수 있어요."
                        else "선택한 기간에 표시할 가격 기록이 없습니다.",
                        Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodySmall,
                        color = labelColor,
                    )
                } else {
                    val rawMin = values.min()
                    val rawMax = values.max()
                    val minimumPadding = if (mode == ComparePriceMode.ASKING_GAP) 2.0 else 10_000_000.0
                    val valuePadding = ((rawMax - rawMin) * .1).coerceAtLeast(minimumPadding)
                    val minValue = if (mode == ComparePriceMode.ASKING_GAP) rawMin - valuePadding else (rawMin - valuePadding).coerceAtLeast(0.0)
                    val maxValue = rawMax + valuePadding
                    val valueRange = (maxValue - minValue).coerceAtLeast(1.0)
                    val dayRange = ChronoUnit.DAYS.between(startDate, today).coerceAtLeast(1L)
                    Canvas(
                        Modifier.fillMaxSize()
                            .pointerInput(visiblePoints, startDate, today) {
                                detectTapGestures { tap ->
                                    val left = 60.dp.toPx()
                                    val right = size.width - 8.dp.toPx()
                                    val fraction = ((tap.x - left) / (right - left)).coerceIn(0f, 1f)
                                    val date = startDate.plusDays((dayRange * fraction).roundToLong())
                                    selectedDate = visiblePoints.minByOrNull { abs(ChronoUnit.DAYS.between(it.date, date)) }?.date
                                }
                            }
                            .pointerInput(visiblePoints, startDate, today) {
                                fun selectAt(x: Float) {
                                    val left = 60.dp.toPx()
                                    val right = size.width - 8.dp.toPx()
                                    val fraction = ((x - left) / (right - left)).coerceIn(0f, 1f)
                                    val date = startDate.plusDays((dayRange * fraction).roundToLong())
                                    selectedDate = visiblePoints.minByOrNull { abs(ChronoUnit.DAYS.between(it.date, date)) }?.date
                                }
                                detectHorizontalDragGestures(
                                    onDragStart = { selectAt(it.x) },
                                    onHorizontalDrag = { change, _ -> selectAt(change.position.x) },
                                )
                            },
                    ) {
                        val left = 60.dp.toPx()
                        val right = size.width - 8.dp.toPx()
                        val top = 6.dp.toPx()
                        val bottom = size.height - 30.dp.toPx()
                        fun x(date: LocalDate) = left + (right - left) * ChronoUnit.DAYS.between(startDate, date).toFloat() / dayRange
                        fun y(value: Double) = bottom - (bottom - top) * ((value - minValue) / valueRange).toFloat()
                        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = labelColor.toArgb()
                            textSize = 9.dp.toPx()
                        }
                        listOf(0f, .25f, .5f, .75f, 1f).forEach { fraction ->
                            val lineY = bottom - (bottom - top) * fraction
                            drawLine(gridColor, Offset(left, lineY), Offset(right, lineY), 1.dp.toPx())
                            val value = minValue + valueRange * fraction
                            val label = if (mode == ComparePriceMode.ASKING_GAP) "%.1f%%".format(value) else formatEokAxis(value.roundToLong())
                            drawContext.canvas.nativeCanvas.drawText(label, 0f, lineY + 4.dp.toPx(), paint)
                        }
                        if (mode == ComparePriceMode.ASKING_GAP && minValue < 0 && maxValue > 0) {
                            drawLine(labelColor.copy(alpha = .7f), Offset(left, y(0.0)), Offset(right, y(0.0)), 1.5.dp.toPx())
                        }
                        drawContext.canvas.nativeCanvas.drawText(formatAxisDate(startDate, period), left, size.height, paint)
                        val endLabel = formatAxisDate(today, period)
                        drawContext.canvas.nativeCanvas.drawText(endLabel, right - paint.measureText(endLabel), size.height, paint)
                        targets.forEachIndexed { index, target ->
                            if (target.id in hiddenIds) return@forEachIndexed
                            val color = colorsByTarget[target.id] ?: comparisonColor(index)
                            splitPriceSegments(points.filter { it.targetId == target.id }, period).forEach { segment ->
                                drawCompareRangeSegment(segment, color, ::x, ::y, mode == ComparePriceMode.ASKING_RANGE)
                            }
                        }
                        selectedDate?.let { date ->
                            drawLine(labelColor.copy(alpha = .72f), Offset(x(date), top), Offset(x(date), bottom), 1.dp.toPx())
                        }
                    }
                    selectedDate?.let { date ->
                        val selectedPoints = visiblePoints.filter { it.date == date }
                        Surface(
                            Modifier.align(Alignment.TopEnd).padding(4.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 4.dp,
                        ) {
                            Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(formatChartDate(date, period), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                selectedPoints.forEach { point ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(7.dp).background(colorsByTarget[point.targetId] ?: MaterialTheme.colorScheme.primary, CircleShape))
                                        Text("${point.targetName}  ${formatComparePoint(point, mode)}", Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                targets.forEachIndexed { index, target ->
                    val color = colorsByTarget[target.id] ?: comparisonColor(index)
                    Row(
                        Modifier.clickable { hiddenIds = if (target.id in hiddenIds) hiddenIds - target.id else hiddenIds + target.id }.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(8.dp).background(if (target.id in hiddenIds) color.copy(alpha = .25f) else color, CircleShape))
                        Text(
                            target.apartmentName,
                            Modifier.padding(start = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (target.id in hiddenIds) labelColor.copy(alpha = .5f) else labelColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegacyComparisonTimelineChart(
    targets: List<PropertyWatchTargetEntity>,
    state: PropertyTrackerUiState,
    colorsByTarget: Map<String, Color>,
    modifier: Modifier,
) {
    val today = LocalDate.now()
    var period by remember { mutableStateOf(PriceChartPeriod.YEAR) }
    var showTrades by remember { mutableStateOf(true) }
    var showAsking by remember { mutableStateOf(true) }
    var selection by remember { mutableStateOf<PriceChartSelection?>(null) }
    val startDate = period.startDate(today)
    val actualRanges = targets.flatMap { target ->
        state.tradesFor(target.id)
            .mapNotNull { trade -> parseTradeDate(trade.tradeDate)?.let { it to trade.priceKrw } }
            .filter { (date, _) -> date in startDate..today }
            .groupBy { (date, _) -> if (period == PriceChartPeriod.YEAR) YearMonth.from(date) else date }
            .map { (bucket, trades) ->
                val prices = trades.map { it.second }
                val date = when (bucket) {
                    is YearMonth -> bucket.atDay(minOf(15, bucket.lengthOfMonth())).coerceAtMost(today)
                    else -> bucket as LocalDate
                }
                ActualRangePoint(target.id, target.apartmentName, date, prices.min(), prices.max(), prices.size)
            }
            .sortedBy(ActualRangePoint::date)
    }
    val datedSnapshots = targets.flatMap { target ->
        state.snapshotsFor(target.id).mapNotNull { snapshot ->
            val date = LocalDate.ofEpochDay(snapshot.epochDay)
            if (date in startDate..today && snapshot.minPriceKrw > 0 && snapshot.maxPriceKrw > 0) {
                AskingRangePoint(target.id, target.apartmentName, date, snapshot)
            } else {
                null
            }
        }
    }
    val allPrices = buildList {
        if (showTrades) actualRanges.forEach { add(it.minPriceKrw); add(it.maxPriceKrw) }
        if (showAsking) datedSnapshots.forEach { add(it.snapshot.minPriceKrw); add(it.snapshot.maxPriceKrw) }
    }
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val fallbackColor = MaterialTheme.colorScheme.primary

    Surface(modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("가격 흐름", Modifier.weight(1f), fontWeight = FontWeight.Black)
                PriceChartPeriod.entries.forEach { option ->
                    FilterChip(
                        selected = period == option,
                        onClick = { period = option; selection = null },
                        label = { Text(option.label) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = showTrades,
                    onClick = { if (!showTrades || showAsking) showTrades = !showTrades; selection = null },
                    label = { Text("실거래 최저~최고") },
                )
                FilterChip(
                    selected = showAsking,
                    onClick = { if (!showAsking || showTrades) showAsking = !showAsking; selection = null },
                    label = { Text("호가 범위") },
                )
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                if (allPrices.isEmpty()) {
                    Text(
                        "선택한 기간에 표시할 가격 기록이 없습니다.",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val rawMin = allPrices.min()
                    val rawMax = allPrices.max()
                    val pricePadding = ((rawMax - rawMin) * .08).roundToLong().coerceAtLeast(10_000_000L)
                    val minPrice = (rawMin - pricePadding).coerceAtLeast(0L)
                    val maxPrice = rawMax + pricePadding
                    val priceRange = (maxPrice - minPrice).coerceAtLeast(1L)
                    val dayRange = ChronoUnit.DAYS.between(startDate, today).coerceAtLeast(1L)
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(
                            targets,
                            actualRanges,
                            datedSnapshots,
                            showTrades,
                            showAsking,
                            minPrice,
                            maxPrice,
                        ) {
                            detectTapGestures { tap ->
                                val left = 64.dp.toPx()
                                val right = size.width - 8.dp.toPx()
                                val top = 6.dp.toPx()
                                val bottom = size.height - 30.dp.toPx()
                                fun x(date: LocalDate) = left + (right - left) * ChronoUnit.DAYS.between(startDate, date).toFloat() / dayRange
                                fun y(price: Long) = bottom - (bottom - top) * (price - minPrice).toFloat() / priceRange
                                val hitRadius = 24.dp.toPx()
                                var nearestDistance = Float.MAX_VALUE
                                var nearest: PriceChartSelection? = null
                                if (showTrades) actualRanges.forEach { point ->
                                    val pointX = x(point.date)
                                    val upper = y(point.maxPriceKrw)
                                    val lower = y(point.minPriceKrw)
                                    val verticalDistance = when {
                                        tap.y < upper -> upper - tap.y
                                        tap.y > lower -> tap.y - lower
                                        else -> 0f
                                    }
                                    val distance = hypot(tap.x - pointX, verticalDistance)
                                    if (distance <= hitRadius && distance < nearestDistance) {
                                        nearestDistance = distance
                                        nearest = PriceChartSelection(
                                            "${formatChartDate(point.date, period)} · ${point.targetName}\n실거래 ${formatPriceRange(PriceRange(point.minPriceKrw, point.maxPriceKrw))} · ${point.count}건",
                                            colorsByTarget[point.targetId] ?: fallbackColor,
                                        )
                                    }
                                }
                                if (showAsking) datedSnapshots.forEach { point ->
                                    val targetIndex = targets.indexOfFirst { it.id == point.targetId }.coerceAtLeast(0)
                                    val targetSnapshotCount = datedSnapshots.count { it.targetId == point.targetId }
                                    val pointX = x(point.date) - if (targetSnapshotCount == 1) (targets.lastIndex - targetIndex) * 12.dp.toPx() else 0f
                                    val upper = y(point.snapshot.maxPriceKrw)
                                    val lower = y(point.snapshot.minPriceKrw)
                                    val verticalDistance = when {
                                        tap.y < upper -> upper - tap.y
                                        tap.y > lower -> tap.y - lower
                                        else -> 0f
                                    }
                                    val distance = hypot(tap.x - pointX, verticalDistance)
                                    if (distance <= hitRadius && distance < nearestDistance) {
                                        nearestDistance = distance
                                        nearest = PriceChartSelection(
                                            "${point.date.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일"))} · ${point.targetName}\n호가 ${formatPriceRange(PriceRange(point.snapshot.minPriceKrw, point.snapshot.maxPriceKrw))}",
                                            colorsByTarget[point.targetId] ?: fallbackColor,
                                        )
                                    }
                                }
                                selection = nearest
                            }
                        },
                    ) {
                        val left = 64.dp.toPx()
                        val right = size.width - 8.dp.toPx()
                        val top = 6.dp.toPx()
                        val bottom = size.height - 30.dp.toPx()
                        fun x(date: LocalDate): Float = left + (right - left) * ChronoUnit.DAYS.between(startDate, date).toFloat() / dayRange
                        fun y(price: Long): Float = bottom - (bottom - top) * (price - minPrice).toFloat() / priceRange
                        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = labelColor.toArgb()
                            textSize = 9.dp.toPx()
                        }
                        listOf(0f, .5f, 1f).forEach { fraction ->
                            val lineY = bottom - (bottom - top) * fraction
                            drawLine(gridColor, Offset(left, lineY), Offset(right, lineY), strokeWidth = 1.dp.toPx())
                            val price = minPrice + (priceRange * fraction).roundToLong()
                            drawContext.canvas.nativeCanvas.drawText(formatEokAxis(price), 0f, lineY + 4.dp.toPx(), paint)
                        }
                        drawContext.canvas.nativeCanvas.drawText(formatAxisDate(startDate, period), left, size.height, paint)
                        val endLabel = formatAxisDate(today, period)
                        drawContext.canvas.nativeCanvas.drawText(endLabel, right - paint.measureText(endLabel), size.height, paint)

                        if (showAsking) targets.forEachIndexed { index, target ->
                            val color = colorsByTarget[target.id] ?: comparisonColor(index)
                            val snapshots = datedSnapshots.filter { it.targetId == target.id }.sortedBy(AskingRangePoint::date)
                            if (snapshots.size == 1) {
                                val point = snapshots.first()
                                val pointX = (x(point.date) - (targets.lastIndex - index) * 12.dp.toPx()).coerceAtLeast(left)
                                val halfWidth = 10.dp.toPx()
                                val upper = y(point.snapshot.maxPriceKrw)
                                val lower = y(point.snapshot.minPriceKrw)
                                drawRect(color.copy(alpha = .1f), Offset(pointX - halfWidth, upper), androidx.compose.ui.geometry.Size(halfWidth * 2, lower - upper))
                                var hatchY = upper
                                while (hatchY < lower) {
                                    drawLine(color.copy(alpha = .45f), Offset(pointX - halfWidth, hatchY + 7.dp.toPx()), Offset(pointX + halfWidth, hatchY), 1.dp.toPx())
                                    hatchY += 7.dp.toPx()
                                }
                                drawCircle(color, 4.dp.toPx(), Offset(pointX, upper))
                                drawCircle(color, 4.dp.toPx(), Offset(pointX, lower))
                            } else if (snapshots.size > 1) {
                                drawRangeBand(
                                    points = snapshots.map { Triple(it.date, it.snapshot.minPriceKrw, it.snapshot.maxPriceKrw) },
                                    color = color,
                                    x = ::x,
                                    y = ::y,
                                    hatched = true,
                                )
                            }
                        }
                        if (showTrades) targets.forEachIndexed { index, target ->
                            val color = colorsByTarget[target.id] ?: comparisonColor(index)
                            val points = actualRanges.filter { it.targetId == target.id }.sortedBy(ActualRangePoint::date)
                            drawRangeBand(
                                points = points.map { Triple(it.date, it.minPriceKrw, it.maxPriceKrw) },
                                color = color,
                                x = ::x,
                                y = ::y,
                                hatched = false,
                            )
                        }
                    }
                    selection?.let { selected ->
                        Surface(
                            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shadowElevation = 4.dp,
                        ) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.Top) {
                                Box(Modifier.padding(top = 4.dp).size(8.dp).background(selected.color, CircleShape))
                                Text(selected.text, Modifier.padding(start = 7.dp), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketActivitySection(
    targets: List<PropertyWatchTargetEntity>,
    state: PropertyTrackerUiState,
    colorsByTarget: Map<String, Color>,
    selectedTargetId: String,
    onSelectTarget: (String) -> Unit,
    period: ComparePeriod,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val target = targets.firstOrNull { it.id == selectedTargetId } ?: targets.first()
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("매물 · 매매 수", Modifier.weight(1f), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                Box {
                    TextButton(onClick = { menuOpen = true }) {
                        Box(Modifier.size(8.dp).background(colorsByTarget[target.id] ?: MaterialTheme.colorScheme.primary, CircleShape))
                        Text(target.apartmentName, Modifier.padding(start = 6.dp), maxLines = 1)
                        Icon(Icons.Rounded.ArrowDropDown, null)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        targets.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.apartmentName) },
                                onClick = { onSelectTarget(option.id); menuOpen = false },
                            )
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("■ 매매 거래량(건)", style = MaterialTheme.typography.labelSmall, color = Color(0xFFB79BF6))
                Text("● 매물 수(개)", style = MaterialTheme.typography.labelSmall, color = colorsByTarget[target.id] ?: MaterialTheme.colorScheme.primary)
            }
            CompareMarketActivityChart(
                target = target,
                snapshots = state.snapshotsFor(target.id),
                trades = state.tradesFor(target.id),
                color = colorsByTarget[target.id] ?: MaterialTheme.colorScheme.primary,
                period = period,
                modifier = Modifier.fillMaxWidth().height(190.dp),
            )
        }
    }
}

@Composable
private fun CompareMarketActivityChart(
    target: PropertyWatchTargetEntity,
    snapshots: List<PropertyAskingSnapshotEntity>,
    trades: List<PropertyActualTradeEntity>,
    color: Color,
    period: ComparePeriod,
    modifier: Modifier,
) {
    val today = LocalDate.now()
    val earliest = (snapshots.map { LocalDate.ofEpochDay(it.epochDay) } + trades.mapNotNull { parseTradeDate(it.tradeDate) }).minOrNull()
    val startDate = period.startDate(today, earliest)
    val points = remember(target.id, snapshots, trades, period) {
        val listingCounts = snapshots
            .map { LocalDate.ofEpochDay(it.epochDay) to it }
            .filter { it.first in startDate..today }
            .groupBy { period.bucket(it.first) }
            .mapValues { (_, entries) -> entries.maxByOrNull { it.second.epochDay }?.second?.activeCount ?: 0 }
        val tradeCounts = trades.mapNotNull { parseTradeDate(it.tradeDate) }
            .filter { it in startDate..today }
            .groupingBy(period::bucket)
            .eachCount()
        (listingCounts.keys + tradeCounts.keys).distinct().sorted().map { date ->
            MarketActivityPoint(date, tradeCounts[date] ?: 0, listingCounts[date])
        }
    }
    var selectedDate by remember(period, target.id) { mutableStateOf<LocalDate?>(null) }
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val barColor = Color(0xFFB79BF6)
    val maxListings = points.mapNotNull(MarketActivityPoint::listingCount).maxOrNull()?.coerceAtLeast(1) ?: 1
    val maxTrades = points.maxOfOrNull(MarketActivityPoint::tradeCount)?.coerceAtLeast(1) ?: 1
    val dayRange = ChronoUnit.DAYS.between(startDate, today).coerceAtLeast(1L)

    Box(modifier) {
        if (points.isEmpty()) {
            Text("선택한 기간에 활동 기록이 없습니다.", Modifier.align(Alignment.Center), style = MaterialTheme.typography.bodySmall, color = labelColor)
        } else {
            Canvas(
                Modifier.fillMaxSize()
                    .pointerInput(points, startDate, today) {
                        fun selectAt(positionX: Float) {
                            val left = 44.dp.toPx()
                            val right = size.width - 38.dp.toPx()
                            val fraction = ((positionX - left) / (right - left)).coerceIn(0f, 1f)
                            val date = startDate.plusDays((dayRange * fraction).roundToLong())
                            selectedDate = points.minByOrNull { abs(ChronoUnit.DAYS.between(it.date, date)) }?.date
                        }
                        detectTapGestures { selectAt(it.x) }
                    }
                    .pointerInput(points, startDate, today) {
                        fun selectAt(positionX: Float) {
                            val left = 44.dp.toPx()
                            val right = size.width - 38.dp.toPx()
                            val fraction = ((positionX - left) / (right - left)).coerceIn(0f, 1f)
                            val date = startDate.plusDays((dayRange * fraction).roundToLong())
                            selectedDate = points.minByOrNull { abs(ChronoUnit.DAYS.between(it.date, date)) }?.date
                        }
                        detectHorizontalDragGestures(
                            onDragStart = { selectAt(it.x) },
                            onHorizontalDrag = { change, _ -> selectAt(change.position.x) },
                        )
                    },
            ) {
                val left = 44.dp.toPx()
                val right = size.width - 38.dp.toPx()
                val top = 6.dp.toPx()
                val bottom = size.height - 30.dp.toPx()
                fun x(date: LocalDate) = left + (right - left) * ChronoUnit.DAYS.between(startDate, date).toFloat() / dayRange
                fun listingY(value: Int) = bottom - (bottom - top) * value / maxListings.toFloat()
                fun tradeHeight(value: Int) = (bottom - top) * value / maxTrades.toFloat()
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = labelColor.toArgb()
                    textSize = 9.dp.toPx()
                }
                listOf(0f, .5f, 1f).forEach { fraction ->
                    val lineY = bottom - (bottom - top) * fraction
                    drawLine(gridColor, Offset(left, lineY), Offset(right, lineY), 1.dp.toPx())
                    drawContext.canvas.nativeCanvas.drawText((maxTrades * fraction).roundToLong().toString(), 0f, lineY + 4.dp.toPx(), paint)
                    val rightLabel = (maxListings * fraction).roundToLong().toString()
                    drawContext.canvas.nativeCanvas.drawText(rightLabel, size.width - paint.measureText(rightLabel), lineY + 4.dp.toPx(), paint)
                }
                val barWidth = ((right - left) / points.size.coerceAtLeast(1) * .55f).coerceIn(4.dp.toPx(), 24.dp.toPx())
                points.forEach { point ->
                    val height = tradeHeight(point.tradeCount)
                    drawRect(barColor.copy(alpha = .55f), Offset(x(point.date) - barWidth / 2, bottom - height), androidx.compose.ui.geometry.Size(barWidth, height))
                }
                val listingPoints = points.mapNotNull { point -> point.listingCount?.let { point to Offset(x(point.date), listingY(it)) } }
                splitActivitySegments(listingPoints, period).forEach { segment ->
                    val path = Path()
                    segment.forEachIndexed { index, (_, point) -> if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y) }
                    if (segment.size > 1) drawPath(path, color, style = Stroke(2.5.dp.toPx()))
                    segment.forEach { (_, point) -> drawCircle(color, 3.5.dp.toPx(), point) }
                }
                listOf(startDate, startDate.plusDays(dayRange / 2), today).forEach { date ->
                    val label = formatAxisDate(date, period)
                    val labelX = x(date).coerceIn(left + paint.measureText(label) / 2, right - paint.measureText(label) / 2)
                    drawContext.canvas.nativeCanvas.drawText(label, labelX - paint.measureText(label) / 2, size.height, paint)
                }
                selectedDate?.let { date -> drawLine(labelColor.copy(alpha = .7f), Offset(x(date), top), Offset(x(date), bottom), 1.dp.toPx()) }
            }
            selectedDate?.let { date ->
                val point = points.firstOrNull { it.date == date } ?: return@let
                Surface(
                    Modifier.align(Alignment.TopEnd).padding(3.dp),
                    shape = RoundedCornerShape(9.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp,
                ) {
                    Column(Modifier.padding(horizontal = 9.dp, vertical = 6.dp)) {
                        Text(formatChartDate(date, period), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                        Text("매매 ${point.tradeCount}건 · 매물 ${point.listingCount?.let { "${it}개" } ?: "기록 없음"}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompareSummaryPager(
    targets: List<PropertyWatchTargetEntity>,
    state: PropertyTrackerUiState,
    colorsByTarget: Map<String, Color>,
) {
    val pagerState = rememberPagerState { 3 }
    val summaryHeight = (92 + targets.size.coerceAtMost(4) * 30).dp
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("주요 요약", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().height(summaryHeight), pageSpacing = 10.dp) { page ->
            val rows = when (page) {
                0 -> targets.map { target ->
                    val median = recentTradeMedian(state.tradesFor(target.id), LocalDate.now())
                    SummaryRow(target, median, median?.let(::formatWon) ?: emptyActualText(target))
                }.sortedByDescending { it.numericValue ?: Long.MIN_VALUE }
                1 -> targets.map { target ->
                    val returnRate = oneYearReturn(state.tradesFor(target.id), LocalDate.now())
                    SummaryRow(target, returnRate?.times(100)?.roundToLong(), returnRate?.let { "%+.1f%%".format(it) } ?: "계산 불가")
                }.sortedByDescending { it.numericValue ?: Long.MIN_VALUE }
                else -> targets.map { target ->
                    val gap = currentAskingGapPercent(state, target.id)
                    SummaryRow(target, gap?.times(100)?.roundToLong(), gap?.let { "%+.1f%%".format(it) } ?: "계산 불가")
                }.sortedByDescending { it.numericValue ?: Long.MIN_VALUE }
            }
            SummaryRankingCard(listOf("현재 가격 요약", "1년 상승률", "호가 괴리율 순위")[page], rows, colorsByTarget)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(3) { index ->
                Box(
                    Modifier.padding(horizontal = 3.dp).size(if (pagerState.currentPage == index) 8.dp else 6.dp)
                        .background(if (pagerState.currentPage == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun SummaryRankingCard(title: String, rows: List<SummaryRow>, colorsByTarget: Map<String, Color>) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, fontWeight = FontWeight.Black)
            rows.take(4).forEachIndexed { index, row ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}", Modifier.width(22.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.size(8.dp).background(colorsByTarget[row.target.id] ?: MaterialTheme.colorScheme.primary, CircleShape))
                    Text(row.target.apartmentName, Modifier.padding(start = 7.dp).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(row.displayValue, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun MarketActivityChart(
    targets: List<PropertyWatchTargetEntity>,
    snapshotsByTarget: Map<String, List<PropertyAskingSnapshotEntity>>,
    tradesByTarget: Map<String, List<PropertyActualTradeEntity>>,
    colorsByTarget: Map<String, Color> = emptyMap(),
    modifier: Modifier,
) {
    val months = remember { List(12) { offset -> YearMonth.now().minusMonths((11 - offset).toLong()) } }
    var selection by remember { mutableStateOf<MarketChartSelection?>(null) }
    val listingCounts = targets.associate { target ->
        target.id to snapshotsByTarget[target.id].orEmpty()
            .groupBy { YearMonth.from(LocalDate.ofEpochDay(it.epochDay)) }
            .mapValues { (_, values) -> values.maxByOrNull(PropertyAskingSnapshotEntity::epochDay)?.activeCount ?: 0 }
    }
    val saleCounts = targets.associate { target ->
        target.id to tradesByTarget[target.id].orEmpty()
            .mapNotNull { parseTradeDate(it.tradeDate) }
            .groupingBy(YearMonth::from)
            .eachCount()
    }
    val maxListings = listingCounts.values.flatMap { it.values }.maxOrNull()?.coerceAtLeast(1) ?: 1
    val maxSales = saleCounts.values.flatMap { it.values }.maxOrNull()?.coerceAtLeast(1) ?: 1
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(targets, listingCounts, saleCounts, maxListings, maxSales) {
                detectTapGestures { tap ->
                    val left = 44.dp.toPx()
                    val right = size.width - 4.dp.toPx()
                    val listingTop = 4.dp.toPx()
                    val listingBottom = size.height * .50f
                    val salesTop = size.height * .61f
                    val salesBottom = size.height - 30.dp.toPx()
                    val monthWidth = (right - left) / months.size
                    val hitRadius = 24.dp.toPx()
                    var nearestDistance = Float.MAX_VALUE
                    var nearest: MarketChartSelection? = null
                    targets.forEachIndexed { targetIndex, target ->
                        val color = colorsByTarget[target.id] ?: comparisonColor(targetIndex)
                        months.forEachIndexed { monthIndex, month ->
                            val centerX = left + monthWidth * (monthIndex + .5f)
                            listingCounts[target.id]?.get(month)?.let { count ->
                                val pointY = listingBottom - (listingBottom - listingTop) * count / maxListings.toFloat()
                                val distance = hypot(tap.x - centerX, tap.y - pointY)
                                if (distance <= hitRadius && distance < nearestDistance) {
                                    nearestDistance = distance
                                    nearest = MarketChartSelection(month, target.apartmentName, "매물", count, color)
                                }
                            }
                            val sales = saleCounts[target.id]?.get(month) ?: 0
                            if (sales > 0) {
                                val groupWidth = monthWidth * .7f
                                val barWidth = (groupWidth / targets.size).coerceAtLeast(2.dp.toPx())
                                val barX = left + monthWidth * monthIndex + monthWidth * .15f + barWidth * targetIndex
                                val barHeight = (salesBottom - salesTop) * sales / maxSales.toFloat()
                                val horizontalDistance = when {
                                    tap.x < barX -> barX - tap.x
                                    tap.x > barX + barWidth -> tap.x - (barX + barWidth)
                                    else -> 0f
                                }
                                val verticalDistance = when {
                                    tap.y < salesBottom - barHeight -> salesBottom - barHeight - tap.y
                                    tap.y > salesBottom -> tap.y - salesBottom
                                    else -> 0f
                                }
                                val distance = hypot(horizontalDistance, verticalDistance)
                                if (distance <= hitRadius && distance < nearestDistance) {
                                    nearestDistance = distance
                                    nearest = MarketChartSelection(month, target.apartmentName, "매매", sales, color)
                                }
                            }
                        }
                    }
                    selection = nearest
                }
            }
        ) {
            val left = 44.dp.toPx()
            val right = size.width - 4.dp.toPx()
            val listingTop = 4.dp.toPx()
            val listingBottom = size.height * .50f
            val salesTop = size.height * .61f
            val salesBottom = size.height - 30.dp.toPx()
            val monthWidth = (right - left) / months.size
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = labelColor.toArgb()
                textSize = 9.dp.toPx()
            }
            drawContext.canvas.nativeCanvas.drawText("매물 $maxListings", 0f, listingTop + 10.dp.toPx(), paint)
            drawContext.canvas.nativeCanvas.drawText("매매 $maxSales", 0f, salesTop + 10.dp.toPx(), paint)
            drawLine(gridColor, Offset(left, listingBottom), Offset(right, listingBottom), 1.dp.toPx())
            drawLine(gridColor, Offset(left, salesBottom), Offset(right, salesBottom), 1.dp.toPx())

            targets.forEachIndexed { targetIndex, target ->
                val color = colorsByTarget[target.id] ?: comparisonColor(targetIndex)
                val points = months.mapIndexedNotNull { monthIndex, month ->
                    listingCounts[target.id]?.get(month)?.let { count ->
                        val x = left + monthWidth * (monthIndex + .5f)
                        val y = listingBottom - (listingBottom - listingTop) * count / maxListings.toFloat()
                        Offset(x, y)
                    }
                }
                val path = Path()
                points.forEachIndexed { index, point -> if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y) }
                if (points.size > 1) drawPath(path, color, style = Stroke(2.5.dp.toPx()))
                points.forEach { drawCircle(color, 3.5.dp.toPx(), it) }

                months.forEachIndexed { monthIndex, month ->
                    val count = saleCounts[target.id]?.get(month) ?: 0
                    if (count <= 0) return@forEachIndexed
                    val groupWidth = monthWidth * .7f
                    val barWidth = (groupWidth / targets.size).coerceAtLeast(2.dp.toPx())
                    val x = left + monthWidth * monthIndex + monthWidth * .15f + barWidth * targetIndex
                    val height = (salesBottom - salesTop) * count / maxSales.toFloat()
                    drawRect(color.copy(alpha = .75f), Offset(x, salesBottom - height), androidx.compose.ui.geometry.Size(barWidth * .82f, height))
                }
            }
            listOf(0, 5, 11).forEach { index ->
                val centerX = left + monthWidth * (index + .5f)
                val yearLabel = "${months[index].year}년"
                val monthLabel = "${months[index].monthValue}월"
                drawContext.canvas.nativeCanvas.drawText(yearLabel, centerX - paint.measureText(yearLabel) / 2, size.height - 11.dp.toPx(), paint)
                drawContext.canvas.nativeCanvas.drawText(monthLabel, centerX - paint.measureText(monthLabel) / 2, size.height, paint)
            }
        }
        selection?.let { selected ->
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(3.dp),
                shape = RoundedCornerShape(9.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp,
            ) {
                Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(selected.color, CircleShape))
                    Text(
                        "${selected.month.year}년 ${selected.month.monthValue}월 · ${selected.targetName}\n${selected.kind} ${selected.count}${if (selected.kind == "매물") "개" else "건"}",
                        Modifier.padding(start = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun ComparisonSummaryCard(
    target: PropertyWatchTargetEntity,
    state: PropertyTrackerUiState,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val asking = askingRange(state, target.id)
    val actual = actualRange(state, target.id)
    Card(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) color.copy(alpha = .1f) else MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) color else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(color, CircleShape))
                Text(target.apartmentName, Modifier.padding(start = 8.dp).weight(1f), fontWeight = FontWeight.Black)
                Text(targetAreaLabel(target), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                if (selected) "그래프에 표시 중 · 누르면 숨김" else "누르면 그래프에 표시",
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row {
                Text("현재 호가", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatPriceRange(asking), fontWeight = FontWeight.Bold)
            }
            Row {
                Text("최근 실거래", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatPriceRange(actual, emptyActualText(target)), fontWeight = FontWeight.Bold)
            }
        }
    }
}

private enum class PriceChartPeriod(val label: String) {
    WEEK("주"), MONTH("월"), YEAR("년");

    fun startDate(today: LocalDate): LocalDate = when (this) {
        WEEK -> today.minusDays(6)
        MONTH -> today.minusMonths(1)
        YEAR -> today.minusYears(1)
    }
}

private data class ActualRangePoint(
    val targetId: String,
    val targetName: String,
    val date: LocalDate,
    val minPriceKrw: Long,
    val maxPriceKrw: Long,
    val count: Int,
)

private data class AskingRangePoint(
    val targetId: String,
    val targetName: String,
    val date: LocalDate,
    val snapshot: PropertyAskingSnapshotEntity,
)

private data class PriceChartSelection(val text: String, val color: Color)

private data class MarketChartSelection(
    val month: YearMonth,
    val targetName: String,
    val kind: String,
    val count: Int,
    val color: Color,
)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRangeBand(
    points: List<Triple<LocalDate, Long, Long>>,
    color: Color,
    x: (LocalDate) -> Float,
    y: (Long) -> Float,
    hatched: Boolean,
) {
    if (points.isEmpty()) return
    if (points.size == 1) {
        val (date, minPrice, maxPrice) = points.first()
        val pointX = x(date)
        val upper = y(maxPrice)
        val lower = y(minPrice)
        drawLine(color.copy(alpha = .75f), Offset(pointX, upper), Offset(pointX, lower), 2.dp.toPx())
        drawCircle(color, 3.5.dp.toPx(), Offset(pointX, upper))
        drawCircle(color, 3.5.dp.toPx(), Offset(pointX, lower))
        return
    }
    val sorted = points.sortedBy { it.first }
    val band = Path()
    sorted.forEachIndexed { index, (date, _, maxPrice) ->
        val point = Offset(x(date), y(maxPrice))
        if (index == 0) band.moveTo(point.x, point.y) else band.lineTo(point.x, point.y)
    }
    sorted.asReversed().forEach { (date, minPrice, _) -> band.lineTo(x(date), y(minPrice)) }
    band.close()
    drawPath(band, color.copy(alpha = if (hatched) .1f else .07f))
    sorted.zipWithNext().forEach { (first, second) ->
        val x1 = x(first.first)
        val x2 = x(second.first)
        drawLine(color.copy(alpha = .72f), Offset(x1, y(first.second)), Offset(x2, y(second.second)), 2.dp.toPx())
        drawLine(color.copy(alpha = .72f), Offset(x1, y(first.third)), Offset(x2, y(second.third)), 2.dp.toPx())
        if (hatched) {
            val width = (x2 - x1).coerceAtLeast(1f)
            var hatchX = x1
            while (hatchX <= x2) {
                val fraction = (hatchX - x1) / width
                val low = first.second + ((second.second - first.second) * fraction).roundToLong()
                val high = first.third + ((second.third - first.third) * fraction).roundToLong()
                drawLine(
                    color.copy(alpha = .3f),
                    Offset(hatchX - 4.dp.toPx(), y(low)),
                    Offset(hatchX + 4.dp.toPx(), y(high)),
                    1.dp.toPx(),
                )
                hatchX += 9.dp.toPx()
            }
        }
    }
    val circles = if (hatched) listOf(sorted.first(), sorted.last()) else sorted
    circles.forEach { (date, minPrice, maxPrice) ->
        drawCircle(color, 3.5.dp.toPx(), Offset(x(date), y(minPrice)))
        drawCircle(color, 3.5.dp.toPx(), Offset(x(date), y(maxPrice)))
    }
}

private enum class ComparePeriod(val label: String) {
    WEEK("1주일"), MONTH("1개월"), THREE_MONTHS("3개월"), SIX_MONTHS("6개월"), YEAR("1년"), THREE_YEARS("3년"), ALL("전체");

    fun startDate(today: LocalDate, earliest: LocalDate? = null): LocalDate = when (this) {
        WEEK -> today.minusDays(6)
        MONTH -> today.minusMonths(1)
        THREE_MONTHS -> today.minusMonths(3)
        SIX_MONTHS -> today.minusMonths(6)
        YEAR -> today.minusYears(1)
        THREE_YEARS -> today.minusYears(3)
        ALL -> earliest ?: today.minusYears(1)
    }

    fun bucket(date: LocalDate): LocalDate = when (this) {
        WEEK, MONTH -> date
        THREE_MONTHS -> date.minusDays(date.dayOfWeek.value.toLong() - 1)
        SIX_MONTHS, YEAR, THREE_YEARS -> date.withDayOfMonth(1)
        ALL -> date.withDayOfYear(1)
    }

    fun adjacent(first: LocalDate, second: LocalDate): Boolean = when (this) {
        WEEK, MONTH -> ChronoUnit.DAYS.between(first, second) <= 1
        THREE_MONTHS -> ChronoUnit.WEEKS.between(first, second) <= 1
        SIX_MONTHS, YEAR, THREE_YEARS -> ChronoUnit.MONTHS.between(YearMonth.from(first), YearMonth.from(second)) <= 1
        ALL -> second.year - first.year <= 1
    }
}

private enum class ComparePriceMode(val label: String) {
    ACTUAL_RANGE("실거래 최저~최고"), ASKING_RANGE("호가 범위"), ASKING_GAP("호가 괴리")
}

private data class ComparePricePoint(
    val targetId: String,
    val targetName: String,
    val date: LocalDate,
    val minValue: Double,
    val maxValue: Double,
    val centerValue: Double?,
    val count: Int,
)

private data class MarketActivityPoint(
    val date: LocalDate,
    val tradeCount: Int,
    val listingCount: Int?,
)

private data class SummaryRow(
    val target: PropertyWatchTargetEntity,
    val numericValue: Long?,
    val displayValue: String,
)

private fun buildComparePricePoints(
    targets: List<PropertyWatchTargetEntity>,
    state: PropertyTrackerUiState,
    period: ComparePeriod,
    mode: ComparePriceMode,
    startDate: LocalDate,
    today: LocalDate,
): List<ComparePricePoint> = targets.flatMap { target ->
    when (mode) {
        ComparePriceMode.ACTUAL_RANGE -> state.tradesFor(target.id)
            .mapNotNull { trade -> parseTradeDate(trade.tradeDate)?.let { it to trade.priceKrw } }
            .filter { it.first in startDate..today }
            .groupBy { period.bucket(it.first) }
            .map { (date, entries) ->
                val prices = entries.map { it.second }.sorted()
                ComparePricePoint(target.id, target.apartmentName, date, prices.first().toDouble(), prices.last().toDouble(), prices.medianLong().toDouble(), prices.size)
            }
        ComparePriceMode.ASKING_RANGE -> state.snapshotsFor(target.id)
            .filter { LocalDate.ofEpochDay(it.epochDay) in startDate..today && it.minPriceKrw > 0 && it.maxPriceKrw > 0 }
            .groupBy { period.bucket(LocalDate.ofEpochDay(it.epochDay)) }
            .mapNotNull { (date, entries) -> entries.maxByOrNull(PropertyAskingSnapshotEntity::epochDay)?.let { snapshot ->
                ComparePricePoint(
                    target.id,
                    target.apartmentName,
                    date,
                    snapshot.minPriceKrw.toDouble(),
                    snapshot.maxPriceKrw.toDouble(),
                    snapshot.medianPriceKrw.takeIf { it > 0 }?.toDouble(),
                    snapshot.activeCount,
                )
            } }
        ComparePriceMode.ASKING_GAP -> state.snapshotsFor(target.id)
            .filter { LocalDate.ofEpochDay(it.epochDay) in startDate..today && it.medianPriceKrw > 0 }
            .groupBy { period.bucket(LocalDate.ofEpochDay(it.epochDay)) }
            .mapNotNull { (date, entries) ->
                val snapshot = entries.maxByOrNull(PropertyAskingSnapshotEntity::epochDay) ?: return@mapNotNull null
                val snapshotDate = LocalDate.ofEpochDay(snapshot.epochDay)
                val actualMedian = state.tradesFor(target.id).mapNotNull { trade ->
                    parseTradeDate(trade.tradeDate)?.takeIf { it in snapshotDate.minusMonths(3)..snapshotDate }?.let { trade.priceKrw }
                }.medianLongOrNull() ?: return@mapNotNull null
                val gap = (snapshot.medianPriceKrw - actualMedian) * 100.0 / actualMedian
                ComparePricePoint(target.id, target.apartmentName, date, gap, gap, gap, snapshot.activeCount)
            }
    }
}.sortedBy(ComparePricePoint::date)

private fun splitPriceSegments(points: List<ComparePricePoint>, period: ComparePeriod): List<List<ComparePricePoint>> {
    if (points.isEmpty()) return emptyList()
    val result = mutableListOf<MutableList<ComparePricePoint>>()
    points.sortedBy(ComparePricePoint::date).forEach { point ->
        val current = result.lastOrNull()
        if (current == null || !period.adjacent(current.last().date, point.date)) result += mutableListOf(point)
        else current += point
    }
    return result
}

private fun splitActivitySegments(
    points: List<Pair<MarketActivityPoint, Offset>>,
    period: ComparePeriod,
): List<List<Pair<MarketActivityPoint, Offset>>> {
    if (points.isEmpty()) return emptyList()
    val result = mutableListOf<MutableList<Pair<MarketActivityPoint, Offset>>>()
    points.forEach { point ->
        val current = result.lastOrNull()
        if (current == null || !period.adjacent(current.last().first.date, point.first.date)) result += mutableListOf(point)
        else current += point
    }
    return result
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCompareRangeSegment(
    points: List<ComparePricePoint>,
    color: Color,
    x: (LocalDate) -> Float,
    y: (Double) -> Float,
    hatched: Boolean,
) {
    if (points.isEmpty()) return
    val sorted = points.sortedBy(ComparePricePoint::date)
    if (sorted.size == 1) {
        val point = sorted.first()
        drawLine(color.copy(alpha = .75f), Offset(x(point.date), y(point.maxValue)), Offset(x(point.date), y(point.minValue)), 2.dp.toPx())
        drawCircle(color, 3.5.dp.toPx(), Offset(x(point.date), y(point.maxValue)))
        drawCircle(color, 3.5.dp.toPx(), Offset(x(point.date), y(point.minValue)))
        point.centerValue?.let { drawCircle(color, 3.dp.toPx(), Offset(x(point.date), y(it))) }
        return
    }
    val band = Path()
    sorted.forEachIndexed { index, point ->
        if (index == 0) band.moveTo(x(point.date), y(point.maxValue)) else band.lineTo(x(point.date), y(point.maxValue))
    }
    sorted.asReversed().forEach { band.lineTo(x(it.date), y(it.minValue)) }
    band.close()
    drawPath(band, color.copy(alpha = if (hatched) .11f else .09f))
    if (hatched) {
        sorted.zipWithNext().forEach { (first, second) ->
            val x1 = x(first.date)
            val x2 = x(second.date)
            val width = (x2 - x1).coerceAtLeast(1f)
            var hatchX = x1
            while (hatchX <= x2) {
                val fraction = (hatchX - x1) / width
                val low = first.minValue + (second.minValue - first.minValue) * fraction
                val high = first.maxValue + (second.maxValue - first.maxValue) * fraction
                drawLine(color.copy(alpha = .28f), Offset(hatchX - 4.dp.toPx(), y(low)), Offset(hatchX + 4.dp.toPx(), y(high)), 1.dp.toPx())
                hatchX += 9.dp.toPx()
            }
        }
    }
    val centers = sorted.mapNotNull { point -> point.centerValue?.let { Offset(x(point.date), y(it)) } }
    val centerPath = Path()
    centers.forEachIndexed { index, point -> if (index == 0) centerPath.moveTo(point.x, point.y) else centerPath.lineTo(point.x, point.y) }
    if (centers.size > 1) drawPath(centerPath, color, style = Stroke(2.5.dp.toPx()))
    centers.forEach { drawCircle(color, 3.dp.toPx(), it) }
    listOf(sorted.first(), sorted.last()).distinct().forEach { point ->
        drawCircle(color, 3.5.dp.toPx(), Offset(x(point.date), y(point.minValue)))
        drawCircle(color, 3.5.dp.toPx(), Offset(x(point.date), y(point.maxValue)))
    }
}

private fun formatComparePoint(point: ComparePricePoint, mode: ComparePriceMode): String = when (mode) {
    ComparePriceMode.ASKING_GAP -> "%+.1f%%".format(point.centerValue ?: point.minValue)
    else -> formatPriceRange(PriceRange(point.minValue.roundToLong(), point.maxValue.roundToLong())) +
        if (mode == ComparePriceMode.ACTUAL_RANGE) " · ${point.count}건" else ""
}

private fun recentTradeMedian(trades: List<PropertyActualTradeEntity>, today: LocalDate): Long? = trades.mapNotNull { trade ->
    parseTradeDate(trade.tradeDate)?.takeIf { it in today.minusMonths(3)..today }?.let { trade.priceKrw }
}.medianLongOrNull()

private fun oneYearReturn(trades: List<PropertyActualTradeEntity>, today: LocalDate): Double? {
    val current = recentTradeMedian(trades, today) ?: return null
    val base = trades.mapNotNull { trade ->
        parseTradeDate(trade.tradeDate)?.takeIf { it in today.minusMonths(15)..today.minusMonths(9) }?.let { trade.priceKrw }
    }.medianLongOrNull() ?: return null
    return (current - base) * 100.0 / base
}

private fun currentAskingGapPercent(state: PropertyTrackerUiState, targetId: String): Double? {
    val asking = PropertyTrackerRepository.groupDuplicateListings(state.activeListingsFor(targetId))
        .map { it.representative.priceKrw }.filter { it > 0 }.medianLongOrNull() ?: return null
    val actual = recentTradeMedian(state.tradesFor(targetId), LocalDate.now()) ?: return null
    return (asking - actual) * 100.0 / actual
}

private fun List<Long>.medianLongOrNull(): Long? = if (isEmpty()) null else sorted().medianLong()
private fun List<Long>.medianLong(): Long = if (size % 2 == 1) this[size / 2] else (this[size / 2 - 1] + this[size / 2]) / 2

private data class PriceRange(val min: Long = 0L, val max: Long = 0L) {
    val isAvailable: Boolean get() = min > 0L && max > 0L
}

private fun askingRange(state: PropertyTrackerUiState, id: String): PriceRange {
    val prices = PropertyTrackerRepository.groupDuplicateListings(state.activeListingsFor(id))
        .map { it.representative.priceKrw }
        .filter { it > 0 }
    return PriceRange(prices.minOrNull() ?: 0L, prices.maxOrNull() ?: 0L)
}

private fun actualRange(state: PropertyTrackerUiState, id: String): PriceRange =
    state.tradesFor(id).map(PropertyActualTradeEntity::priceKrw).filter { it > 0 }.let { prices ->
        PriceRange(prices.minOrNull() ?: 0L, prices.maxOrNull() ?: 0L)
    }

private fun hasActualTradeSyncFailure(target: PropertyWatchTargetEntity): Boolean =
    target.lastSyncMessage.contains("실거래 조회 실패")

private fun emptyActualText(target: PropertyWatchTargetEntity): String =
    if (hasActualTradeSyncFailure(target)) "실거래 조회 지연" else "최근 18개월 신고 거래 없음"

private fun formatPriceRange(range: PriceRange, emptyText: String = "기록 없음"): String = when {
    !range.isAvailable -> emptyText
    range.min == range.max -> formatWon(range.min)
    else -> "${formatWon(range.min)} ~ ${formatWon(range.max)}"
}

private fun parseTradeDate(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()

private fun formatTradeDate(value: String): String = parseTradeDate(value)
    ?.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일"))
    ?: value

private fun formatSyncTime(value: Long): String = Instant.ofEpochMilli(value)
    .atZone(ZoneId.of("Asia/Seoul"))
    .format(DateTimeFormatter.ofPattern("M월 d일 HH:mm"))

private fun formatEokAxis(value: Long): String = "%.2f억".format(Locale.KOREA, value / 100_000_000.0)

private fun formatAxisDate(date: LocalDate, period: PriceChartPeriod): String = date.format(
    DateTimeFormatter.ofPattern(if (period == PriceChartPeriod.YEAR) "yyyy.M" else "yyyy.M.d"),
)

private fun formatChartDate(date: LocalDate, period: PriceChartPeriod): String = date.format(
    DateTimeFormatter.ofPattern(if (period == PriceChartPeriod.YEAR) "yyyy년 M월" else "yyyy년 M월 d일"),
)

private fun formatAxisDate(date: LocalDate, period: ComparePeriod): String = date.format(
    DateTimeFormatter.ofPattern(
        when (period) {
            ComparePeriod.WEEK, ComparePeriod.MONTH -> "yyyy.M.d"
            else -> "yyyy.M"
        },
    ),
)

private fun formatChartDate(date: LocalDate, period: ComparePeriod): String = date.format(
    DateTimeFormatter.ofPattern(
        when (period) {
            ComparePeriod.WEEK, ComparePeriod.MONTH -> "yyyy년 M월 d일"
            else -> "yyyy년 M월"
        },
    ),
)

private fun comparisonColor(index: Int): Color = listOf(
    Color(0xFF4556D7),
    Color(0xFFE2576A),
    Color(0xFF1E9B70),
    Color(0xFFF08A35),
    Color(0xFF8B5DC8),
)[index % 5]

private fun targetAreaLabel(target: PropertyWatchTargetEntity): String {
    val typeCount = NaverLandProvider.parseAreaNos(target.areaNo).size
    return if (typeCount > 1) "전용 ${formatArea(target.exclusiveAreaSqm)}㎡대 · 타입 ${typeCount}개"
    else "전용 ${formatArea(target.exclusiveAreaSqm)}㎡"
}

private fun toggleSimilarAreaSelection(
    selected: Set<String>,
    option: NaverLandArea,
    allAreas: List<NaverLandArea>,
): Set<String> {
    if (option.areaNo in selected) return selected - option.areaNo
    val anchor = allAreas.firstOrNull { it.areaNo in selected }
    return if (anchor == null || abs(anchor.exclusiveAreaSqm - option.exclusiveAreaSqm) <= 2.0) {
        selected + option.areaNo
    } else {
        setOf(option.areaNo)
    }
}

private fun formatWon(value: Long): String {
    if (value == 0L) return "기록 없음"
    val sign = if (value < 0) "-" else ""
    val absolute = kotlin.math.abs(value)
    val eok = absolute / 100_000_000L
    val man = absolute % 100_000_000L / 10_000L
    return when {
        eok > 0 && man > 0 -> "$sign${eok}억 ${NumberFormat.getIntegerInstance(Locale.KOREA).format(man)}만"
        eok > 0 -> "$sign${eok}억"
        else -> "$sign${NumberFormat.getIntegerInstance(Locale.KOREA).format(man)}만원"
    }
}

private fun formatArea(value: Double): String = if (value % 1.0 == 0.0) value.roundToLong().toString() else "%.1f".format(value)
private fun syncColor(status: String) = when (status) { "SUCCESS" -> Color(0xFF21845A); "FAILED" -> Color(0xFFC6464E); else -> Color(0xFF8B6D13) }

private fun Modifier.propertyEdgeSwipe(
    currentPage: Int,
    pageCount: Int,
    onSwipeEdgeLeft: () -> Unit,
    onSwipeEdgeRight: () -> Unit,
): Modifier = pointerInput(currentPage, pageCount) {
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
                        if (dx < 0 && currentPage == pageCount - 1) onSwipeEdgeLeft()
                        if (dx > 0 && currentPage == 0) onSwipeEdgeRight()
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
