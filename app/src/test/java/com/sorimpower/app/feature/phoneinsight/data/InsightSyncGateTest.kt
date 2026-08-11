package com.sorimpower.app.feature.phoneinsight.data

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import org.junit.Assert.assertEquals
import org.junit.Test

class InsightSyncGateTest {
    @Test fun `수동 확인과 백그라운드 확인은 동시에 AI 큐를 처리하지 않는다`() = runBlocking {
        val active = AtomicInteger(0)
        val maximum = AtomicInteger(0)
        coroutineScope {
            repeat(8) {
                launch(Dispatchers.Default) {
                    InsightSyncGate.run {
                        val count = active.incrementAndGet()
                        maximum.updateAndGet { old -> maxOf(old, count) }
                        delay(10)
                        active.decrementAndGet()
                    }
                }
            }
        }
        assertEquals(1, maximum.get())
    }
}
