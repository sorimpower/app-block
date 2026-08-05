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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AppBlockAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val repository by lazy { BlockerRepository(applicationContext) }
    private val eventMutex = Mutex()
    private var allowedSessionPackage: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == applicationContext.packageName) return
        scope.launch {
            eventMutex.withLock {
                val state = repository.state.first()
                if (packageName == state.oneTimeBypassPackage) {
                    repository.consumeNextLaunch(packageName)
                    allowedSessionPackage = packageName
                    BlockedLaunchSession.finish(packageName)
                    return@withLock
                }
                if (packageName == allowedSessionPackage) return@withLock
                allowedSessionPackage = null
                if (state.shouldBlock(packageName)) {
                    val todayCount = BlockedLaunchSession.countFor(packageName)
                        ?: repository.recordBlockedLaunch(packageName).also { count ->
                            BlockedLaunchSession.start(packageName, count)
                        }
                    showBlockScreen(packageName, state.blockMessage, todayCount)
                } else {
                    BlockedLaunchSession.finishAll()
                }
            }
        }
    }
    override fun onInterrupt() = Unit

    private fun showBlockScreen(packageName: String, message: String, todayCount: Int) {
        val appName = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)
        startActivity(Intent(this, BlockedActivity::class.java)
            .putExtra(BlockedActivity.EXTRA_APP_NAME, appName)
            .putExtra(BlockedActivity.EXTRA_PACKAGE_NAME, packageName)
            .putExtra(BlockedActivity.EXTRA_TODAY_COUNT, todayCount)
            .putExtra(BlockedActivity.EXTRA_MESSAGE, message)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
    }
}

internal object BlockedLaunchSession {
    private var packageName: String? = null
    private var count: Int? = null

    @Synchronized
    fun countFor(packageName: String): Int? = count.takeIf { this.packageName == packageName }

    @Synchronized
    fun start(packageName: String, count: Int) {
        this.packageName = packageName
        this.count = count
    }

    @Synchronized
    fun finish(packageName: String) {
        if (this.packageName == packageName) finishAll()
    }

    @Synchronized
    fun finishAll() {
        packageName = null
        count = null
    }
}
