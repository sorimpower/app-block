package com.sorimpower.app.feature.auction.presentation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.sorimpower.app.core.ui.AppCobalt
import com.sorimpower.app.core.ui.AppGreen
import com.sorimpower.app.core.ui.AppOrange
import com.sorimpower.app.feature.auction.domain.AuctionItem
import com.sorimpower.app.feature.auction.domain.AuctionListFilter
import com.sorimpower.app.feature.auction.domain.AuctionSortField
import com.sorimpower.app.feature.auction.domain.AuctionSortDirection
import com.sorimpower.app.feature.auction.domain.auctionDateLabel
import com.sorimpower.app.feature.auction.domain.auctionDdayLabel
import com.sorimpower.app.feature.auction.domain.formatAuctionPrice
import com.sorimpower.app.feature.auction.domain.formatAuctionUpdatedAt
import com.sorimpower.app.feature.auction.domain.isAuctionDataStale
import com.sorimpower.app.feature.auction.domain.isRemoved
import com.sorimpower.app.feature.auction.domain.mapSearchQuery
import com.sorimpower.app.feature.auction.domain.removedAtLabel
import java.util.Locale
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuctionScreen(padding: PaddingValues, viewModel: AuctionViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showFilterDialog by remember { mutableStateOf(false) }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize().padding(padding),
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { AuctionSummaryCard(state) }

            item {
                AuctionHeader(
                    state = state,
                    onShowFilter = { showFilterDialog = true },
                    onClearFilter = { viewModel.setFilter(AuctionListFilter()) },
                    onSort = viewModel::setSort,
                    onSortDirection = viewModel::setSortDirection,
                    onSearchQueryChange = viewModel::setSearchQuery,
                    onListModeChange = viewModel::setListMode,
                )
            }

            state.errorMessage?.let { message ->
                item { AuctionErrorCard(message, hasCache = state.hasCache, onRetry = viewModel::refresh) }
            }

            if (state.listMode == AuctionListMode.REMOVED) {
                item { EndedAuctionNotice() }
            }

            when {
                state.items.isNotEmpty() -> items(state.items, key = AuctionItem::itemKey) { item ->
                    AuctionItemCard(
                        item = item,
                        isFavorite = item.itemKey in state.favoriteKeys,
                        onFavoriteChange = { favorite -> viewModel.setFavorite(item.itemKey, favorite) },
                    )
                }
                state.isRefreshing && !state.hasCache -> item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AppCobalt)
                    }
                }
                state.refreshCompleted && state.errorMessage == null -> item {
                    EmptyAuctionCard(
                        isSearchResult = state.searchQuery.isNotBlank() || state.filter.isActive,
                        listMode = state.listMode,
                    )
                }
            }

            item {
                Text(
                    "이 목록은 간편 확인용입니다. 입찰 전 매각기일과 사건 상태를 법원 경매정보에서 다시 확인하세요.",
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
}

@Composable
private fun AuctionSummaryCard(state: AuctionUiState) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Row(
            Modifier.padding(10.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF7C2AE8), Color(0xFFB623E6), Color(0xFFE72A99))))
                .padding(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("REAL ESTATE AUCTION", color = Color.White.copy(alpha = .76f), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (state.refreshCompleted || state.hasCache) "진행 중 경매 ${state.totalCount}건" else "경매 목록 불러오는 중",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    "서울 · 아파트 · 감정가 10억 이상",
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
    onListModeChange: (AuctionListMode) -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).background(AppCobalt.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Gavel, contentDescription = null, tint = AppCobalt)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("경매 검색 및 정렬", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            state.listMode == AuctionListMode.FAVORITES -> "${state.items.size}건 / 관심 ${state.favoriteCount}건"
                            state.listMode == AuctionListMode.REMOVED -> "${state.items.size}건 / 종료 ${state.removedCount}건"
                            state.filter.isActive || state.searchQuery.isNotBlank() -> "${state.items.size}건 / 전체 ${state.totalCount}건"
                            else -> "총 ${state.totalCount}건"
                        },
                        color = AppCobalt,
                        fontWeight = FontWeight.Bold,
                    )
                }
                OutlinedButton(onClick = onShowFilter) {
                    Text(if (state.filter.isActive) "필터 적용됨" else "필터")
                }
            }
            LazyRow(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                FilterChip(
                    selected = state.listMode == AuctionListMode.ACTIVE,
                    onClick = { onListModeChange(AuctionListMode.ACTIVE) },
                    label = { Text("진행 중 ${state.totalCount}") },
                )
                }
                item {
                FilterChip(
                    selected = state.listMode == AuctionListMode.FAVORITES,
                    onClick = { onListModeChange(AuctionListMode.FAVORITES) },
                    label = { Text("관심 사건 ${state.favoriteCount}") },
                    leadingIcon = {
                        Icon(
                            if (state.listMode == AuctionListMode.FAVORITES) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                        )
                    },
                )
                }
                item {
                    FilterChip(
                        selected = state.listMode == AuctionListMode.REMOVED,
                        onClick = { onListModeChange(AuctionListMode.REMOVED) },
                        label = { Text("종료 사건 ${state.removedCount}") },
                    )
                }
            }
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                label = { Text("아파트명 검색") },
                placeholder = { Text("예: 헬리오시티") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searchQuery.isNotBlank()) IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "검색어 지우기")
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
                        Modifier.background(AppOrange.copy(alpha = .12f), RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
                        color = AppOrange,
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
private fun AuctionItemCard(item: AuctionItem, isFavorite: Boolean, onFavoriteChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val mapQuery = item.mapSearchQuery()
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Rounded.CalendarMonth,
                            contentDescription = null,
                            tint = AppCobalt,
                            modifier = Modifier.padding(top = 2.dp).size(19.dp),
                        )
                        Text(
                            if (item.isRemoved) item.removedAtLabel()?.let { "종료 감지 $it" } ?: "종료 감지 시각 미상" else auctionDateLabel(item),
                            Modifier.padding(start = 6.dp).weight(1f),
                            color = AppCobalt,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                        )
                    }
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (item.isRemoved) "상태 미확인" else auctionDdayLabel(item),
                            Modifier.background(AppOrange.copy(alpha = .12f), RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
                            color = AppOrange,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        if (item.isNew && !item.isRemoved) {
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
                OutlinedButton(
                    onClick = { openNaverMap(context, mapQuery) },
                    enabled = mapQuery.isNotBlank(),
                    modifier = Modifier.padding(start = 8.dp).height(38.dp),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Icon(Icons.Rounded.Map, contentDescription = null, modifier = Modifier.size(17.dp))
                    Text("지도", Modifier.padding(start = 4.dp))
                }
            }
            Text(
                item.buildingName.ifBlank { "아파트 경매" },
                Modifier.padding(top = 11.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.address,
                Modifier.padding(top = 3.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.isRemoved) {
                Text(
                    "진행 중 목록에서 제외됨 · 낙찰/취하 여부는 확인되지 않음",
                    Modifier.padding(top = 9.dp).background(AppOrange.copy(alpha = .1f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    color = AppOrange,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text("감정가 ${formatAuctionPrice(item.appraisalPrice)}", fontWeight = FontWeight.Bold)
            Text(
                "최저가 ${formatAuctionPrice(item.minimumPrice)} · ${formatRate(item.minimumPriceRate)}",
                Modifier.padding(top = 3.dp),
                color = AppCobalt,
                fontWeight = FontWeight.Black,
            )
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${item.caseNumber} · 물건 ${item.auctionItemNumber}", style = MaterialTheme.typography.bodySmall)
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
                Text(
                    item.note.replace('\n', ' '),
                    Modifier.padding(top = 8.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)).padding(9.dp),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EndedAuctionNotice() {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppOrange.copy(alpha = .1f)),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(15.dp)) {
            Text("종료 사건 안내", color = AppOrange, fontWeight = FontWeight.Black)
            Text(
                "전날 진행 중 목록에는 있었지만 오늘 목록에서 제외되어 종료 사건으로 분류했습니다. 낙찰, 취하, 변경 등 정확한 사유는 이 데이터만으로 확인할 수 없습니다.",
                Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF2F0)), shape = RoundedCornerShape(18.dp)) {
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
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Gavel, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
            Text(
                when {
                    listMode == AuctionListMode.FAVORITES && isSearchResult -> "현재 검색·필터 조건에 맞는 관심 사건이 없어요."
                    listMode == AuctionListMode.FAVORITES -> "아직 저장한 관심 사건이 없어요."
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
private fun parseDateInput(value: String): LocalDate? = value.takeIf(String::isNotBlank)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
private fun decimalText(value: String): String = value.filter { it.isDigit() || it == '.' }.let { text ->
    val firstDot = text.indexOf('.')
    if (firstDot < 0) text.take(4) else text.substring(0, firstDot).take(4) + "." + text.substring(firstDot + 1).filter(Char::isDigit).take(1)
}
private fun dateText(value: String): String = value.filter { it.isDigit() || it == '-' }.take(10)
