package com.sorimpower.app

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

internal fun FirebaseAppCheck.installSorimPowerProvider() {
    installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
    // 개발 APK 최초 실행에서 Firebase Console에 등록할 디버그 토큰을 발급한다.
    getAppCheckToken(false)
}
