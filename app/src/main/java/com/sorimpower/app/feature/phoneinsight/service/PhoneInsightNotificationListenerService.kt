package com.sorimpower.app.feature.phoneinsight.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.sorimpower.app.feature.phoneinsight.data.*
import com.sorimpower.app.feature.phoneinsight.domain.InsightSourceType
import com.sorimpower.app.feature.phoneinsight.domain.InsightAppSelectionPolicy
import kotlinx.coroutines.*
import java.time.LocalDate
import org.json.JSONObject

/** Android delivers new notification text here; it is queued locally and joined with other sources on the next batch. */
class PhoneInsightNotificationListenerService : NotificationListenerService() {
    private val serviceScope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val extras=sbn.notification.extras
        val title=extras.getCharSequence("android.title")?.toString().orEmpty()
        val text=extras.getCharSequence("android.text")?.toString().orEmpty()
        val combined="$title\n$text".trim();if(combined.isBlank()||!InsightLocalPreprocessor.isCandidate(combined))return
        serviceScope.launch {
            val dao=PhoneInsightDatabase.get(applicationContext).dao()
            val config=dao.config(InsightSourceType.NOTIFICATION) ?: return@launch
            if (!config.enabled) return@launch
            val selected=config.settings.selectedPackages;if(!InsightAppSelectionPolicy.includes(selected,sbn.packageName))return@launch
            val stable="${sbn.packageName}:${title.lowercase().replace(Regex("\\s+"),"").take(60)}:${LocalDate.now()}"
            dao.queueCandidates(listOf(InsightCandidateEntity(sourceType=InsightSourceType.NOTIFICATION,sourceId=stable,senderOrApp=sbn.packageName,text=combined,occurredAt=sbn.postTime)))
        }
    }
    override fun onDestroy(){serviceScope.cancel();super.onDestroy()}
}
