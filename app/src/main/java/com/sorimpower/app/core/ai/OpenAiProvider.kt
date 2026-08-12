package com.sorimpower.app.core.ai

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import android.util.Base64

/** OpenAI key never reaches the app: this calls the App Check protected Function. */
internal class OpenAiProvider : AiProvider {
    override suspend fun generate(model: AiModelId, request: AiRequest): AiResponse {
        require(model.provider == AiProviderType.OPENAI)
        val result = FirebaseFunctions.getInstance("asia-northeast3")
            .getHttpsCallable("openAiGenerate")
            .call(
                buildMap<String, Any> {
                    put("taskType", request.taskType.name)
                    put("model", model.name)
                    put("prompt", request.userPrompt)
                    put("jsonOutput", request.jsonOutput)
                    request.reasoningEffort?.let { put("reasoningEffort", it) }
                    if (request.images.isNotEmpty()) put("images", request.images.map { image -> mapOf("sourceId" to image.sourceId,"mimeType" to image.mimeType, "base64" to Base64.encodeToString(image.bytes, Base64.NO_WRAP)) })
                    if (request.audios.isNotEmpty()) put("audios", request.audios.map { audio -> mapOf("sourceId" to audio.sourceId, "fileName" to audio.fileName, "mimeType" to audio.mimeType, "base64" to Base64.encodeToString(audio.bytes, Base64.NO_WRAP)) })
                },
            )
            .await()
        val data = result.data as? Map<*, *> ?: error("OpenAI 서버 응답 형식이 올바르지 않습니다.")
        val sources = (data["sources"] as? List<*>)?.mapNotNull { item ->
            val source = item as? Map<*, *> ?: return@mapNotNull null
            val url = source["url"] as? String ?: return@mapNotNull null
            AiSource(source["title"] as? String ?: url, url)
        } ?: emptyList()
        return AiResponse(
            text = data["text"] as? String ?: error("OpenAI가 분석 결과를 반환하지 않았어요."),
            provider = AiProviderType.OPENAI,
            model = data["model"] as? String ?: model.apiModelName,
            inputTokens = (data["inputTokens"] as? Number)?.toInt(),
            outputTokens = (data["outputTokens"] as? Number)?.toInt(),
            sources = sources,
            checkedAt = (data["checkedAt"] as? Number)?.toLong(),
        )
    }
}
