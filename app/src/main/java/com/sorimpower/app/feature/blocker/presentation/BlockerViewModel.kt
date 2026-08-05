package com.sorimpower.app.feature.blocker.presentation

import android.app.Application
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sorimpower.app.feature.blocker.data.BlockerRepository
import com.sorimpower.app.feature.blocker.data.BlockerState
import com.sorimpower.app.feature.blocker.data.StartDestination
import com.sorimpower.app.feature.blocker.domain.BlockSchedule
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class InstalledApp(val label: String, val packageName: String, val icon: Drawable)

class BlockerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = BlockerRepository(application)
    val state = repository.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BlockerState())
    @Suppress("DEPRECATION")
    val apps: List<InstalledApp> = application.packageManager.getInstalledApplications(0)
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
    fun setPassword(password: String) = viewModelScope.launch { repository.setPassword(password) }
    fun clearPassword() = viewModelScope.launch { repository.clearPassword() }
    fun verifyPassword(password: String, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        onResult(repository.verifyPassword(password))
    }
}
