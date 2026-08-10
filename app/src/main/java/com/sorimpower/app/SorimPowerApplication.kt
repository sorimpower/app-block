package com.sorimpower.app

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.sorimpower.app.feature.auction.reminder.AuctionAiRecommendationScheduler

class SorimPowerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AuctionAiRecommendationScheduler.restore(this)
        FirebaseApp.initializeApp(this) ?: return
        FirebaseAppCheck.getInstance().installSorimPowerProvider()
        verifyFirebaseAiConnectionOnce()
    }
}
