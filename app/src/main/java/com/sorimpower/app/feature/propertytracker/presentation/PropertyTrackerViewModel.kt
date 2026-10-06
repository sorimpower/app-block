package com.sorimpower.app.feature.propertytracker.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sorimpower.app.feature.propertytracker.data.AddPropertyTarget
import com.sorimpower.app.feature.propertytracker.data.PropertyActualTradeEntity
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

class PropertyTrackerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PropertyTrackerRepository(application)
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

    fun syncNow() = viewModelScope.launch { sync(force = false) }

    private suspend fun sync(force: Boolean) {
        syncing.value = true
        val result = repository.syncAll(force)
        message.value = result.message
        syncing.value = false
    }

    fun clearMessage() { message.value = null }
}
