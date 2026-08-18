package com.sorimpower.app.feature.perspective.data

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

internal data class YoutubeAnalysisContext(
    val videoId: String,
    val url: String,
    val title: String = "",
    val channelName: String = "",
    val thumbnailUrl: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val category: String = "",
    val publishedAt: String = "",
    val duration: String = "",
    val viewCount: String = "",
    val chapters: List<String> = emptyList(),
    val transcript: String = "",
) {
    val hasTranscript: Boolean get() = transcript.isNotBlank()
}

internal data class YoutubeRecommendation(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    val url: String,
    val publishedAt: String,
)

/** 서버에서만 보관하는 YouTube Data API 키로 정확 일치 영상만 찾는다. */
internal class YoutubeVideoResolver {
    suspend fun resolve(videoId: String?, title: String, channel: String): YoutubeAnalysisContext? {
        val result = FirebaseFunctions.getInstance("asia-northeast3")
            .getHttpsCallable("resolveYoutubeVideoContext")
            .call(mapOf("videoId" to videoId.orEmpty(), "title" to title, "channel" to channel))
            .await()
        val data = result.data as? Map<*, *> ?: return null
        if (data["matched"] != true) return null
        val videoId = data["videoId"] as? String ?: return null
        val url = data["url"] as? String ?: return null
        fun strings(key: String) = (data[key] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        return YoutubeAnalysisContext(
            videoId = videoId,
            url = url,
            title = data["title"] as? String ?: "",
            channelName = data["channelName"] as? String ?: "",
            thumbnailUrl = data["thumbnailUrl"] as? String ?: "",
            description = data["description"] as? String ?: "",
            tags = strings("tags"),
            category = data["category"] as? String ?: "",
            publishedAt = data["publishedAt"] as? String ?: "",
            duration = data["duration"] as? String ?: "",
            viewCount = data["viewCount"] as? String ?: "",
            chapters = strings("chapters"),
            transcript = data["transcript"] as? String ?: "",
        )
    }
}

internal class YoutubePerspectiveSearch {
    suspend fun find(queries: List<String>): Map<String, List<YoutubeRecommendation>> {
        if (queries.isEmpty()) return emptyMap()
        val result = FirebaseFunctions.getInstance("asia-northeast3")
            .getHttpsCallable("findYoutubePerspectiveVideos")
            .call(mapOf("queries" to queries.take(4)))
            .await()
        val data = result.data as? Map<*, *> ?: return emptyMap()
        return (data["results"] as? Map<*, *>)?.mapNotNull { (query, items) ->
            val key = query as? String ?: return@mapNotNull null
            val videos = (items as? List<*>)?.mapNotNull videoMap@ { raw ->
                val item = raw as? Map<*, *> ?: return@videoMap null
                val videoId = item["videoId"] as? String ?: return@videoMap null
                val url = item["url"] as? String ?: return@videoMap null
                YoutubeRecommendation(videoId, item["title"] as? String ?: "YouTube 영상", item["channelName"] as? String ?: "", item["thumbnailUrl"] as? String ?: "", url, item["publishedAt"] as? String ?: "")
            } ?: emptyList()
            key to videos
        }?.toMap() ?: emptyMap()
    }
}
