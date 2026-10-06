package com.sorimpower.app.feature.propertytracker.presentation

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sorimpower.app.feature.propertytracker.data.AddPropertyTarget
import com.sorimpower.app.feature.propertytracker.data.NaverLandArea
import com.sorimpower.app.feature.propertytracker.data.NaverLandComplex
import com.sorimpower.app.feature.propertytracker.data.PropertyAskingSnapshotEntity
import com.sorimpower.app.feature.propertytracker.data.PropertyListingEntity
import com.sorimpower.app.feature.propertytracker.data.PropertyListingEventEntity
import com.sorimpower.app.feature.propertytracker.data.PropertyTrackerRepository
import com.sorimpower.app.feature.propertytracker.data.PropertyWatchTargetEntity
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

private enum class PropertyTab(val label: String) {
    WATCH("관심"), MOVE("갈아타기"), COMPARE("비교"), CHANGES("변화")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertyTrackerScreen(
    padding: PaddingValues,
    viewModel: PropertyTrackerViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val syncing by viewModel.syncing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(PropertyTab.WATCH) }
    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<PropertyWatchTargetEntity?>(null) }

    Box(Modifier.fillMaxSize().padding(padding)) {
        Column(Modifier.fillMaxSize()) {
            if (syncing) LinearProgressIndicator(Modifier.fillMaxWidth())
            PrimaryTabRow(selectedTabIndex = PropertyTab.entries.indexOf(tab)) {
                PropertyTab.entries.forEach { item ->
                    Tab(
                        selected = tab == item,
                        onClick = { tab = item },
                        text = { Text(item.label, maxLines = 1) },
                    )
                }
            }
            when (tab) {
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
                PropertyTab.CHANGES -> ChangesTab(state)
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
                    Text("매일 오전 8시 시세 기록", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                    Text(
                        state.latestRun?.let { "최근 동기화 ${formatEpochDay(it.epochDay)} · ${syncLabel(it.status)}" }
                            ?: "관심 단지의 매매 호가와 실거래가를 하루 한 번 저장합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                state.activeListingsFor(target.id),
                state.listingsFor(target.id),
                state.snapshotsFor(target.id),
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
    active: List<PropertyListingEntity>,
    all: List<PropertyListingEntity>,
    snapshots: List<PropertyAskingSnapshotEntity>,
    onCurrent: (String, Boolean) -> Unit,
    onMove: (String, Boolean) -> Unit,
    onCompare: (String, Boolean) -> Unit,
    onDelete: (PropertyWatchTargetEntity) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    var expanded by remember { mutableStateOf(false) }
    val sortedPrices = active.map(PropertyListingEntity::priceKrw).sorted()
    val median = PropertyTrackerRepository.medianPrice(sortedPrices)
    val removed = all.count { it.status == "REMOVED" }
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
                        "전용 ${formatArea(target.exclusiveAreaSqm)}㎡ · 단지 ${target.complexNo}" +
                            target.areaNo.takeIf(String::isNotBlank)?.let { " · 평형 $it" }.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onDelete(target) }) { Icon(Icons.Rounded.DeleteOutline, "삭제") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("중앙 호가", formatWon(median), Modifier.weight(1f))
                Metric("현재 매물", "${active.size}개", Modifier.weight(1f))
                Metric("제거 매물", "${removed}개", Modifier.weight(1f))
            }
            if (snapshots.size >= 2) PriceLineChart(snapshots, Modifier.fillMaxWidth().height(96.dp))
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
                    target.lastSyncMessage,
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = syncColor(target.lastSyncStatus),
                )
            }
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "매물 접기" else "매물 보기") }
            if (expanded) {
                all.sortedWith(compareBy<PropertyListingEntity> { it.status != "ACTIVE" }.thenBy { it.priceKrw })
                    .take(30).forEach { listing ->
                        ListingRow(listing) { runCatching { uriHandler.openUri(listing.sourceUrl) } }
                    }
            }
        }
    }
}

@Composable
private fun ListingRow(listing: PropertyListingEntity, open: () -> Unit) {
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
                if (listing.status == "REMOVED") "네이버에서 제거됨" else listOf(listing.direction, listing.tags, listing.description).filter(String::isNotBlank).joinToString(" · "),
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
    val current = state.targets.firstOrNull(PropertyWatchTargetEntity::isCurrentHome)
    val targets = state.targets.filter(PropertyWatchTargetEntity::isMoveTarget)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("내 집과 목표 단지의 갭", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text("호가 중앙값과 최근 실거래 중앙값을 같은 기준으로 비교합니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (current == null || targets.isEmpty()) {
            item { EmptyCard("선택이 더 필요해요", "관심 탭에서 현재 집 1곳과 갈아타기 후보를 최대 5곳 선택해 주세요.") }
        } else {
            val currentAsking = medianAsking(state, current.id)
            val currentActual = medianActual(state, current.id)
            item {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("현재 집 · ${current.apartmentName}", fontWeight = FontWeight.Black)
                        Text("호가 ${formatWon(currentAsking)} · 실거래 ${formatWon(currentActual)}")
                    }
                }
            }
            items(targets, key = PropertyWatchTargetEntity::id) { target ->
                val asking = medianAsking(state, target.id)
                val actual = medianActual(state, target.id)
                Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.SwapHoriz, null, tint = MaterialTheme.colorScheme.primary)
                            Text(target.apartmentName, Modifier.padding(start = 8.dp), fontWeight = FontWeight.Black)
                        }
                        GapRow("호가 중앙값 갭", asking - currentAsking)
                        if (actual > 0 && currentActual > 0) GapRow("실거래 중앙값 갭", actual - currentActual)
                        val minAsking = state.activeListingsFor(target.id).minOfOrNull(PropertyListingEntity::priceKrw) ?: 0
                        val currentMin = state.activeListingsFor(current.id).minOfOrNull(PropertyListingEntity::priceKrw) ?: 0
                        if (minAsking > 0 && currentMin > 0) GapRow("최저 호가 갭", minAsking - currentMin)
                    }
                }
            }
        }
    }
}

@Composable
private fun GapRow(label: String, gap: Long) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            (if (gap >= 0) "+" else "") + formatWon(gap),
            fontWeight = FontWeight.Black,
            color = if (gap > 0) MaterialTheme.colorScheme.error else Color(0xFF24865B),
        )
    }
}

@Composable
private fun CompareTab(state: PropertyTrackerUiState, onSelect: (String, Boolean) -> Unit) {
    val selected = state.targets.filter(PropertyWatchTargetEntity::isCompareSelected)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("단지 가격 변동 비교", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text("최대 5개 단지의 호가 중앙값 변화를 겹쳐 봅니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.targets.forEach { target ->
                    FilterChip(
                        selected = target.isCompareSelected,
                        onClick = { onSelect(target.id, !target.isCompareSelected) },
                        label = { Text("${target.apartmentName} ${formatArea(target.exclusiveAreaSqm)}㎡") },
                    )
                }
            }
        }
        if (selected.isEmpty()) {
            item { EmptyCard("비교할 단지를 골라 주세요", "관심 단지 중 최대 5개를 선택할 수 있습니다.") }
        } else {
            item { ComparisonChart(selected, state, Modifier.fillMaxWidth().height(230.dp)) }
            items(selected, key = PropertyWatchTargetEntity::id) { target ->
                val latest = state.snapshotsFor(target.id).lastOrNull()
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text(target.apartmentName, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text(formatWon(latest?.medianPriceKrw ?: 0))
                }
            }
        }
    }
}

@Composable
private fun ChangesTab(state: PropertyTrackerUiState) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        item {
            Text("매물 변화", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text("신규·가격 변경·재등록·제거 이력을 최근 순서로 보여줍니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.events.isEmpty()) item { EmptyCard("아직 변화 기록이 없어요", "두 번 이상 동기화하면 매물의 등장과 가격 변화를 확인할 수 있습니다.") }
        items(state.events, key = PropertyListingEventEntity::id) { event ->
            val target = state.targets.firstOrNull { it.id == event.watchTargetId }
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Row {
                        Text(eventLabel(event.type), Modifier.weight(1f), fontWeight = FontWeight.Black, color = eventColor(event.type))
                        Text(formatTimestamp(event.occurredAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${target?.apartmentName ?: "삭제된 단지"} · ${formatWon(event.priceKrw)}")
                    if (event.type == "PRICE_CHANGED" && event.previousPriceKrw != null) {
                        Text("${formatWon(event.previousPriceKrw)} → ${formatWon(event.priceKrw)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
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
                                    }) { Text("다른 단지 선택") }
                                }
                            }
                        }
                        selectedDetail?.let { detail ->
                            if (detail.areas.isEmpty()) {
                                item { Text("자동으로 확인된 평형이 없습니다. 아래 직접 입력을 이용해 주세요.", style = MaterialTheme.typography.bodySmall) }
                            } else {
                                item { Text("전용면적 선택", fontWeight = FontWeight.Bold) }
                                items(detail.areas, key = { "${it.areaNo}:${it.exclusiveAreaSqm}" }) { option ->
                                    AreaOption(
                                        option = option,
                                        selected = areaNo == option.areaNo && area.toDoubleOrNull() == option.exclusiveAreaSqm,
                                        onClick = {
                                            areaNo = option.areaNo
                                            area = option.exclusiveAreaSqm.toString().removeSuffix(".0")
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
                            areaNo, { areaNo = it.filter(Char::isDigit) },
                            label = { Text("네이버 평형번호(선택)") },
                            supportingText = { Text("비워두면 전용면적으로 매물을 골라냅니다.") },
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
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)) {
        Column(Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.Black, maxLines = 1)
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
private fun PriceLineChart(points: List<PropertyAskingSnapshotEntity>, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .22f), RoundedCornerShape(12.dp)).padding(8.dp)) {
        drawPricePath(points.map { it.medianPriceKrw }, color)
    }
}

@Composable
private fun ComparisonChart(targets: List<PropertyWatchTargetEntity>, state: PropertyTrackerUiState, modifier: Modifier) {
    val colors = listOf(Color(0xFF4556D7), Color(0xFFE2576A), Color(0xFF1E9B70), Color(0xFFF08A35), Color(0xFF8B5DC8))
    val series = targets.map { state.snapshotsFor(it.id).map(PropertyAskingSnapshotEntity::medianPriceKrw) }
    val all = series.flatten().filter { it > 0 }
    Canvas(modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp)).padding(16.dp)) {
        if (all.isEmpty()) return@Canvas
        val min = all.min()
        val max = all.max()
        series.forEachIndexed { index, prices -> drawPricePath(prices, colors[index % colors.size], min, max) }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPricePath(
    values: List<Long>,
    color: Color,
    forcedMin: Long? = null,
    forcedMax: Long? = null,
) {
    val valid = values.filter { it > 0 }
    if (valid.isEmpty()) return
    val min = forcedMin ?: valid.min()
    val max = forcedMax ?: valid.max()
    val range = (max - min).coerceAtLeast(1)
    val path = Path()
    valid.forEachIndexed { index, value ->
        val x = if (valid.size == 1) size.width / 2 else size.width * index / (valid.size - 1)
        val y = size.height - ((value - min).toFloat() / range * size.height * .82f + size.height * .09f)
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        drawCircle(color, 4.dp.toPx(), Offset(x, y))
    }
    if (valid.size > 1) drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx()))
}

private fun medianAsking(state: PropertyTrackerUiState, id: String): Long =
    PropertyTrackerRepository.medianPrice(state.activeListingsFor(id).map(PropertyListingEntity::priceKrw).sorted())

private fun medianActual(state: PropertyTrackerUiState, id: String): Long =
    PropertyTrackerRepository.medianPrice(state.tradesFor(id).map { it.priceKrw }.sorted())

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
private fun formatEpochDay(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("M월 d일"))
private fun formatTimestamp(value: Long): String = java.time.Instant.ofEpochMilli(value).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M.d HH:mm"))
private fun syncLabel(status: String) = when (status) { "SUCCESS" -> "완료"; "PARTIAL" -> "일부 완료"; "FAILED" -> "실패"; else -> "조회 중" }
private fun syncColor(status: String) = when (status) { "SUCCESS" -> Color(0xFF21845A); "FAILED" -> Color(0xFFC6464E); else -> Color(0xFF8B6D13) }
private fun eventLabel(type: String) = when (type) { "ADDED" -> "신규 매물"; "PRICE_CHANGED" -> "가격 변경"; "REMOVED" -> "매물 제거"; "RELISTED" -> "매물 재등록"; else -> type }
private fun eventColor(type: String) = when (type) { "ADDED", "RELISTED" -> Color(0xFF21845A); "REMOVED" -> Color(0xFF8A818A); else -> Color(0xFFC25B26) }
