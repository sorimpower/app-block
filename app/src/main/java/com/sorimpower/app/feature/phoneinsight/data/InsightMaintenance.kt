package com.sorimpower.app.feature.phoneinsight.data

import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

internal class InsightMaintenance(private val dao:PhoneInsightDao){
    suspend fun cleanup(now:Long){val today=Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();val runBefore=now-TimeUnit.DAYS.toMillis(90);dao.expirePastDue(today,now);dao.expireOldUndated(now-TimeUnit.DAYS.toMillis(30),now);dao.deleteOldProcessed(runBefore);dao.deleteOldCandidates(now-TimeUnit.DAYS.toMillis(7));dao.deleteOldInsights(now-TimeUnit.DAYS.toMillis(30),now-TimeUnit.DAYS.toMillis(7));dao.deleteOldSourceRuns(runBefore);dao.deleteOldRuns(runBefore)}
}
