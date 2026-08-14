package com.sorimpower.app.feature.phoneinsight.service

import android.app.Notification
import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.sorimpower.app.feature.perspective.data.PerspectiveRepository
import com.sorimpower.app.feature.phoneinsight.data.*
import com.sorimpower.app.feature.phoneinsight.domain.InsightSourceType
import com.sorimpower.app.feature.phoneinsight.domain.InsightAppSelectionPolicy
import kotlinx.coroutines.*
import java.time.LocalDate
import org.json.JSONObject

/** Android delivers new notification text here; it is queued locally and joined with other sources on the next batch. */
class PhoneInsightNotificationListenerService : NotificationListenerService() {
    private val serviceScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val mediaControllers = mutableMapOf<MediaSession.Token, Pair<MediaController, MediaController.Callback>>()
    private val activeSessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        syncYouTubeControllers(controllers.orEmpty())
    }
    private val mediaSessionPoll = object : Runnable {
        override fun run() {
            mediaControllers.values.forEach { (controller, _) -> captureController(controller) }
            mainHandler.postDelayed(this, MEDIA_POLL_INTERVAL_MS)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val manager = getSystemService(MediaSessionManager::class.java)
        val listenerComponent = ComponentName(this, javaClass)
        runCatching {
            manager.addOnActiveSessionsChangedListener(activeSessionsListener, listenerComponent, mainHandler)
            syncYouTubeControllers(manager.getActiveSessions(listenerComponent))
            mainHandler.removeCallbacks(mediaSessionPoll)
            mainHandler.postDelayed(mediaSessionPoll, MEDIA_POLL_INTERVAL_MS)
        }
    }
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val extras=sbn.notification.extras
        val title=extras.getCharSequence("android.title")?.toString().orEmpty()
        val text=extras.getCharSequence("android.text")?.toString().orEmpty()
        if (sbn.packageName == YOUTUBE_PACKAGE) {
            captureYouTubePlayback(sbn, title, text)
        }
        val combined="$title\n$text".trim();if(combined.isBlank()||!InsightLocalPreprocessor.isCandidate(combined))return
        serviceScope.launch {
            val dao=PhoneInsightDatabase.get(applicationContext).dao()
            val config=dao.config(InsightSourceType.NOTIFICATION) ?: return@launch
            if (!config.enabled) return@launch
            val selected=config.settings.selectedPackages;if(!InsightAppSelectionPolicy.includes(selected,sbn.packageName))return@launch
            val stable="${sbn.packageName}:${title.lowercase().replace(Regex("\\s+"),"").take(60)}:${LocalDate.now()}"
            dao.queueCandidates(listOf(InsightCandidateEntity(sourceType=InsightSourceType.NOTIFICATION,sourceId=stable,senderOrApp=sbn.packageName,text=combined,occurredAt=sbn.postTime)))
        }
    }
    private fun captureYouTubePlayback(sbn: StatusBarNotification, fallbackTitle: String, fallbackChannel: String) {
        val extras = sbn.notification.extras
        val token = if (Build.VERSION.SDK_INT >= 33) {
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            @Suppress("DEPRECATION") extras.getParcelable(Notification.EXTRA_MEDIA_SESSION) as? MediaSession.Token
        }
        val controller = token?.let { runCatching { MediaController(applicationContext, it) }.getOrNull() }
        if (controller != null) captureController(controller, fallbackTitle, fallbackChannel)
        else serviceScope.launch { PerspectiveRepository(applicationContext).recordPlayback(fallbackTitle, fallbackChannel, null, 0, 0) }
    }

    private fun syncYouTubeControllers(activeControllers: List<MediaController>) {
        val youtubeControllers = activeControllers.filter { it.packageName == YOUTUBE_PACKAGE }
        val activeTokens = youtubeControllers.mapTo(mutableSetOf()) { it.sessionToken }
        mediaControllers.keys.filter { it !in activeTokens }.forEach { token ->
            mediaControllers.remove(token)?.let { (controller, callback) -> runCatching { controller.unregisterCallback(callback) } }
        }
        youtubeControllers.forEach { controller ->
            val token = controller.sessionToken
            if (token !in mediaControllers) {
                val callback = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) = captureController(controller)
                    override fun onPlaybackStateChanged(state: PlaybackState?) = captureController(controller)
                    override fun onSessionDestroyed() {
                        mediaControllers.remove(token)?.let { (savedController, savedCallback) -> runCatching { savedController.unregisterCallback(savedCallback) } }
                    }
                }
                runCatching { controller.registerCallback(callback, mainHandler) }
                mediaControllers[token] = controller to callback
            }
            captureController(controller)
        }
    }

    private fun captureController(controller: MediaController, fallbackTitle: String = "", fallbackChannel: String = "") {
        val metadata = controller.metadata
        val state = controller.playbackState
        if (state?.state !in setOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_PAUSED, PlaybackState.STATE_BUFFERING, PlaybackState.STATE_STOPPED)) return
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
            .ifBlank { metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE).orEmpty() }
            .ifBlank { fallbackTitle }
        val channel = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
            .ifBlank { metadata?.getString(MediaMetadata.METADATA_KEY_AUTHOR).orEmpty() }
            .ifBlank { metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty() }
            .ifBlank { fallbackChannel }
        if (title.isBlank()) return
        // YouTube는 기기·영상 유형마다 MediaSession의 필드를 다르게 채운다.
        // 기본 ID 외에 MediaDescription의 URI/ID까지 함께 확인해야 실제 영상 주소를
        // 얻을 수 있다. 주소가 없는 세션은 제목만으로 영상을 특정할 수 없으므로 저장만 한다.
        val description = metadata?.description
        val mediaId = listOfNotNull(
            metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
            metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_URI),
            description?.mediaId,
            description?.mediaUri?.toString(),
        ).firstNotNullOfOrNull(::extractYouTubeVideoId)
        val durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0L) ?: 0L
        val rawPosition = if (state?.state == PlaybackState.STATE_PLAYING && state.lastPositionUpdateTime > 0L) {
            state.position + ((SystemClock.elapsedRealtime() - state.lastPositionUpdateTime) * state.playbackSpeed).toLong()
        } else state?.position ?: 0L
        val positionMs = if (durationMs > 0) rawPosition.coerceIn(0L, durationMs) else rawPosition.coerceAtLeast(0L)
        // 사용자가 일시정지한 경우에는 방해하지 않는다. 앱이 재생을 종료한 상태만 처리한다.
        val playbackEnded = state?.state == PlaybackState.STATE_STOPPED
        serviceScope.launch {
            PerspectiveRepository(applicationContext).recordPlayback(title, channel, mediaId, durationMs / 1_000L, positionMs / 1_000L, playbackEnded)
        }
    }

    private fun extractYouTubeVideoId(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.matches(Regex("[A-Za-z0-9_-]{11}"))) return trimmed
        return runCatching {
            val uri = android.net.Uri.parse(trimmed)
            when {
                uri.host?.contains("youtu.be") == true -> uri.lastPathSegment
                uri.path?.startsWith("/shorts/") == true -> uri.pathSegments.getOrNull(1)
                else -> uri.getQueryParameter("v") ?: uri.lastPathSegment
            }?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
        }.getOrNull()
    }

    override fun onListenerDisconnected() {
        cleanupMediaSessions()
        super.onListenerDisconnected()
    }

    override fun onDestroy(){cleanupMediaSessions();serviceScope.cancel();super.onDestroy()}

    private fun cleanupMediaSessions() {
        mainHandler.removeCallbacks(mediaSessionPoll)
        runCatching { getSystemService(MediaSessionManager::class.java).removeOnActiveSessionsChangedListener(activeSessionsListener) }
        mediaControllers.values.forEach { (controller, callback) -> runCatching { controller.unregisterCallback(callback) } }
        mediaControllers.clear()
    }

    private companion object {
        const val YOUTUBE_PACKAGE = "com.google.android.youtube"
        const val MEDIA_POLL_INTERVAL_MS = 30_000L
    }
}
