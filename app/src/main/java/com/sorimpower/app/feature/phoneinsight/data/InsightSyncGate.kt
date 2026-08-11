package com.sorimpower.app.feature.phoneinsight.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object InsightSyncGate{
    private val mutex=Mutex()
    suspend fun <T> run(block:suspend()->T):T=mutex.withLock{block()}
}
