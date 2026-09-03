package com.sorimpower.app.feature.perspective.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sorimpower.app.feature.perspective.data.InterestPeriod
import com.sorimpower.app.feature.perspective.data.PerspectiveRepository
import com.sorimpower.app.feature.perspective.data.PerspectiveState
import com.sorimpower.app.feature.perspective.data.WatchedVideoEntity
import com.sorimpower.app.feature.perspective.data.WatchedVideoPlayback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PerspectiveViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PerspectiveRepository(application)
    val state = repository.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PerspectiveState())
    private val _analyzing = MutableStateFlow<InterestPeriod?>(null)
    val analyzing = _analyzing.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _watchedVideoPlayback = MutableStateFlow<Map<String, WatchedVideoPlayback>>(emptyMap())
    val watchedVideoPlayback = _watchedVideoPlayback.asStateFlow()
    private val resolvingVideoIds = mutableSetOf<String>()

    init { viewModelScope.launch { repository.initialize() } }

    fun analyze(period: InterestPeriod) = viewModelScope.launch {
        if (_analyzing.value != null) return@launch
        _analyzing.value = period; _message.value = null
        runCatching { repository.analyze(period) }
            .onSuccess { _message.value = "${period.label} 관심 흐름을 업데이트했어요." }
            .onFailure { _message.value = it.message ?: "관심 흐름을 분석하지 못했어요." }
        _analyzing.value = null
    }

    fun deleteWatchRecord(videoId: String) = viewModelScope.launch {
        repository.deleteWatchRecord(videoId); _message.value = "시청 기록을 삭제했어요."
    }
    fun resolveWatchedVideo(video: WatchedVideoEntity) {
        if (video.id in _watchedVideoPlayback.value || !resolvingVideoIds.add(video.id)) return
        viewModelScope.launch {
            runCatching { repository.resolveWatchedVideoPlayback(video) }.getOrNull()?.let { playback ->
                _watchedVideoPlayback.value = _watchedVideoPlayback.value + (video.id to playback)
            }
            resolvingVideoIds.remove(video.id)
        }
    }
    fun clearMessage() { _message.value = null }
}
