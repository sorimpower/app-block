package com.sorimpower.app.feature.blocker.presentation

import android.app.Application
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sorimpower.app.feature.blocker.data.BlockerRepository
import com.sorimpower.app.feature.blocker.data.BlockerState
import com.sorimpower.app.feature.blocker.data.BottomNavigationTab
import com.sorimpower.app.feature.blocker.data.StartDestination
import com.sorimpower.app.feature.blocker.domain.BlockSchedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledApp(val label: String, val packageName: String, val icon: Drawable)

class BlockerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BlockerRepository(application)
    val state = repository.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BlockerState())
    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps = _apps.asStateFlow()
    private val _appsLoading = MutableStateFlow(false)
    val appsLoading = _appsLoading.asStateFlow()
    private var appsLoaded = false

    init {
        viewModelScope.launch { repository.migrateBottomNavigation() }
    }

    fun loadApps() {
        if (appsLoaded || _appsLoading.value) return
        viewModelScope.launch {
            _appsLoading.value = true
            try {
                _apps.value = withContext(Dispatchers.IO) { installedApps(getApplication()) }
                appsLoaded = true
            } finally {
                _appsLoading.value = false
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun installedApps(application: Application): List<InstalledApp> =
        application.packageManager.getInstalledApplications(0)
            .asSequence()
            .filter { it.packageName != application.packageName && application.packageManager.getLaunchIntentForPackage(it.packageName) != null }
            .map {
                InstalledApp(
                    label = application.packageManager.getApplicationLabel(it).toString(),
                    packageName = it.packageName,
                    icon = application.packageManager.getApplicationIcon(it),
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()

    fun setEnabled(enabled: Boolean) = viewModelScope.launch { repository.setEnabled(enabled) }
    fun setBlocked(packageName: String, blocked: Boolean) = viewModelScope.launch { repository.setBlocked(packageName, blocked) }
    fun upsertSchedule(schedule: BlockSchedule) = viewModelScope.launch { repository.upsertSchedule(schedule) }
    fun deleteSchedule(id: String) = viewModelScope.launch { repository.deleteSchedule(id) }
    fun setAppSchedules(packageName: String, scheduleIds: Set<String>) = viewModelScope.launch {
        repository.setAppSchedules(packageName, scheduleIds)
    }
    fun setBlockMessage(message: String) = viewModelScope.launch { repository.setBlockMessage(message) }
    fun setStartDestination(destination: StartDestination) = viewModelScope.launch { repository.setStartDestination(destination) }
    fun setBottomNavigationOrder(order: List<BottomNavigationTab>) = viewModelScope.launch { repository.setBottomNavigationOrder(order) }
    fun setPassword(password: String) = viewModelScope.launch { repository.setPassword(password) }
    fun clearPassword() = viewModelScope.launch { repository.clearPassword() }
    fun verifyPassword(password: String, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        onResult(repository.verifyPassword(password))
    }
}
