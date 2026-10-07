package com.sorimpower.app.feature.propertytracker.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SwapHoriz
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
                    )
                    PropertyTab.MOVE -> MoveTab(state)
                    PropertyTab.COMPARE -> CompareTab(state, viewModel::setCompareSelected)
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
) {
    val uriHandler = LocalUriHandler.current
    var expanded by remember { mutableStateOf(false) }
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
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "매물 접기" else "매물 보기") }
            if (expanded) {
                groupedListings.sortedWith(
                    compareBy<PropertyListingGroup> { it.representative.status != "ACTIVE" }
                        .thenBy { it.representative.priceKrw },
                ).take(30).forEach { group ->
                        ListingRow(group.representative, group.listings.size) {
                            runCatching { uriHandler.openUri(group.representative.sourceUrl) }
                        }
                    }
            }
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
private fun CompareTab(state: PropertyTrackerUiState, onSelect: (String, Boolean) -> Unit) {
    val selected = state.targets.filter(PropertyWatchTargetEntity::isCompareSelected)
    val targetColors = state.targets.mapIndexed { index, target -> target.id to comparisonColor(index) }.toMap()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("단지 가격 비교", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text("실거래 흐름과 현재 호가 범위를 한눈에 비교해요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected.isEmpty()) {
            item { EmptyCard("비교할 단지를 골라 주세요", "아래 단지 카드를 누르면 그래프에 표시됩니다.") }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ComparisonTimelineChart(selected, state, targetColors, Modifier.fillMaxWidth().height(370.dp))
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("매물·매매 수", fontWeight = FontWeight.Black)
                            Text(
                                "선은 월말 매물 수, 막대는 월별 실거래 건수예요.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            MarketActivityChart(
                                targets = selected,
                                snapshotsByTarget = selected.associate { it.id to state.snapshotsFor(it.id) },
                                tradesByTarget = selected.associate { it.id to state.tradesFor(it.id) },
                                colorsByTarget = targetColors,
                                modifier = Modifier.fillMaxWidth().height(170.dp),
                            )
                        }
                    }
                }
            }
        }
        items(state.targets, key = PropertyWatchTargetEntity::id) { target ->
            ComparisonSummaryCard(
                target = target,
                state = state,
                color = targetColors.getValue(target.id),
                selected = target.isCompareSelected,
                onClick = { onSelect(target.id, !target.isCompareSelected) },
            )
        }
    }
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
    if (hasActualTradeSyncFailure(target)) "실거래 조회 지연" else "최근 1년 신고 거래 없음"

private fun formatPriceRange(range: PriceRange, emptyText: String = "기록 없음"): String = when {
    !range.isAvailable -> emptyText
    range.min == range.max -> formatWon(range.min)
    else -> "${formatWon(range.min)} ~ ${formatWon(range.max)}"
}

private fun parseTradeDate(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()

private fun formatEokAxis(value: Long): String = "%.2f억".format(Locale.KOREA, value / 100_000_000.0)

private fun formatAxisDate(date: LocalDate, period: PriceChartPeriod): String = date.format(
    DateTimeFormatter.ofPattern(if (period == PriceChartPeriod.YEAR) "yyyy.M" else "yyyy.M.d"),
)

private fun formatChartDate(date: LocalDate, period: PriceChartPeriod): String = date.format(
    DateTimeFormatter.ofPattern(if (period == PriceChartPeriod.YEAR) "yyyy년 M월" else "yyyy년 M월 d일"),
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
