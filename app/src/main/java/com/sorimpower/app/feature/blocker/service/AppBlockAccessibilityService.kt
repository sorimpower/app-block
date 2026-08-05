package com.sorimpower.app.feature.blocker.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.sorimpower.app.feature.blocker.data.BlockerRepository
import com.sorimpower.app.feature.blocker.data.BlockerState
import com.sorimpower.app.feature.blocker.presentation.BlockedActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AppBlockAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val repository by lazy { BlockerRepository(applicationContext) }
    private val eventMutex = Mutex()
    private val stateCache = MutableStateFlow(BlockerState())
    private var stateCacheJob: Job? = null
    private var allowedSessionPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        stateCacheJob?.cancel()
        stateCacheJob = scope.launch {
            repository.state.collect { stateCache.value = it }
        }
    }

    override fun onDestroy() {
        stateCacheJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == applicationContext.packageName) return
        scope.launch {
            eventMutex.withLock {
                // 설정은 서비스 연결 중 한 번만 구독하고, 앱 전환마다 메모리 캐시를 읽습니다.
                // 서비스가 막 연결된 첫 이벤트에서만 캐시가 준비될 때까지 DataStore를 직접 읽습니다.
                val state = stateCache.value.takeIf { it.loaded }
                    ?: repository.state.first().also { stateCache.value = it }
                if (packageName == state.oneTimeBypassPackage) {
                    repository.consumeNextLaunch(packageName)
                    allowedSessionPackage = packageName
                    BlockedLaunchSession.finish(packageName)
                    return@withLock
                }
                if (packageName == allowedSessionPackage) return@withLock
                allowedSessionPackage = null
                if (state.shouldBlock(packageName)) {
                    val existingCount = BlockedLaunchSession.countFor(packageName)
                    val todayCount = existingCount ?: 1
                    if (existingCount == null) {
                        BlockedLaunchSession.start(packageName, todayCount)
                    }
                    showBlockScreen(packageName, state.blockMessage, todayCount)
                    if (existingCount == null) {
                        // 실행 횟수 저장이 늦어져도 차단 화면 표시를 지연시키지 않습니다.
                        scope.launch {
                            runCatching { repository.recordBlockedLaunch(packageName) }
                                .onSuccess { count ->
                                    BlockedLaunchSession.updateCount(packageName, count)
                                }
                        }
                    }
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
    data class CountUpdate(val packageName: String, val count: Int)

    private var packageName: String? = null
    private var count: Int? = null
    val countUpdates = MutableStateFlow<CountUpdate?>(null)

    @Synchronized
    fun countFor(packageName: String): Int? = count.takeIf { this.packageName == packageName }

    @Synchronized
    fun start(packageName: String, count: Int) {
        this.packageName = packageName
        this.count = count
        countUpdates.value = CountUpdate(packageName, count)
    }

    @Synchronized
    fun updateCount(packageName: String, count: Int) {
        if (this.packageName != packageName) return
        this.count = count
        countUpdates.value = CountUpdate(packageName, count)
    }

    @Synchronized
    fun finish(packageName: String) {
        if (this.packageName == packageName) finishAll()
    }

    @Synchronized
    fun finishAll() {
        packageName = null
        count = null
        countUpdates.value = null
    }
}
