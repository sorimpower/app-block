package com.sorimpower.app.feature.assets.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HomeWork
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sorimpower.app.feature.assets.domain.AssetClass
import com.sorimpower.app.feature.assets.domain.AssetDataSourceRegistry
import com.sorimpower.app.feature.assets.domain.AssetItem
import com.sorimpower.app.feature.assets.domain.AssetSnapshot
import com.sorimpower.app.feature.assets.domain.ValuationBadge
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private enum class AssetPage(val label: String) { PORTFOLIO("자산"), HISTORY("히스토리"), SOURCES("데이터 기준") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AssetScreen(
    padding: PaddingValues,
    viewModel: AssetViewModel,
    onSwipeEdgeLeft: () -> Unit,
    onSwipeEdgeRight: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var page by remember { mutableStateOf(AssetPage.PORTFOLIO) }
    var editingDraft by remember { mutableStateOf<AssetDraft?>(null) }
    var selectedAsset by remember { mutableStateOf<AssetItem?>(null) }
    var selectedSnapshot by remember { mutableStateOf<AssetSnapshot?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            val selectedIndex = AssetPage.entries.indexOf(page)
            PrimaryTabRow(selectedTabIndex = selectedIndex) {
                AssetPage.entries.forEachIndexed { index, item ->
                    Tab(
                        selected = index == selectedIndex,
                        onClick = { page = item },
                        text = { Text(item.label, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }
            Box(
                Modifier.fillMaxSize().assetHorizontalSwipe(
                    onSwipeLeft = {
                        when (page) {
                            AssetPage.PORTFOLIO -> page = AssetPage.HISTORY
                            AssetPage.HISTORY -> page = AssetPage.SOURCES
                            AssetPage.SOURCES -> onSwipeEdgeLeft()
                        }
                    },
                    onSwipeRight = {
                        when (page) {
                            AssetPage.SOURCES -> page = AssetPage.HISTORY
                            AssetPage.HISTORY -> page = AssetPage.PORTFOLIO
                            AssetPage.PORTFOLIO -> onSwipeEdgeRight()
                        }
                    },
                ),
            ) {
                when (page) {
                    AssetPage.PORTFOLIO -> PortfolioPage(
                        state = state,
                        onAdd = { editingDraft = AssetDraft() },
                        onSelect = { selectedAsset = it },
                    )
                    AssetPage.HISTORY -> HistoryPage(state.portfolio.snapshots, onSelect = { selectedSnapshot = it })
                    AssetPage.SOURCES -> DataSourcePage()
                }
            }
        }
    }

    editingDraft?.let { draft ->
        AssetEditorDialog(
            initial = draft,
            saving = state.saving,
            lookingUp = state.lookingUpRealEstate,
            onDismiss = { editingDraft = null },
            onLookup = { value, callback -> viewModel.lookupRealEstate(value, callback) },
            onSave = {
                viewModel.save(it)
                editingDraft = null
            },
        )
    }
    selectedAsset?.let { item ->
        AssetDetailDialog(
            item = item,
            onDismiss = { selectedAsset = null },
            onEdit = {
                editingDraft = item.toDraft()
                selectedAsset = null
            },
            onDelete = {
                viewModel.delete(item.id)
                selectedAsset = null
            },
        )
    }
    selectedSnapshot?.let { snapshot ->
        SnapshotDialog(snapshot = snapshot, onDismiss = { selectedSnapshot = null })
    }
}

private fun Modifier.assetHorizontalSwipe(
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
private fun PortfolioPage(state: AssetUiState, onAdd: () -> Unit, onSelect: (AssetItem) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            NetWorthCard(
                netWorth = state.portfolio.netWorthKrw,
                assets = state.portfolio.totalAssetsKrw,
                liabilities = state.portfolio.totalLiabilitiesKrw,
            )
        }
        item {
            Button(modifier = Modifier.fillMaxWidth(), onClick = onAdd) {
                Icon(Icons.Rounded.Add, null)
                Text("자산 추가", modifier = Modifier.padding(start = 7.dp))
            }
        }
        if (state.portfolio.items.isEmpty()) {
            item { EmptyAssetCard() }
        } else {
            AssetClass.entries.forEach { assetClass ->
                val group = state.portfolio.items.filter { it.assetClass == assetClass }
                if (group.isNotEmpty()) {
                    item {
                        Text(assetClass.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 4.dp))
                    }
                    items(group, key = AssetItem::id) { item -> AssetRow(item, onClick = { onSelect(item) }) }
                }
            }
        }
    }
}

@Composable
private fun NetWorthCard(netWorth: Long, assets: Long, liabilities: Long) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .62f)),
        shape = RoundedCornerShape(26.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(22.dp)) {
            Text("내 순자산", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            Text(formatWon(netWorth), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 5.dp))
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryValue("총자산", assets, Modifier.weight(1f))
                SummaryValue("부채", liabilities, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SummaryValue(label: String, value: Long, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = .72f)) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatWon(value), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AssetRow(item: AssetItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(assetColor(item.assetClass).copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(assetIcon(item.assetClass), null, tint = assetColor(item.assetClass), modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.name, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Badge(item.badge, Modifier.padding(start = 7.dp))
                }
                Text(
                    "${item.providerName} · ${item.valuationDate.format(DateTimeFormatter.ofPattern("M.d"))}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatWon(item.valueKrw), fontWeight = FontWeight.Black)
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Badge(badge: ValuationBadge, modifier: Modifier = Modifier) {
    val color = when (badge) {
        ValuationBadge.LIVE -> Color(0xFF147D4A)
        ValuationBadge.ESTIMATED -> Color(0xFF2962B8)
        ValuationBadge.MANUAL -> MaterialTheme.colorScheme.onSurfaceVariant
        ValuationBadge.STALE -> Color(0xFFC06A00)
    }
    Surface(modifier, shape = RoundedCornerShape(7.dp), color = color.copy(alpha = .12f)) {
        Text(badge.label, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
    }
}

@Composable
private fun EmptyAssetCard() {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Savings, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
            Text("아직 등록한 자산이 없어요", fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 10.dp))
            Text("현금부터 가볍게 추가해 보세요.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun HistoryPage(snapshots: List<AssetSnapshot>, onSelect: (AssetSnapshot) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("날짜별 순자산", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text("날짜를 누르면 당시 사용한 데이터 기준을 확인할 수 있어요.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        if (snapshots.isEmpty()) item { EmptyAssetCard() }
        items(snapshots, key = AssetSnapshot::id) { snapshot ->
            Card(Modifier.fillMaxWidth().clickable { onSelect(snapshot) }, shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.History, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(snapshot.date.format(DateTimeFormatter.ofPattern("yyyy.MM.dd")), fontWeight = FontWeight.Bold)
                        Text("총자산 ${formatWon(snapshot.totalAssetsKrw)} · 부채 ${formatWon(snapshot.totalLiabilitiesKrw)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(formatWon(snapshot.netWorthKrw), fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun DataSourcePage() {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("데이터 기준", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text("내 자산 금액은 아래 고정 기준으로 계산돼요.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        items(AssetDataSourceRegistry.policies, key = { it.assetClass.name }) { policy ->
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(assetIcon(policy.assetClass), null, tint = assetColor(policy.assetClass), modifier = Modifier.size(22.dp))
                        Text(policy.assetClass.label, fontWeight = FontWeight.Black, modifier = Modifier.padding(start = 9.dp))
                    }
                    Text(policy.providerName, modifier = Modifier.padding(top = 10.dp), fontWeight = FontWeight.Bold)
                    Text(policy.valuationMethod, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Text(policy.connectionNote, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 7.dp))
                }
            }
        }
        item {
            Text("부동산 추정가는 실제 매도 가능 가격과 다를 수 있으며, 공시가격은 총자산 계산에 포함하지 않아요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
        }
    }
}

@Composable
private fun AssetEditorDialog(
    initial: AssetDraft,
    saving: Boolean,
    lookingUp: Boolean,
    onDismiss: () -> Unit,
    onLookup: (AssetDraft, (AssetDraft) -> Unit) -> Unit,
    onSave: (AssetDraft) -> Unit,
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == null) "자산 추가" else "자산 수정", fontWeight = FontWeight.Black) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(AssetClass.entries) { type ->
                            FilterChip(selected = draft.assetClass == type, onClick = { draft = draft.copy(assetClass = type) }, label = { Text(type.label) })
                        }
                    }
                }
                item { OutlinedTextField(draft.name, { draft = draft.copy(name = it) }, label = { Text(assetNameLabel(draft.assetClass)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                if (draft.assetClass == AssetClass.REAL_ESTATE) {
                    item { OutlinedTextField(draft.address, { draft = draft.copy(address = it) }, label = { Text("주소·단지") }, modifier = Modifier.fillMaxWidth()) }
                    item {
                        OutlinedTextField(
                            value = draft.lawdCd,
                            onValueChange = { draft = draft.copy(lawdCd = it.filter(Char::isDigit).take(5)) },
                            label = { Text("법정동 지역코드 5자리") },
                            supportingText = { Text("예: 서울 강남구 11680 · 시군구 기준 코드") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumberField(draft.exclusiveAreaSqmText, { draft = draft.copy(exclusiveAreaSqmText = it) }, "전용면적 ㎡", Modifier.weight(1f), decimal = true)
                            NumberField(draft.ownershipPercentText, { draft = draft.copy(ownershipPercentText = it) }, "소유지분 %", Modifier.weight(1f), decimal = true)
                        }
                    }
                    item {
                        OutlinedTextField(
                            draft.comparablePricesText,
                            { draft = draft.copy(comparablePricesText = it, comparablesFromMolit = false, latestComparableTradeDate = "") },
                            label = { Text("최근 유사 실거래가 (만원, 쉼표 구분)") },
                            supportingText = { Text("입력하면 V1 중앙값으로 추정하고, 비우면 직접 평가액을 사용해요.") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item {
                        OutlinedButton(
                            enabled = !lookingUp,
                            onClick = { onLookup(draft) { loaded -> draft = loaded } },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (lookingUp) "국토부 실거래 조회 중…" else "국토부 최근 실거래 불러오기")
                        }
                    }
                    if (draft.comparablesFromMolit) {
                        item {
                            Text(
                                "MOLIT_RTMS · ${draft.latestComparableTradeDate} 기준 · ${draft.comparablePricesText.split(',').size}건",
                                color = Color(0xFF147D4A),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                if (draft.assetClass == AssetClass.VEHICLE) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumberField(draft.modelYearText, { draft = draft.copy(modelYearText = it) }, "연식", Modifier.weight(1f))
                            NumberField(draft.mileageKmText, { draft = draft.copy(mileageKmText = it) }, "주행거리 km", Modifier.weight(1f))
                        }
                    }
                    item { OutlinedTextField(draft.trim, { draft = draft.copy(trim = it) }, label = { Text("세부 트림") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                }
                item {
                    NumberField(
                        value = draft.valueKrwText,
                        onValueChange = { draft = draft.copy(valueKrwText = it) },
                        label = if (draft.assetClass == AssetClass.LIABILITY) "남은 부채 (원)" else "현재 평가액 (원)",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item { OutlinedTextField(draft.detail, { draft = draft.copy(detail = it) }, label = { Text("메모·종목·차량번호 등") }, modifier = Modifier.fillMaxWidth()) }
                item {
                    val policy = AssetDataSourceRegistry.policy(draft.assetClass)
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f)) {
                        Text("자동 기준: ${policy.providerName}\n현재 직접 입력한 값은 MANUAL로 명확히 표시됩니다.", modifier = Modifier.fillMaxWidth().padding(11.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = { Button(enabled = !saving, onClick = { onSave(draft) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun NumberField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier, decimal: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter { it.isDigit() || (decimal && it == '.') }) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
private fun AssetDetailDialog(item: AssetItem, onDismiss: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name, fontWeight = FontWeight.Black) },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (item.assetClass == AssetClass.REAL_ESTATE) "실거래 기반 추정가" else if (item.assetClass == AssetClass.VEHICLE) "중고차 추정 시세" else "평가액", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Badge(item.badge, Modifier.padding(start = 8.dp))
                    }
                    Text(formatWon(item.valueKrw), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                }
                if (item.address.isNotBlank()) item { DetailLine("주소·단지", item.address) }
                item.exclusiveAreaSqm?.let { area -> item { DetailLine("전용면적·지분", "${area}㎡ · ${item.ownershipPercent}%") } }
                if (item.modelYear != null || item.mileageKm != null) item { DetailLine("차량 정보", listOfNotNull(item.modelYear?.let { "${it}년식" }, item.mileageKm?.let { "${NumberFormat.getIntegerInstance().format(it)}km" }).joinToString(" · ")) }
                if (item.trim.isNotBlank()) item { DetailLine("트림", item.trim) }
                if (item.comparableMinKrw != null && item.comparableMaxKrw != null) item { DetailLine("최근 유사 실거래", "${formatWon(item.comparableMinKrw)} ~ ${formatWon(item.comparableMaxKrw)} (${item.comparableCount}건)") }
                item { HorizontalDivider() }
                item { DetailLine("데이터 출처", item.providerName) }
                item { DetailLine("평가 방식", item.valuationMethod) }
                item.algorithmVersion?.let { version -> item { DetailLine("평가 알고리즘", version) } }
                item { DetailLine("평가기준일", item.valuationDate.toString()) }
                item.confidence?.let { confidence -> item { DetailLine("추정 신뢰도", confidence.label) } }
                item { DetailLine("상태", item.sourceStatus) }
                if (item.detail.isNotBlank()) item { DetailLine("메모", item.detail) }
            }
        },
        confirmButton = { Button(onClick = onEdit) { Text("수정") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("삭제", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("닫기") }
            }
        },
    )
}

@Composable
private fun SnapshotDialog(snapshot: AssetSnapshot, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(snapshot.date.format(DateTimeFormatter.ofPattern("yyyy.MM.dd")), fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("순자산 ${formatWon(snapshot.netWorthKrw)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                HorizontalDivider()
                Text("당시 데이터 기준", fontWeight = FontWeight.Black)
                DetailLine("주식·ETF", snapshot.stockProvider)
                DetailLine("코인", snapshot.cryptoProvider)
                DetailLine("부동산", "${snapshot.realEstateDataset} · ${snapshot.realEstateAlgorithm}")
                DetailLine("자동차", snapshot.vehicleProvider)
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("확인") } },
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, modifier = Modifier.weight(.38f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Text(value, modifier = Modifier.weight(.62f), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
    }
}

private fun AssetItem.toDraft() = AssetDraft(
    id = id,
    assetClass = assetClass,
    name = name,
    valueKrwText = valueKrw.toString(),
    detail = detail,
    address = address,
    lawdCd = lawdCd,
    exclusiveAreaSqmText = exclusiveAreaSqm?.toString().orEmpty(),
    ownershipPercentText = ownershipPercent.toString(),
    modelYearText = modelYear?.toString().orEmpty(),
    trim = trim,
    mileageKmText = mileageKm?.toString().orEmpty(),
)

private fun assetNameLabel(type: AssetClass) = when (type) {
    AssetClass.STOCK_ETF -> "종목명"
    AssetClass.CRYPTO -> "코인명"
    AssetClass.REAL_ESTATE -> "단지·부동산 이름"
    AssetClass.VEHICLE -> "차량 모델"
    AssetClass.CASH -> "계좌·현금 이름"
    AssetClass.LIABILITY -> "대출·부채 이름"
}

private fun assetIcon(type: AssetClass): ImageVector = when (type) {
    AssetClass.STOCK_ETF -> Icons.Rounded.TrendingUp
    AssetClass.CRYPTO -> Icons.Rounded.Savings
    AssetClass.REAL_ESTATE -> Icons.Rounded.HomeWork
    AssetClass.VEHICLE -> Icons.Rounded.DirectionsCar
    AssetClass.CASH -> Icons.Rounded.Payments
    AssetClass.LIABILITY -> Icons.Rounded.AccountBalance
}

@Composable
private fun assetColor(type: AssetClass): Color = when (type) {
    AssetClass.STOCK_ETF -> Color(0xFF2962B8)
    AssetClass.CRYPTO -> Color(0xFFF0A000)
    AssetClass.REAL_ESTATE -> Color(0xFF147D4A)
    AssetClass.VEHICLE -> Color(0xFF6A45B8)
    AssetClass.CASH -> MaterialTheme.colorScheme.primary
    AssetClass.LIABILITY -> MaterialTheme.colorScheme.error
}

private fun formatWon(value: Long?): String = value?.let { "₩ ${NumberFormat.getIntegerInstance(Locale.KOREA).format(it)}" } ?: "-"
