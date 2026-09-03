package com.sorimpower.app.feature.perspective.data

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import java.net.URLEncoder

data class WatchedVideoPlayback(val videoId: String?, val url: String, val thumbnailUrl: String?)

/** Resolves legacy MediaSession records that did not expose a YouTube video ID. */
internal class YoutubeHistoryResolver {
    suspend fun resolve(title: String, channel: String): WatchedVideoPlayback? {
        val fallback = WatchedVideoPlayback(
            videoId = null,
            url = "https://www.youtube.com/results?search_query=" + URLEncoder.encode("$title $channel", Charsets.UTF_8.name()),
            thumbnailUrl = null,
        )
        return runCatching {
            val result = FirebaseFunctions.getInstance("asia-northeast3")
                .getHttpsCallable("resolveYoutubeVideoContext")
                .call(mapOf("videoId" to "", "title" to title, "channel" to channel))
                .await()
            val data = result.data as? Map<*, *> ?: return@runCatching fallback
            if (data["matched"] != true) return@runCatching fallback
            val id = data["videoId"] as? String ?: return@runCatching fallback
            val url = data["url"] as? String ?: return@runCatching fallback
            val thumbnail = (data["thumbnailUrl"] as? String).orEmpty().ifBlank { "https://i.ytimg.com/vi/$id/mqdefault.jpg" }
            WatchedVideoPlayback(id, url, thumbnail)
        }.getOrDefault(fallback)
    }
}
