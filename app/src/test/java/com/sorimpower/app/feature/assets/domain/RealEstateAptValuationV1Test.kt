package com.sorimpower.app.feature.assets.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RealEstateAptValuationV1Test {
    @Test
    fun `five or more trades trims extremes and uses median`() {
        val result = RealEstateAptValuationV1.evaluate(
            listOf(1_000_000_000L, 1_450_000_000L, 1_500_000_000L, 1_550_000_000L, 3_000_000_000L),
        )

        requireNotNull(result)
        assertEquals(1_500_000_000L, result.estimatedValueKrw)
        assertEquals(5, result.comparableCount)
        assertEquals(ValuationConfidence.HIGH, result.confidence)
    }

    @Test
    fun `ownership share is applied to estimated value only`() {
        val result = RealEstateAptValuationV1.evaluate(
            listOf(1_400_000_000L, 1_500_000_000L, 1_600_000_000L),
            ownershipPercent = 50.0,
        )

        requireNotNull(result)
        assertEquals(750_000_000L, result.estimatedValueKrw)
        assertEquals(1_400_000_000L, result.comparableMinKrw)
        assertEquals(1_600_000_000L, result.comparableMaxKrw)
        assertEquals(ValuationConfidence.MEDIUM, result.confidence)
    }

    @Test
    fun `empty or invalid trades cannot be valued`() {
        assertNull(RealEstateAptValuationV1.evaluate(listOf(0L, -1L)))
    }
}
