package com.sorimpower.app

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

internal fun FirebaseAppCheck.installSorimPowerProvider() {
    installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
}
