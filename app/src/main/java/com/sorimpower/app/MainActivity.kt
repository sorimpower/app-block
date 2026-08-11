package com.sorimpower.app

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.sorimpower.app.core.app.SorimPowerApp
import com.sorimpower.app.feature.blocker.presentation.BlockerViewModel
import com.sorimpower.app.feature.blocker.service.AppBlockAccessibilityService
import com.sorimpower.app.feature.bodylog.presentation.BodyLogViewModel
import com.sorimpower.app.feature.auction.presentation.AuctionViewModel
import com.sorimpower.app.feature.healthcheckup.presentation.HealthCheckupViewModel
import com.sorimpower.app.feature.phoneinsight.presentation.PhoneInsightViewModel
import com.sorimpower.app.core.ui.SorimPowerTheme

class MainActivity : ComponentActivity() {
    private val blockerViewModel: BlockerViewModel by viewModels()
    private val bodyLogViewModel: BodyLogViewModel by viewModels()
    private val auctionViewModel: AuctionViewModel by viewModels()
    private val healthCheckupViewModel: HealthCheckupViewModel by viewModels()
    private val phoneInsightViewModel: PhoneInsightViewModel by viewModels()
    private var openAuctionAnalysesRequest by mutableIntStateOf(0)
    private var openPhoneInsightRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        handleAuctionAnalysisIntent(intent)
        handlePhoneInsightIntent(intent)
        enableEdgeToEdge()
        setContent {
            SorimPowerTheme {
                SorimPowerApp(
                    blockerViewModel,
                    bodyLogViewModel,
                    auctionViewModel,
                    healthCheckupViewModel,
                    phoneInsightViewModel,
                    ::isAccessibilityServiceEnabled,
                    ::openAccessibilitySettings,
                    openAuctionAnalysesRequest,
                    openPhoneInsightRequest,
                )
            }
        }
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuctionAnalysisIntent(intent)
        handlePhoneInsightIntent(intent)
    }

    private fun handleAuctionAnalysisIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_AUCTION_ANALYSES, false) == true) {
            openAuctionAnalysesRequest++
        }
    }
    private fun handlePhoneInsightIntent(intent: Intent?) { if (intent?.getBooleanExtra(EXTRA_OPEN_PHONE_INSIGHT, false) == true) openPhoneInsightRequest++ }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabled?.let {
            TextUtils.SimpleStringSplitter(':').apply { setString(it) }.any { name ->
                ComponentName(this, AppBlockAccessibilityService::class.java).flattenToString() == name
            }
        } == true
    }

    private fun openAccessibilitySettings() = startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    companion object {
        const val EXTRA_OPEN_AUCTION_ANALYSES = "open_auction_analyses"
        const val EXTRA_OPEN_PHONE_INSIGHT = "open_phone_insight"
    }
}
