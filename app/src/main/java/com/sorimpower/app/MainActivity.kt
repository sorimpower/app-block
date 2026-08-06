package com.sorimpower.app

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.sorimpower.app.core.app.SorimPowerApp
import com.sorimpower.app.feature.blocker.presentation.BlockerViewModel
import com.sorimpower.app.feature.blocker.service.AppBlockAccessibilityService
import com.sorimpower.app.feature.bodylog.presentation.BodyLogViewModel
import com.sorimpower.app.feature.auction.presentation.AuctionViewModel
import com.sorimpower.app.core.ui.SorimPowerTheme

class MainActivity : ComponentActivity() {
    private val blockerViewModel: BlockerViewModel by viewModels()
    private val bodyLogViewModel: BodyLogViewModel by viewModels()
    private val auctionViewModel: AuctionViewModel by viewModels()

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SorimPowerTheme {
                SorimPowerApp(blockerViewModel, bodyLogViewModel, auctionViewModel, ::isAccessibilityServiceEnabled, ::openAccessibilitySettings)
            }
        }
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
}
