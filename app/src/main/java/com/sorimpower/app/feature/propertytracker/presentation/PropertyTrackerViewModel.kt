package com.sorimpower.app.feature.propertytracker.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sorimpower.app.feature.propertytracker.data.AddPropertyTarget
import com.sorimpower.app.feature.propertytracker.data.NaverLandComplex
import com.sorimpower.app.feature.propertytracker.data.NaverLandComplexDetail
import com.sorimpower.app.feature.propertytracker.data.PropertyActualTradeEntity
import com.sorimpower.app.feature.propertytracker.data.PropertyAiAnalyzer
import com.sorimpower.app.feature.propertytracker.data.PropertyAskingSnapshotEntity
import com.sorimpower.app.feature.propertytracker.data.PropertyListingEntity
import com.sorimpower.app.feature.propertytracker.data.PropertyListingEventEntity
import com.sorimpower.app.feature.propertytracker.data.PropertySyncRunEntity
import com.sorimpower.app.feature.propertytracker.data.PropertyTrackerRepository
import com.sorimpower.app.feature.propertytracker.data.PropertyWatchTargetEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PropertyTrackerUiState(
    val targets: List<PropertyWatchTargetEntity> = emptyList(),
    val listings: List<PropertyListingEntity> = emptyList(),
    val snapshots: List<PropertyAskingSnapshotEntity> = emptyList(),
    val trades: List<PropertyActualTradeEntity> = emptyList(),
    val events: List<PropertyListingEventEntity> = emptyList(),
    val latestRun: PropertySyncRunEntity? = null,
) {
    fun listingsFor(targetId: String) = listings.filter { it.watchTargetId == targetId }
    fun activeListingsFor(targetId: String) = listings.filter { it.watchTargetId == targetId && it.status == "ACTIVE" }
    fun snapshotsFor(targetId: String) = snapshots.filter { it.watchTargetId == targetId }.sortedBy { it.epochDay }
    fun tradesFor(targetId: String) = trades.filter { it.watchTargetId == targetId }
}

data class PropertyAiUiState(
    val loading: Boolean = false,
    val title: String = "",
    val content: String? = null,
)

class PropertyTrackerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PropertyTrackerRepository(application)
    private val aiAnalyzer = PropertyAiAnalyzer()
    private val core = combine(
        repository.targets,
        repository.listings,
        repository.snapshots,
        repository.trades,
    ) { targets, listings, snapshots, trades ->
        PropertyTrackerUiState(targets, listings, snapshots, trades)
    }

    val state: StateFlow<PropertyTrackerUiState> = combine(
        core,
        repository.events,
        repository.latestRun,
    ) { state, events, run -> state.copy(events = events, latestRun = run) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PropertyTrackerUiState())

    val syncing = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val complexSearchResults = MutableStateFlow<List<NaverLandComplex>>(emptyList())
    val selectedComplexDetail = MutableStateFlow<NaverLandComplexDetail?>(null)
    val searchingComplex = MutableStateFlow(false)
    val complexSearchMessage = MutableStateFlow<String?>(null)
    val aiState = MutableStateFlow(PropertyAiUiState())

    fun searchComplexes(keyword: String) = viewModelScope.launch {
        searchingComplex.value = true
        selectedComplexDetail.value = null
        complexSearchMessage.value = null
        runCatching { repository.searchComplexes(keyword) }
            .onSuccess { results ->
                complexSearchResults.value = results
                if (results.isEmpty()) complexSearchMessage.value = "검색된 아파트 단지가 없습니다. 주소를 포함해 다시 검색해 주세요."
            }
            .onFailure { complexSearchMessage.value = it.message ?: "단지 검색에 실패했습니다." }
        searchingComplex.value = false
    }

    fun selectComplex(complex: NaverLandComplex) = viewModelScope.launch {
        searchingComplex.value = true
        complexSearchMessage.value = null
        selectedComplexDetail.value = null
        runCatching { repository.fetchComplexDetail(complex.complexNo) }
            .onSuccess { selectedComplexDetail.value = it }
            .onFailure { complexSearchMessage.value = it.message ?: "평형 정보를 불러오지 못했습니다. 직접 입력해 주세요." }
        searchingComplex.value = false
    }

    fun clearComplexSearch() {
        complexSearchResults.value = emptyList()
        selectedComplexDetail.value = null
        complexSearchMessage.value = null
        searchingComplex.value = false
    }

    fun addTarget(input: AddPropertyTarget) {
        viewModelScope.launch {
            syncing.value = true
            repository.addTarget(input)
                .onSuccess {
                    message.value = "관심 단지를 등록했습니다. 첫 시세를 조회합니다."
                    sync(force = true)
                }
                .onFailure { message.value = it.message }
            syncing.value = false
        }
    }

    fun deleteTarget(id: String) = viewModelScope.launch { repository.deleteTarget(id) }

    fun setCurrentHome(id: String, selected: Boolean) = viewModelScope.launch {
        repository.setCurrentHome(id, selected)
    }

    fun setMoveTarget(id: String, selected: Boolean) = viewModelScope.launch {
        repository.setMoveTarget(id, selected).onFailure { message.value = it.message }
    }

    fun setCompareSelected(id: String, selected: Boolean) = viewModelScope.launch {
        repository.setCompareSelected(id, selected).onFailure { message.value = it.message }
    }

    fun syncNow() = viewModelScope.launch { sync(force = true) }

    fun analyzeListings(targetId: String) = viewModelScope.launch {
        if (aiState.value.loading) return@launch
        val snapshot = state.value
        val target = snapshot.targets.firstOrNull { it.id == targetId } ?: return@launch
        aiState.value = PropertyAiUiState(loading = true, title = "${target.apartmentName} 매물 AI 분석")
        runCatching {
            aiAnalyzer.analyzeListings(target, snapshot.activeListingsFor(target.id), snapshot.tradesFor(target.id))
        }.onSuccess { result ->
            aiState.value = PropertyAiUiState(title = "${target.apartmentName} 매물 AI 분석", content = result)
        }.onFailure { error ->
            aiState.value = PropertyAiUiState()
            message.value = error.message ?: "매물 AI 분석에 실패했습니다."
        }
    }

    fun analyzeComparison() = viewModelScope.launch {
        if (aiState.value.loading) return@launch
        val snapshot = state.value
        val current = snapshot.targets.firstOrNull(PropertyWatchTargetEntity::isCurrentHome)
        val selected = snapshot.targets.filter { it.isCompareSelected && it.id != current?.id }
        if (current == null) {
            message.value = "관심 탭에서 현재 집을 먼저 선택해 주세요."
            return@launch
        }
        if (selected.isEmpty()) {
            message.value = "비교 카드에서 갈아타기 후보를 한 곳 이상 선택해 주세요."
            return@launch
        }
        aiState.value = PropertyAiUiState(loading = true, title = "갈아타기 AI 분석")
        runCatching {
            aiAnalyzer.analyzeComparison(current, selected, snapshot.listings, snapshot.snapshots, snapshot.trades)
        }.onSuccess { result ->
            aiState.value = PropertyAiUiState(title = "갈아타기 AI 분석", content = result)
        }.onFailure { error ->
            aiState.value = PropertyAiUiState()
            message.value = error.message ?: "갈아타기 AI 분석에 실패했습니다."
        }
    }

    fun closeAiAnalysis() {
        if (!aiState.value.loading) aiState.value = PropertyAiUiState()
    }

    private suspend fun sync(force: Boolean) {
        syncing.value = true
        val result = repository.syncAll(force)
        message.value = result.message
        syncing.value = false
    }

    fun clearMessage() { message.value = null }
}
