package com.sorimpower.app

import android.content.Context
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "SorimPowerFirebaseAI"
private const val PREFERENCES = "firebase_ai_debug_check"
private const val VERIFIED = "verified"

internal fun SorimPowerApplication.verifyFirebaseAiConnectionOnce() {
    val preferences = getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    if (preferences.getBoolean(VERIFIED, false)) return

    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        runCatching {
            Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel("gemini-3.5-flash-lite")
                .generateContent("Reply with exactly SORIMPOWER_OK")
                .text
                .orEmpty()
                .trim()
        }.onSuccess { response ->
            if (response == "SORIMPOWER_OK") {
                preferences.edit().putBoolean(VERIFIED, true).apply()
                Log.i(TAG, "Firebase AI Logic connection verified")
            } else {
                Log.w(TAG, "Firebase AI Logic returned an unexpected smoke-test response")
            }
        }.onFailure { error ->
            Log.e(TAG, "Firebase AI Logic connection check failed", error)
        }
    }
}
