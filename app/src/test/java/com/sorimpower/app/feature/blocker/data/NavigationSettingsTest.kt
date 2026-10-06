package com.sorimpower.app.feature.blocker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationSettingsTest {
    @Test
    fun `부동산 시세를 시작 화면 저장값에서 복원한다`() {
        assertEquals(StartDestination.PROPERTY_TRACKER, StartDestination.from("PROPERTY_TRACKER"))
    }

    @Test
    fun `부동산 시세가 기본 하단 탭에 포함된다`() {
        assertTrue(BottomNavigationTab.PROPERTY_TRACKER in BottomNavigationTab.defaultOrder)
        assertEquals(BottomNavigationTab.MORE, BottomNavigationTab.defaultOrder.last())
    }

    @Test
    fun `저장된 하단 탭 순서에서 부동산 시세를 복원한다`() {
        assertEquals(
            listOf(BottomNavigationTab.HOME, BottomNavigationTab.PROPERTY_TRACKER, BottomNavigationTab.MORE),
            BottomNavigationTab.from("HOME,PROPERTY_TRACKER,MORE"),
        )
    }
}
