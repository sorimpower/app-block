package com.sorimpower.app.feature.blocker.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.sorimpower.app.data.BlockerRepository
import com.sorimpower.app.feature.blocker.presentation.BlockedActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppBlockAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val repository by lazy { BlockerRepository(applicationContext) }
    private var lastBlockedPackage: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == applicationContext.packageName || packageName == lastBlockedPackage) return
        scope.launch {
            val state = repository.state.first()
            if (state.shouldBlock(packageName)) {
                lastBlockedPackage = packageName
                val todayCount = repository.recordBlockedLaunch(packageName)
                val appName = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString() }.getOrDefault(packageName)
                startActivity(Intent(this@AppBlockAccessibilityService, BlockedActivity::class.java)
                    .putExtra(BlockedActivity.EXTRA_APP_NAME, appName)
                    .putExtra(BlockedActivity.EXTRA_TODAY_COUNT, todayCount)
                    .putExtra(BlockedActivity.EXTRA_MESSAGE, state.blockMessage)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
                // Allows the same app to be recognized again after the blocker has redirected home.
                kotlinx.coroutines.delay(1_500)
                lastBlockedPackage = null
            }
        }
    }
    override fun onInterrupt() = Unit
}
