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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sorimpower.app.core.app.SorimPowerApp
import com.sorimpower.app.feature.blocker.presentation.BlockerViewModel
import com.sorimpower.app.feature.blocker.service.AppBlockAccessibilityService
import com.sorimpower.app.feature.bodylog.presentation.BodyLogViewModel
import com.sorimpower.app.feature.auction.presentation.AuctionViewModel
import com.sorimpower.app.feature.healthcheckup.presentation.HealthCheckupViewModel
import com.sorimpower.app.feature.phoneinsight.presentation.PhoneInsightViewModel
import com.sorimpower.app.feature.perspective.presentation.PerspectiveViewModel
import com.sorimpower.app.feature.assets.presentation.AssetViewModel
import com.sorimpower.app.core.ui.SorimPowerTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val blockerViewModel: BlockerViewModel by viewModels()
    private val bodyLogViewModel: BodyLogViewModel by viewModels()
    private val auctionViewModel: AuctionViewModel by viewModels()
    private val healthCheckupViewModel: HealthCheckupViewModel by viewModels()
    private val phoneInsightViewModel: PhoneInsightViewModel by viewModels()
    private val perspectiveViewModel: PerspectiveViewModel by viewModels()
    private val assetViewModel: AssetViewModel by viewModels()
    private var openAuctionAnalysesRequest by mutableIntStateOf(0)
    private var openPhoneInsightRequest by mutableIntStateOf(0)
    private var openPerspectiveRequest by mutableIntStateOf(0)
    private var openPerspectiveTopicsRequest by mutableIntStateOf(0)
    private var sharedYoutubeUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        handleAuctionAnalysisIntent(intent)
        handlePhoneInsightIntent(intent)
        handlePerspectiveIntent(intent)
        handlePerspectiveTopicIntent(intent)
        handleYoutubeShareIntent(intent)
        enableEdgeToEdge()
        setContent {
            val blockerState by blockerViewModel.state.collectAsStateWithLifecycle()
            SorimPowerTheme(themeMode = blockerState.themeMode) {
                SorimPowerApp(
                    blockerViewModel,
                    bodyLogViewModel,
                    auctionViewModel,
                    healthCheckupViewModel,
                    phoneInsightViewModel,
                    perspectiveViewModel,
                    assetViewModel,
                    ::isAccessibilityServiceEnabled,
                    ::openAccessibilitySettings,
                    openAuctionAnalysesRequest,
                    openPhoneInsightRequest,
                    openPerspectiveRequest,
                    openPerspectiveTopicsRequest,
                    sharedYoutubeUrl,
                    { sharedYoutubeUrl = null },
                )
            }
        }
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuctionAnalysisIntent(intent)
        handlePhoneInsightIntent(intent)
        handlePerspectiveIntent(intent)
        handlePerspectiveTopicIntent(intent)
        handleYoutubeShareIntent(intent)
    }

    private fun handleAuctionAnalysisIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_AUCTION_ANALYSES, false) == true) {
            openAuctionAnalysesRequest++
        }
    }
    private fun handlePhoneInsightIntent(intent: Intent?) { if (intent?.getBooleanExtra(EXTRA_OPEN_PHONE_INSIGHT, false) == true) openPhoneInsightRequest++ }
    private fun handlePerspectiveIntent(intent: Intent?) {
        if (
            intent?.getBooleanExtra(EXTRA_OPEN_PERSPECTIVE, false) == true ||
            intent?.action == ACTION_OPEN_PERSPECTIVE_RECORDS
        ) {
            openPerspectiveRequest++
        }
    }
    private fun handlePerspectiveTopicIntent(intent: Intent?) {
        if (
            intent?.getBooleanExtra(EXTRA_OPEN_PERSPECTIVE_TOPICS, false) == true ||
            intent?.action == ACTION_OPEN_PERSPECTIVE_TOPICS
        ) {
            openPerspectiveTopicsRequest++
        }
    }
    private fun handleYoutubeShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val url = Regex("https?://(?:www\\.)?(?:youtube\\.com/(?:watch\\?[^\\s]*v=|shorts/)|youtu\\.be/)[A-Za-z0-9_-]{11}[^\\s]*", RegexOption.IGNORE_CASE)
            .find(text)?.value ?: return
        sharedYoutubeUrl = url
        openPerspectiveRequest++
    }
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
        const val EXTRA_OPEN_PERSPECTIVE = "open_perspective"
        const val EXTRA_OPEN_PERSPECTIVE_TOPICS = "open_perspective_topics"
        const val ACTION_OPEN_PERSPECTIVE_RECORDS = "com.sorimpower.app.action.OPEN_PERSPECTIVE_RECORDS"
        const val ACTION_OPEN_PERSPECTIVE_TOPICS = "com.sorimpower.app.action.OPEN_PERSPECTIVE_TOPICS"
    }
}
