package com.sorimpower.app.feature.perspective.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sorimpower.app.feature.perspective.data.PerspectiveRepository
import com.sorimpower.app.feature.perspective.data.PerspectiveState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PerspectiveViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PerspectiveRepository(application)
    val state = repository.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PerspectiveState())
    private val _busyVideoId = MutableStateFlow<String?>(null)
    val busyVideoId = _busyVideoId.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    init { viewModelScope.launch { repository.initialize() } }

    fun deepAnalyze(videoId: String, premiumVideo: Boolean = false) = viewModelScope.launch {
        if (_busyVideoId.value != null) return@launch
        _busyVideoId.value = videoId
        _message.value = null
        runCatching { repository.deepAnalyze(videoId, premiumVideo) }
            .onSuccess { _message.value = if (premiumVideo) "Gemini 영상 정밀 분석으로 4개의 관점을 만들었어요." else "Terra 공개 정보 분석으로 4개의 관점을 만들었어요." }
            .onFailure { _message.value = it.message ?: "관점 분석을 완료하지 못했어요." }
        _busyVideoId.value = null
    }

    fun explorePerspective(id: String) = viewModelScope.launch { repository.markPerspectiveVisited(id) }
    fun markRecommendedPerspectiveOpened(id: String) = viewModelScope.launch { repository.markPerspectiveOpened(id) }
    fun deleteWatchRecord(videoId: String) = viewModelScope.launch {
        repository.deleteWatchRecord(videoId)
        _message.value = "시청 기록을 삭제했어요."
    }
    fun setTopicEnabled(id: String, enabled: Boolean) = viewModelScope.launch { repository.setTopicEnabled(id, enabled) }
    fun updateTopic(id: String, name: String, description: String, onComplete: () -> Unit) = viewModelScope.launch {
        _message.value = null
        runCatching { repository.updateTopic(id, name, description) }
            .onSuccess {
                _message.value = "주제를 수정했어요."
                onComplete()
            }
            .onFailure { _message.value = it.message ?: "주제를 수정하지 못했어요." }
    }
    fun acceptTopicSuggestion(videoId: String) = viewModelScope.launch {
        repository.acceptTopicSuggestion(videoId)
        _message.value = "새 주제를 등록했어요."
    }
    fun dismissTopicSuggestion(videoId: String) = viewModelScope.launch { repository.dismissTopicSuggestion(videoId) }
    fun clearMessage() { _message.value = null }
}
