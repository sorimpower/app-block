package com.sorimpower.app.feature.perspective.data

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content

/** Public YouTube URL 전용 영상·음성 분석 provider. 다른 앱 AI 작업에는 사용하지 않는다. */
internal class GeminiYoutubeVideoProvider {
    suspend fun analyze(url: String, promptText: String): String {
        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel("gemini-3.5-flash")
        val prompt = content {
            fileData(mimeType = "video/mp4", uri = url)
            text(promptText)
        }
        return model.generateContent(prompt).text?.trim().takeUnless(String?::isNullOrBlank)
            ?: error("Gemini가 영상 분석 결과를 반환하지 않았어요.")
    }
}
