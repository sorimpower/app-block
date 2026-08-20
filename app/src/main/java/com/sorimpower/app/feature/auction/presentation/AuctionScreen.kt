package com.sorimpower.app.feature.auction.presentation

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.json.JSONObject
import com.sorimpower.app.core.ui.AppCobalt
import com.sorimpower.app.core.ui.AppGreen
import com.sorimpower.app.core.ui.AppOrange
import com.sorimpower.app.feature.auction.domain.AuctionItem
import com.sorimpower.app.feature.auction.domain.AuctionAiAnalysis
import com.sorimpower.app.feature.auction.domain.AuctionAiPreferences
import com.sorimpower.app.feature.auction.domain.AuctionAnalysisStatus
import com.sorimpower.app.feature.auction.domain.AuctionRiskLevel
import com.sorimpower.app.feature.auction.domain.AuctionListFilter
import com.sorimpower.app.feature.auction.domain.AuctionSortField
import com.sorimpower.app.feature.auction.domain.auctionCaseNumberForCopy
import com.sorimpower.app.feature.auction.domain.AuctionSortDirection
import com.sorimpower.app.feature.auction.domain.auctionDateLabel
import com.sorimpower.app.feature.auction.domain.auctionDdayLabel
import com.sorimpower.app.feature.auction.domain.calendarTime
import com.sorimpower.app.feature.auction.domain.displayTitle
import com.sorimpower.app.feature.auction.domain.formatAuctionPrice
import com.sorimpower.app.feature.auction.domain.formatAuctionUpdatedAt
import com.sorimpower.app.feature.auction.domain.isAuctionDataStale
import com.sorimpower.app.feature.auction.domain.isAuctionNewToday
import com.sorimpower.app.feature.auction.domain.isRemoved
import com.sorimpower.app.feature.auction.domain.mapSearchQuery
import com.sorimpower.app.feature.auction.domain.removedAtLabel
import java.util.Locale
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuctionScreen(padding: PaddingValues, viewModel: AuctionViewModel, onSwipeEdgeLeft: () -> Unit = {}, onSwipeEdgeRight: () -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showFilterDialog by remember { mutableStateOf(false) }
    var showAiSettingsDialog by remember { mutableStateOf(false) }
    var historyItemToDelete by remember { mutableStateOf<AuctionItem?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(viewModel) { viewModel.refreshIfNeeded() }
    BackHandler(enabled = state.listMode != AuctionListMode.ACTIVE) { viewModel.setListMode(AuctionListMode.ACTIVE) }

    Column(Modifier.fillMaxSize().padding(padding)) {
        AuctionModeTabs(
            state = state,
            onListModeChange = viewModel::setListMode,
        )

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxWidth().weight(1f).horizontalSwipe(
                onSwipeLeft = { moveAuctionTab(state.listMode, 1, viewModel::setListMode, onSwipeEdgeLeft) },
                onSwipeRight = { moveAuctionTab(state.listMode, -1, viewModel::setListMode, onSwipeEdgeRight) },
            ),
        ) {
            LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.listMode == AuctionListMode.ANALYZED) item {
                AuctionAiRecommendationCard(
                    preferences = state.aiPreferences,
                    onOpenSettings = { showAiSettingsDialog = true },
                )
            }

            item {
                AuctionHeader(
                    state = state,
                    onShowFilter = { showFilterDialog = true },
                    onClearFilter = { viewModel.setFilter(AuctionListFilter()) },
                    onSort = viewModel::setSort,
                    onSortDirection = viewModel::setSortDirection,
                    onSearchQueryChange = viewModel::setSearchQuery,
                )
            }

            state.errorMessage?.let { message ->
                item { AuctionErrorCard(message, hasCache = state.hasCache, onRetry = viewModel::refresh) }
            }

            when {
                state.items.isNotEmpty() -> items(state.items, key = AuctionItem::itemKey) { item ->
                    AuctionItemCard(
                        item = item,
                        isFavorite = item.itemKey in state.favoriteKeys,
                        analysis = state.aiAnalyses[item.itemKey],
                        onFavoriteChange = { favorite -> viewModel.setFavorite(item.itemKey, favorite) },
                        onAnalyzeRights = if (item.isRemoved) null else { useTerra -> viewModel.analyzeRights(item, useTerra) },
                        onDeleteHistory = if (item.isRemoved) {
                            { historyItemToDelete = item }
                        } else {
                            null
                        },
                    )
                }
                state.isRefreshing && !state.hasCache -> item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                state.refreshCompleted && state.errorMessage == null -> item {
                    EmptyAuctionCard(
                        isSearchResult = state.searchQuery.isNotBlank() || state.filter.isActive,
                        listMode = state.listMode,
                    )
                }
            }
        }
        }
    }
    if (showFilterDialog) AuctionFilterDialog(
        initial = state.filter,
        onDismiss = { showFilterDialog = false },
        onApply = { filter ->
            viewModel.setFilter(filter)
            showFilterDialog = false
        },
    )
    if (showAiSettingsDialog) AuctionAiSettingsDialog(
        initial = state.aiPreferences,
        onDismiss = { showAiSettingsDialog = false },
        onSave = { preferences ->
            if (preferences.dailyRecommendationEnabled &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            viewModel.saveAiPreferences(preferences)
            showAiSettingsDialog = false
        },
    )
    historyItemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { historyItemToDelete = null },
            title = { Text("이 종료 사건을 삭제할까요?", fontWeight = FontWeight.Black) },
            text = {
                Text("${item.caseNumber.ifBlank { item.buildingName }} 기록과 해당 관심 사건 저장이 함께 삭제됩니다.")
            },
            confirmButton = {
                OutlinedButton(
                    onClick = {
                        viewModel.deleteHistoryItem(item.itemKey)
                        historyItemToDelete = null
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("삭제") }
            },
            dismissButton = {
                OutlinedButton(onClick = { historyItemToDelete = null }) { Text("취소") }
            },
        )
    }
}

private fun moveAuctionTab(current: AuctionListMode, offset: Int, onChange: (AuctionListMode) -> Unit, onEdge: () -> Unit) {
    val tabs = listOf(AuctionListMode.ACTIVE, AuctionListMode.FAVORITES, AuctionListMode.ANALYZED, AuctionListMode.REMOVED)
    val target = (tabs.indexOf(current) + offset).coerceIn(0, tabs.lastIndex)
    if (tabs[target] != current) onChange(tabs[target]) else onEdge()
}

private fun Modifier.horizontalSwipe(onSwipeLeft:()->Unit,onSwipeRight:()->Unit):Modifier=pointerInput(Unit){awaitPointerEventScope{while(true){val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial);var dx=0f;var dy=0f;while(true){val event=awaitPointerEvent(PointerEventPass.Initial);val change=event.changes.firstOrNull{it.id==down.id}?:break;if(!change.pressed){if(abs(dx)>100f&&abs(dx)>abs(dy)*1.2f){if(dx<0)onSwipeLeft() else onSwipeRight()};break};val delta=change.positionChange();dx+=delta.x;dy+=delta.y}}}}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AuctionModeTabs(
    state: AuctionUiState,
    onListModeChange: (AuctionListMode) -> Unit,
) {
    val tabs = listOf(
        AuctionListMode.ACTIVE to "진행",
        AuctionListMode.FAVORITES to "관심",
        AuctionListMode.ANALYZED to "AI",
        AuctionListMode.REMOVED to "종료",
    )
    val selectedIndex = tabs.indexOfFirst { it.first == state.listMode }.coerceAtLeast(0)
    PrimaryTabRow(selectedTabIndex = selectedIndex) {
        tabs.forEachIndexed { index, (mode, label) ->
            val count = when (mode) {
                AuctionListMode.ACTIVE -> state.totalCount
                AuctionListMode.FAVORITES -> state.favoriteCount
                AuctionListMode.ANALYZED -> state.analysisCount
                AuctionListMode.REMOVED -> state.removedCount
            }
            Tab(
                selected = index == selectedIndex,
                onClick = { onListModeChange(mode) },
                text = { Text("$label($count)", maxLines = 1, style = MaterialTheme.typography.labelMedium) },
            )
        }
    }
}

@Composable
fun AuctionCollectionInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("법원 경매 수집 조건", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("• 지역: 서울특별시")
                Text("• 용도: 아파트")
                Text("• 상태: 진행 중")
                Text("• 감정가: 15억 원 이상")
                Text("• 매각기일: 오늘부터 90일 이내")
            }
        },
        confirmButton = { OutlinedButton(onClick = onDismiss) { Text("확인") } },
    )
}

@Composable
private fun AuctionAiRecommendationCard(
    preferences: AuctionAiPreferences,
    onOpenSettings: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = .08f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .13f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("AI 맞춤 경매 추천", fontWeight = FontWeight.Black)
                    Text(
                        if (preferences.dailyRecommendationEnabled) {
                            "매일 오전 ${preferences.notificationHour}시 · 미분석 사건 최대 5건 자동 분석"
                        } else {
                            "예산과 위험 조건에 맞는 사건을 매일 아침 알려드려요."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = onOpenSettings, contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Text("설정")
                }
            }
        }
    }
}

@Composable
private fun AuctionAiSettingsDialog(
    initial: AuctionAiPreferences,
    onDismiss: () -> Unit,
    onSave: (AuctionAiPreferences) -> Unit,
) {
    var enabled by remember(initial) { mutableStateOf(initial.dailyRecommendationEnabled) }
    var minimumBidPrice by remember(initial) { mutableStateOf(initial.minimumBidPrice?.let(::eokText).orEmpty()) }
    var maxBidPrice by remember(initial) { mutableStateOf(initial.maxBidPrice?.let(::eokText).orEmpty()) }
    var minimumScore by remember(initial) { mutableStateOf(initial.minimumSuitabilityScore.toString()) }
    var minimumDiscountRate by remember(initial) { mutableStateOf(initial.minimumDiscountRate?.let(::percentText).orEmpty()) }
    var preferredDistricts by remember(initial) { mutableStateOf(initial.preferredDistricts) }
    var maximumRisk by remember(initial) { mutableStateOf(initial.maximumRiskLevel) }
    var allowOccupied by remember(initial) { mutableStateOf(initial.allowOccupiedProperty) }
    var extraRequest by remember(initial) { mutableStateOf(initial.extraRequest) }
    var notificationHour by remember(initial) { mutableStateOf(initial.notificationHour) }
    val minBidValue = eokValue(minimumBidPrice)
    val maxBidValue = eokValue(maxBidPrice)
    val minScoreValue = minimumScore.toIntOrNull()
    val discountValue = minimumDiscountRate.toDoubleOrNull()
    val valid = (minimumBidPrice.isBlank() || minBidValue != null) &&
        (maxBidPrice.isBlank() || maxBidValue != null) &&
        (minBidValue == null || maxBidValue == null || minBidValue <= maxBidValue) &&
        (minScoreValue != null && minScoreValue in 0..100) &&
        (minimumDiscountRate.isBlank() || (discountValue != null && discountValue in 0.0..99.0))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI 맞춤 경매 추천", fontWeight = FontWeight.Black) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("매일 아침 자동 추천", fontWeight = FontWeight.Bold)
                        Text("법원 문서를 수집해 AI가 최대 5건을 분석합니다.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                OutlinedTextField(
                    value = minimumBidPrice,
                    onValueChange = { minimumBidPrice = decimalText(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("최저매각가격 하한 (억 원)") },
                    placeholder = { Text("예: 20") },
                    supportingText = { Text("추천 대상 가격대의 시작값입니다.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = maxBidPrice,
                    onValueChange = { maxBidPrice = decimalText(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("최저매각가격 상한 (억 원)") },
                    placeholder = { Text("예: 30") },
                    supportingText = { Text("현재 최저매각가격을 기준으로 1차 선별합니다.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = minimumScore,
                    onValueChange = { minimumScore = it.filter(Char::isDigit).take(3) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("추천 최소 AI 적합도 (점)") },
                    supportingText = { Text("50점 이상인 사건만 알림 후보로 포함합니다.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = minimumDiscountRate,
                    onValueChange = { minimumDiscountRate = percentInput(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("최소 할인율 (%)") },
                    placeholder = { Text("예: 20") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                Text("선호 지역", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text("선택하지 않으면 서울 전체를 대상으로 합니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(SEOUL_DISTRICTS) { district ->
                        FilterChip(
                            selected = district in preferredDistricts,
                            onClick = {
                                preferredDistricts = if (district in preferredDistricts) preferredDistricts - district else preferredDistricts + district
                            },
                            label = { Text(district.removeSuffix("구")) },
                        )
                    }
                }
                Text("허용할 최대 위험도", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf(AuctionRiskLevel.LOW, AuctionRiskLevel.MEDIUM, AuctionRiskLevel.HIGH)) { risk ->
                        FilterChip(
                            selected = maximumRisk == risk,
                            onClick = { maximumRisk = risk },
                            label = { Text(risk.riskLabel()) },
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("점유 중인 물건도 허용", fontWeight = FontWeight.Bold)
                        Text("명도 위험을 AI 평가에 반영합니다.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = allowOccupied, onCheckedChange = { allowOccupied = it })
                }
                Text("알림 시각", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf(7, 8, 9)) { hour ->
                        FilterChip(
                            selected = notificationHour == hour,
                            onClick = { notificationHour = hour },
                            label = { Text("오전 ${hour}시") },
                        )
                    }
                }
                OutlinedTextField(
                    value = extraRequest,
                    onValueChange = { extraRequest = it.take(500) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("AI에게 추가로 원하는 조건") },
                    placeholder = { Text("예: 역세권, 실거주 위주, 명도 난이도 낮은 사건") },
                    minLines = 3,
                    maxLines = 5,
                )
                Text(
                    "알림 결과는 공개된 법원 문서 기반의 예비 분석입니다. 등기 원문과 변동 사항은 입찰 전에 별도로 확인해야 합니다.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                if (!valid) Text("가격 범위·최소 점수·할인율 입력값을 확인해 주세요.", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            OutlinedButton(
                enabled = valid,
                onClick = {
                    onSave(
                        AuctionAiPreferences(
                            dailyRecommendationEnabled = enabled,
                            minimumBidPrice = minBidValue,
                            maxBidPrice = maxBidValue,
                            minimumSuitabilityScore = minScoreValue ?: 50,
                            preferredDistricts = preferredDistricts,
                            minimumDiscountRate = discountValue,
                            maximumRiskLevel = maximumRisk,
                            allowOccupiedProperty = allowOccupied,
                            extraRequest = extraRequest.trim(),
                            notificationHour = notificationHour,
                        ),
                    )
                },
            ) { Text("저장") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun AuctionSummaryCard(state: AuctionUiState) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Row(
            Modifier.padding(10.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary)))
                .padding(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Spacer(Modifier.height(4.dp))
                Text(
                    if (state.refreshCompleted || state.hasCache) "진행 중 경매 ${state.totalCount}건" else "경매 목록 불러오는 중",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "서울 · 아파트 · 감정가 15억 이상",
                    color = Color.White.copy(alpha = .8f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Box(
                Modifier.size(54.dp).background(Color.White.copy(alpha = .16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Gavel, contentDescription = null, tint = Color.White, modifier = Modifier.size(29.dp))
            }
        }
    }
}

@Composable
private fun AuctionHeader(
    state: AuctionUiState,
    onShowFilter: () -> Unit,
    onClearFilter: () -> Unit,
    onSort: (AuctionSortField) -> Unit,
    onSortDirection: (AuctionSortDirection) -> Unit,
    onSearchQueryChange: (String) -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("아파트명 검색") },
                placeholder = { Text("예: 헬리오시티") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.searchQuery.isNotBlank()) {
                            IconButton(onClick = { onSearchQueryChange("") }, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.Rounded.Close, contentDescription = "검색어 지우기")
                            }
                        }
                        IconButton(
                            onClick = onShowFilter,
                            modifier = Modifier.padding(end = 4.dp).size(40.dp)
                                .then(
                                    if (state.filter.isActive) {
                                        Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = .12f), CircleShape)
                                    } else {
                                        Modifier
                                    },
                                ),
                        ) {
                            Icon(
                                Icons.Rounded.FilterAlt,
                                contentDescription = if (state.filter.isActive) "필터 적용됨" else "필터",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                singleLine = true,
            )
            if (state.filter.isActive) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onClearFilter) { Text("필터 초기화") }
                }
            }
            Text("정렬", Modifier.padding(top = 14.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            LazyRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val fields = if (state.listMode == AuctionListMode.REMOVED) {
                    AuctionSortField.entries
                } else {
                    AuctionSortField.entries.filterNot { it == AuctionSortField.REMOVED_AT }
                }
                items(fields) { field ->
                    val selected = state.sortField == field
                    FilterChip(
                        selected = selected,
                        onClick = { onSort(field) },
                        label = { Text(field.label) },
                    )
                }
            }
            LazyRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(AuctionSortDirection.entries) { direction ->
                    FilterChip(
                        selected = state.sortDirection == direction,
                        onClick = { onSortDirection(direction) },
                        label = { Text(direction.label) },
                    )
                }
            }
            val updatedAt = formatAuctionUpdatedAt(state.lastUpdatedAt)
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    updatedAt?.let { "데이터 갱신 $it" } ?: "갱신 시각 확인 불가",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.listMode != AuctionListMode.REMOVED && isAuctionDataStale(state.lastUpdatedAt)) {
                    Text(
                        "데이터가 오래되었어요",
                        Modifier.background(MaterialTheme.colorScheme.tertiary.copy(alpha = .12f), RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun AuctionFilterDialog(initial: AuctionListFilter, onDismiss: () -> Unit, onApply: (AuctionListFilter) -> Unit) {
    var minPrice by remember { mutableStateOf(initial.minAppraisalPrice?.let(::billionText).orEmpty()) }
    var maxPrice by remember { mutableStateOf(initial.maxAppraisalPrice?.let(::billionText).orEmpty()) }
    var startDate by remember { mutableStateOf(initial.startAuctionDate?.toString().orEmpty()) }
    var endDate by remember { mutableStateOf(initial.endAuctionDate?.toString().orEmpty()) }
    var minFailedCount by remember { mutableStateOf(initial.minFailedCount?.toString().orEmpty()) }
    var maxFailedCount by remember { mutableStateOf(initial.maxFailedCount?.toString().orEmpty()) }
    val minPriceValue = billionValue(minPrice)
    val maxPriceValue = billionValue(maxPrice)
    val startDateValue = parseDateInput(startDate)
    val endDateValue = parseDateInput(endDate)
    val minFailedValue = minFailedCount.toIntOrNull()
    val maxFailedValue = maxFailedCount.toIntOrNull()
    val valid =
        (minPrice.isBlank() || minPriceValue != null) &&
            (maxPrice.isBlank() || maxPriceValue != null) &&
            (startDate.isBlank() || startDateValue != null) &&
            (endDate.isBlank() || endDateValue != null) &&
            (minFailedCount.isBlank() || minFailedValue != null) &&
            (maxFailedCount.isBlank() || maxFailedValue != null) &&
            (minPriceValue == null || maxPriceValue == null || minPriceValue <= maxPriceValue) &&
            (startDateValue == null || endDateValue == null || startDateValue <= endDateValue) &&
            (minFailedValue == null || maxFailedValue == null || minFailedValue <= maxFailedValue) &&
            (minFailedValue == null || minFailedValue >= 0) &&
            (maxFailedValue == null || maxFailedValue >= 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("경매 목록 필터", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("감정가 (억 원)", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(minPrice, { minPrice = decimalText(it) }, Modifier.weight(1f), label = { Text("최소") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(maxPrice, { maxPrice = decimalText(it) }, Modifier.weight(1f), label = { Text("최대") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                Text("매각기일", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(startDate, { startDate = dateText(it) }, Modifier.weight(1f), label = { Text("시작 YYYY-MM-DD") }, singleLine = true)
                    OutlinedTextField(endDate, { endDate = dateText(it) }, Modifier.weight(1f), label = { Text("종료 YYYY-MM-DD") }, singleLine = true)
                }
                Text("유찰 횟수", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(minFailedCount, { minFailedCount = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), label = { Text("최소") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(maxFailedCount, { maxFailedCount = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), label = { Text("최대") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                if (!valid) Text("입력값과 최소·최대 범위를 확인해 주세요.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            OutlinedButton(
                enabled = valid,
                onClick = {
                    onApply(AuctionListFilter(minPriceValue, maxPriceValue, startDateValue, endDateValue, minFailedValue, maxFailedValue))
                },
            ) { Text("적용") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun AuctionItemCard(
    item: AuctionItem,
    isFavorite: Boolean,
    analysis: AuctionAiAnalysis?,
    onFavoriteChange: (Boolean) -> Unit,
    onAnalyzeRights: ((Boolean) -> Unit)?,
    onDeleteHistory: (() -> Unit)?,
) {
    val context = LocalContext.current
    var showModelChooser by remember { mutableStateOf(false) }
    val mapQuery = item.mapSearchQuery()
    val calendarTime = item.calendarTime()
    var noteExpanded by rememberSaveable(item.itemKey) { mutableStateOf(false) }
    var noteCanExpand by remember(item.itemKey) { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Rounded.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 2.dp).size(19.dp),
                        )
                        Text(
                            if (item.isRemoved) item.removedAtLabel()?.let { "종료 감지 $it" } ?: "종료 감지 시각 미상" else auctionDateLabel(item),
                            Modifier.padding(start = 6.dp).weight(1f),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                        )
                    }
                    if (!item.isRemoved) Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            auctionDdayLabel(item),
                            Modifier.background(MaterialTheme.colorScheme.tertiary.copy(alpha = .12f), RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
                            color = MaterialTheme.colorScheme.tertiary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        if (isAuctionNewToday(item.isNew, item.firstSeenAt)) {
                            Text(
                                "신규",
                                Modifier.padding(start = 6.dp).background(AppGreen.copy(alpha = .13f), RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
                                color = AppGreen,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                IconButton(
                    onClick = { onFavoriteChange(!isFavorite) },
                    modifier = Modifier.padding(start = 6.dp).size(38.dp),
                ) {
                    Icon(
                        if (isFavorite) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        contentDescription = if (isFavorite) "관심 사건 저장 해제" else "관심 사건 저장",
                        tint = if (isFavorite) AppCobalt else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                item.displayTitle(),
                Modifier.padding(top = 7.dp).clip(RoundedCornerShape(8.dp))
                    .clickable { openCourtAuctionSite(context) }
                    .padding(vertical = 4.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.address,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                OutlinedButton(
                    onClick = { openNaverMap(context, mapQuery) },
                    enabled = mapQuery.isNotBlank(),
                    modifier = Modifier.padding(start = 8.dp).height(36.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                ) {
                    Icon(Icons.Rounded.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("지도", Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
            if (item.isRemoved) {
                val hasFinalResult = item.finalResultStatus.isNotBlank()
                val isPendingSaleApproval = item.finalResultStatus == "낙찰(매각허가 전)"
                val resultLabel = when {
                    isPendingSaleApproval && item.finalSalePrice > 0L ->
                        "낙찰 결과 · ${formatAuctionPrice(item.finalSalePrice)} · 매각허가 전"
                    item.finalResultStatus == "매각" && item.finalSalePrice > 0L ->
                        "최종 결과 · 낙찰 ${formatAuctionPrice(item.finalSalePrice)}"
                    item.finalResultStatus == "매각" -> "최종 결과 · 낙찰"
                    hasFinalResult -> "최종 결과 · ${item.finalResultStatus}"
                    else -> "낙찰/취하 여부는 추가 확인 필요함"
                }
                Text(
                    resultLabel,
                    Modifier.padding(top = 9.dp).background(
                        (if (hasFinalResult && !isPendingSaleApproval) AppGreen else AppOrange).copy(alpha = .1f),
                        RoundedCornerShape(10.dp),
                    )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    color = if (hasFinalResult && !isPendingSaleApproval) AppGreen else AppOrange,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text("감정가 ${formatAuctionPrice(item.appraisalPrice)}", fontWeight = FontWeight.Bold)
            Text(
                "최저가 ${formatAuctionPrice(item.minimumPrice)} · ${formatRate(item.minimumPriceRate)}",
                Modifier.padding(top = 3.dp),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
            )
            Row(Modifier.fillMaxWidth().padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = item.caseNumber.isNotBlank()) { copyCaseNumber(context, item.caseNumber) }
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${item.caseNumber} · 물건 ${item.auctionItemNumber}",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.caseNumber.isNotBlank()) {
                        Icon(
                            Icons.Rounded.ContentCopy,
                            contentDescription = "사건번호 복사",
                            modifier = Modifier.padding(horizontal = 6.dp).size(15.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text("유찰 ${item.failedCount}회", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
            Text(
                listOf(item.courtName, item.courtDepartment).filter(String::isNotBlank).joinToString(" · "),
                Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.objectCount >= 2) Text("구성물건 ${item.objectCount}개", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall)
            if (item.note.isNotBlank()) {
                Column(
                    Modifier.padding(top = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(enabled = noteCanExpand) { noteExpanded = !noteExpanded }
                        .padding(10.dp),
                ) {
                    Text(
                        item.note.trim(),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = if (noteExpanded) Int.MAX_VALUE else 2,
                        overflow = if (noteExpanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                        onTextLayout = { result ->
                            if (!noteExpanded && result.hasVisualOverflow) noteCanExpand = true
                        },
                    )
                }
            }
            analysis?.let { AuctionAiAnalysisCard(it) }
            if (!item.isRemoved) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                ) {
                    if (onAnalyzeRights != null) {
                        OutlinedButton(
                            onClick = { showModelChooser = true },
                            enabled = analysis?.status != AuctionAnalysisStatus.ANALYZING,
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        ) {
                            if (analysis?.status == AuctionAnalysisStatus.ANALYZING) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(15.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Text(
                                if (analysis == null) "AI 권리분석" else "AI 재분석",
                                Modifier.padding(start = 5.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { openCalendarInsert(context, item) },
                        enabled = calendarTime != null,
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    ) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("캘린더 등록", Modifier.padding(start = 5.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (showModelChooser && onAnalyzeRights != null) AlertDialog(
                onDismissRequest = { showModelChooser = false },
                title = { Text("AI 모델 선택") },
                text = { Text("Luna는 빠르고 비용이 낮고, Terra는 더 정교한 분석에 적합합니다.") },
                confirmButton = { Button(onClick = { showModelChooser = false; onAnalyzeRights(false) }) { Text("Luna") } },
                dismissButton = { OutlinedButton(onClick = { showModelChooser = false; onAnalyzeRights(true) }) { Text("Terra") } },
            )
            if (onDeleteHistory != null) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(
                        onClick = onDeleteHistory,
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("기록 삭제", Modifier.padding(start = 5.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun AuctionAiAnalysisCard(analysis: AuctionAiAnalysis) {
    val (label, color) = when (analysis.status) {
        AuctionAnalysisStatus.ANALYZING -> "법원 문서 수집·AI 분석 중" to AppCobalt
        AuctionAnalysisStatus.WAITING_FOR_DOCUMENTS -> "문서 공개 대기" to AppOrange
        AuctionAnalysisStatus.PRELIMINARY -> "AI 예비 권리분석" to AppOrange
        AuctionAnalysisStatus.COMPLETE -> "AI 권리분석" to AppGreen
        AuctionAnalysisStatus.FAILED -> "AI 분석 실패" to MaterialTheme.colorScheme.error
    }
    Column(
        Modifier.padding(top = 10.dp).fillMaxWidth()
            .background(color.copy(alpha = .08f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = color, modifier = Modifier.size(17.dp))
            Text(label, Modifier.padding(start = 6.dp).weight(1f), color = color, fontWeight = FontWeight.Black)
            if (analysis.status in setOf(AuctionAnalysisStatus.PRELIMINARY, AuctionAnalysisStatus.COMPLETE)) {
                Text("${analysis.suitabilityScore}점", color = color, fontWeight = FontWeight.Black)
            }
        }
        if (analysis.status == AuctionAnalysisStatus.ANALYZING) {
            Text("사건상세·현황조사서·감정평가·매각명세서 정보를 자동으로 확인하고 있어요.", style = MaterialTheme.typography.bodySmall)
            return@Column
        }
        analysis.headline.takeIf(String::isNotBlank)?.let {
            Text(it, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        }
        analysis.summary.takeIf(String::isNotBlank)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
        if (analysis.riskLevel != AuctionRiskLevel.UNKNOWN) {
            Text("위험도 ${analysis.riskLevel.riskLabel()}", color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        if (analysis.riskItems.isNotEmpty()) {
            Text("주요 위험 · ${analysis.riskItems.take(3).map(::auctionAnalysisDisplayText).joinToString(" · ")}", style = MaterialTheme.typography.bodySmall)
        }
        if (analysis.requiredChecks.isNotEmpty()) {
            Text("추가 확인 · ${analysis.requiredChecks.take(3).map(::auctionAnalysisDisplayText).joinToString(" · ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (analysis.status == AuctionAnalysisStatus.PRELIMINARY) {
            Text("등기사항전부증명서 원문이 없어 확정 분석이 아닌 예비 결과입니다.", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

/** 이전에 객체 문자열로 저장된 분석 결과도 사람이 읽을 수 있게 표시한다. */
private fun auctionAnalysisDisplayText(value: String): String {
    val text = value.trim()
    if (!text.startsWith("{")) return text
    return runCatching {
        val json = JSONObject(text)
        listOf("category", "summary", "detail", "reason", "risk", "action")
            .map { json.optString(it).trim() }
            .filter(String::isNotBlank)
            .distinct()
            .joinToString(" · ")
            .ifBlank { text }
    }.getOrDefault(text)
}

private fun AuctionRiskLevel.riskLabel(): String = when (this) {
    AuctionRiskLevel.UNKNOWN -> "미확인"
    AuctionRiskLevel.LOW -> "낮음"
    AuctionRiskLevel.MEDIUM -> "보통"
    AuctionRiskLevel.HIGH -> "높음"
    AuctionRiskLevel.CRITICAL -> "매우 높음"
}

private fun openCalendarInsert(context: Context, item: AuctionItem) {
    val calendarTime = item.calendarTime() ?: return
    val description = buildList {
        if (item.caseNumber.isNotBlank()) add("사건번호: ${item.caseNumber}")
        if (item.auctionItemNumber.isNotBlank()) add("물건번호: ${item.auctionItemNumber}")
        if (item.buildingName.isNotBlank()) add("아파트: ${item.buildingName}")
        add("감정가: ${formatAuctionPrice(item.appraisalPrice)}")
        add("최저가: ${formatAuctionPrice(item.minimumPrice)} (${formatRate(item.minimumPriceRate)})")
        listOf(item.courtName, item.courtDepartment).filter(String::isNotBlank).joinToString(" · ")
            .takeIf(String::isNotBlank)?.let { add("법원: $it") }
        if (item.address.isNotBlank()) add("주소: ${item.address}")
        if (item.note.isNotBlank()) add("비고: ${item.note.trim()}")
        add("법원경매정보: $COURT_AUCTION_SEARCH_URL")
    }.joinToString("\n")
    val location = listOf(item.courtName, item.auctionPlace).filter(String::isNotBlank).joinToString(" ")
    val titleTarget = item.displayTitle()
    val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, calendarTime.startAtMillis)
        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, calendarTime.endAtMillis)
        putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, calendarTime.isAllDay)
        putExtra(CalendarContract.Events.TITLE, "[경매] $titleTarget")
        putExtra(CalendarContract.Events.DESCRIPTION, description)
        putExtra(CalendarContract.Events.EVENT_TIMEZONE, calendarTime.timeZoneId)
        if (location.isNotBlank()) putExtra(CalendarContract.Events.EVENT_LOCATION, location)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "일정을 등록할 캘린더 앱이 없습니다.", Toast.LENGTH_SHORT).show()
    }
}

private fun copyCaseNumber(context: Context, caseNumber: String) {
    val copyValue = auctionCaseNumberForCopy(caseNumber)
    if (copyValue.isBlank()) return
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("법원 경매 사건번호", copyValue))
    Toast.makeText(context, "사건번호 $copyValue 이(가) 복사되었습니다.", Toast.LENGTH_SHORT).show()
}

private fun openCourtAuctionSite(context: Context) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(COURT_AUCTION_SEARCH_URL))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "웹 브라우저를 열 수 없습니다.", Toast.LENGTH_SHORT).show()
    }
}

private fun openNaverMap(context: Context, query: String) {
    if (query.isBlank()) return
    val appUri = Uri.Builder()
        .scheme("nmap")
        .authority("search")
        .appendQueryParameter("query", query)
        .appendQueryParameter("appname", context.packageName)
        .build()
    val appIntent = Intent(Intent.ACTION_VIEW, appUri).addCategory(Intent.CATEGORY_BROWSABLE)
    try {
        context.startActivity(appIntent)
    } catch (_: ActivityNotFoundException) {
        val webUri = Uri.parse("https://map.naver.com/p/search/${Uri.encode(query)}")
        context.startActivity(Intent(Intent.ACTION_VIEW, webUri).addCategory(Intent.CATEGORY_BROWSABLE))
    }
}

@Composable
private fun AuctionErrorCard(message: String, hasCache: Boolean, onRetry: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (hasCache) "최신 정보를 불러오지 못했어요." else message, fontWeight = FontWeight.Bold)
                if (hasCache) Text("저장된 목록을 표시하고 있어요.", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = onRetry) { Text("다시 시도") }
        }
    }
}

@Composable
private fun EmptyAuctionCard(isSearchResult: Boolean, listMode: AuctionListMode) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Gavel, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
            Text(
                when {
                    listMode == AuctionListMode.FAVORITES && isSearchResult -> "현재 검색·필터 조건에 맞는 관심 사건이 없어요."
                    listMode == AuctionListMode.FAVORITES -> "아직 저장한 관심 사건이 없어요."
                    listMode == AuctionListMode.ANALYZED && isSearchResult -> "현재 검색·필터 조건에 맞는 AI 분석 사건이 없어요."
                    listMode == AuctionListMode.ANALYZED -> "아직 완료된 AI 분석 내역이 없어요. 새 경매 사건이 추가되면 자동으로 분석합니다."
                    listMode == AuctionListMode.REMOVED && isSearchResult -> "현재 검색·필터 조건에 맞는 종료 사건이 없어요."
                    listMode == AuctionListMode.REMOVED -> "아직 감지된 종료 사건이 없어요."
                    isSearchResult -> "현재 검색·필터 조건에 맞는 경매가 없어요."
                    else -> "현재 조건에 맞는 진행 중 경매가 없어요."
                },
                Modifier.padding(top = 10.dp),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun formatRate(value: Double): String = if (value % 1.0 == 0.0) "${value.toInt()}%" else "%.1f%%".format(Locale.KOREA, value)

private fun billionText(value: Long): String = "%.1f".format(Locale.KOREA, value / 1_000_000_000.0).trimEnd('0').trimEnd('.')
private fun billionValue(value: String): Long? = value.takeIf(String::isNotBlank)?.toDoubleOrNull()?.times(1_000_000_000.0)?.toLong()
private fun eokText(value: Long): String = "%.1f".format(Locale.KOREA, value / 100_000_000.0).trimEnd('0').trimEnd('.')
private fun eokValue(value: String): Long? = value.takeIf(String::isNotBlank)?.toDoubleOrNull()?.times(100_000_000.0)?.toLong()
private fun percentText(value: Double): String = "%.1f".format(Locale.KOREA, value).trimEnd('0').trimEnd('.')
private fun percentInput(value: String): String = value.filter { it.isDigit() || it == '.' }.let { text ->
    val firstDot = text.indexOf('.')
    if (firstDot < 0) text.take(2) else text.substring(0, firstDot).take(2) + "." + text.substring(firstDot + 1).filter(Char::isDigit).take(1)
}
private fun parseDateInput(value: String): LocalDate? = value.takeIf(String::isNotBlank)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
private fun decimalText(value: String): String = value.filter { it.isDigit() || it == '.' }.let { text ->
    val firstDot = text.indexOf('.')
    if (firstDot < 0) text.take(4) else text.substring(0, firstDot).take(4) + "." + text.substring(firstDot + 1).filter(Char::isDigit).take(1)
}
private fun dateText(value: String): String = value.filter { it.isDigit() || it == '-' }.take(10)

private const val COURT_AUCTION_SEARCH_URL =
    "https://www.courtauction.go.kr/pgj/index.on?w2xPath=/pgj/ui/pgj100/PGJ159M00.xml"

private val SEOUL_DISTRICTS = listOf(
    "강남구", "강동구", "강북구", "강서구", "관악구", "광진구", "구로구", "금천구", "노원구", "도봉구",
    "동대문구", "동작구", "마포구", "서대문구", "서초구", "성동구", "성북구", "송파구", "양천구", "영등포구",
    "용산구", "은평구", "종로구", "중구", "중랑구",
)
