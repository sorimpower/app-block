package com.sorimpower.app.feature.perspective.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sorimpower.app.feature.perspective.data.PerspectiveRepository
import com.sorimpower.app.feature.perspective.data.PerspectiveState
import com.sorimpower.app.feature.perspective.data.InterestAiComment
import com.sorimpower.app.feature.perspective.data.InterestProfile
import com.sorimpower.app.feature.perspective.data.CrossTopicVideoRecommendation
import com.sorimpower.app.feature.perspective.data.WatchedVideoEntity
import com.sorimpower.app.feature.perspective.data.WatchedVideoPlayback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PerspectiveViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PerspectiveRepository(application)
    val state = repository.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PerspectiveState())
    private val _busyVideoId = MutableStateFlow<String?>(null)
    val busyVideoId = _busyVideoId.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _interestComment = MutableStateFlow<InterestAiComment?>(null)
    val interestComment = _interestComment.asStateFlow()
    private val _interestCommentLoading = MutableStateFlow(false)
    val interestCommentLoading = _interestCommentLoading.asStateFlow()
    private val _interestProfile = MutableStateFlow(repository.interestProfile())
    val interestProfile = _interestProfile.asStateFlow()
    private val interestCommentCache = mutableMapOf<Pair<Long, Int>, InterestAiComment>()
    private var interestCommentJob: Job? = null
    private var lastInterestRequest: Pair<Long, Int>? = null
    private val _crossTopicVideos = MutableStateFlow<List<CrossTopicVideoRecommendation>>(emptyList())
    val crossTopicVideos = _crossTopicVideos.asStateFlow()
    private val _crossTopicLoading = MutableStateFlow(false)
    val crossTopicLoading = _crossTopicLoading.asStateFlow()
    private var crossTopicKey: String? = null
    private val _watchedVideoPlayback = MutableStateFlow<Map<String, WatchedVideoPlayback>>(emptyMap())
    val watchedVideoPlayback = _watchedVideoPlayback.asStateFlow()
    private val resolvingWatchedVideos = mutableSetOf<String>()

    init { viewModelScope.launch { repository.initialize() } }

    fun deepAnalyze(videoId: String, premiumVideo: Boolean = false) = viewModelScope.launch {
        if (_busyVideoId.value != null) return@launch
        _busyVideoId.value = videoId
        _message.value = null
        runCatching { repository.deepAnalyze(videoId, premiumVideo) }
            .onSuccess { _message.value = "확인해볼 만한 구체적인 관점 4개를 만들었어요." }
            .onFailure { _message.value = it.message ?: "관점 분석을 완료하지 못했어요." }
        _busyVideoId.value = null
    }

    fun analyzeSharedUrl(url: String) = viewModelScope.launch {
        if (_busyVideoId.value != null) return@launch
        _busyVideoId.value = "shared"
        _message.value = null
        runCatching { repository.analyzeSharedUrl(url) }
            .onSuccess { _message.value = "공유한 영상에서 구체적인 관점 4개를 만들었어요." }
            .onFailure { _message.value = it.message ?: "공유한 영상을 분석하지 못했어요." }
        _busyVideoId.value = null
    }

    fun loadInterestComment(days: Long, dataVersion: Int, refresh: Boolean = false) {
        val key = days to dataVersion
        lastInterestRequest = key
        if (!refresh) interestCommentCache[key]?.let { _interestComment.value = it; return }
        interestCommentJob?.cancel()
        _interestComment.value = null
        interestCommentJob = viewModelScope.launch {
            _interestCommentLoading.value = true
            runCatching { repository.analyzeInterest(days, _interestProfile.value) }
                .onSuccess { result -> interestCommentCache[key] = result; _interestComment.value = result }
                .onFailure { error ->
                    if (error !is kotlinx.coroutines.CancellationException) _message.value = error.message ?: "관심 분석 코멘트를 만들지 못했어요."
                }
            _interestCommentLoading.value = false
        }
    }

    fun saveInterestProfile(profile: InterestProfile) {
        repository.saveInterestProfile(profile)
        _interestProfile.value = profile
        interestCommentCache.clear()
        lastInterestRequest?.let { (days, dataVersion) -> loadInterestComment(days, dataVersion, refresh = true) }
    }

    fun loadCrossTopicVideos(currentTopics: List<String>, refresh: Boolean = false) {
        val key = currentTopics.sorted().joinToString("|")
        if (!refresh && key == crossTopicKey && _crossTopicVideos.value.isNotEmpty()) return
        if (_crossTopicLoading.value) return
        crossTopicKey = key
        _crossTopicLoading.value = true
        viewModelScope.launch {
            runCatching { repository.crossTopicVideoRecommendations(currentTopics) }
                .onSuccess { _crossTopicVideos.value = it }
                .onFailure { _message.value = it.message ?: "추천 영상을 찾지 못했어요." }
            _crossTopicLoading.value = false
        }
    }

    fun resolveWatchedVideo(video: WatchedVideoEntity) {
        if (video.id in _watchedVideoPlayback.value || !resolvingWatchedVideos.add(video.id)) return
        viewModelScope.launch {
            runCatching { repository.resolveWatchedVideoPlayback(video) }
                .onSuccess { playback ->
                    if (playback != null) _watchedVideoPlayback.value = _watchedVideoPlayback.value + (video.id to playback)
                }
                .onFailure { _message.value = "영상 정보를 불러오지 못했어요." }
            resolvingWatchedVideos.remove(video.id)
        }
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
