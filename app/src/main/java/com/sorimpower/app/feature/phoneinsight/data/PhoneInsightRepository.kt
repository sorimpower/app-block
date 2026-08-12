package com.sorimpower.app.feature.phoneinsight.data

import android.content.Context
import androidx.work.WorkManager
import com.sorimpower.app.feature.phoneinsight.domain.*
import com.sorimpower.app.feature.phoneinsight.reminder.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class PhoneInsightRepository(context:Context){
    private val appContext=context.applicationContext
    private val dao=PhoneInsightDatabase.get(appContext).dao()
    private val collector=InsightSourceCollector(appContext)
    private val queueProcessor=InsightQueueProcessor(appContext,dao)
    private val maintenance=InsightMaintenance(dao)

    val configs=dao.observeConfigs()
    val insights=dao.observeActive()
    val latestRun=dao.observeLatestRun()
    val latestSourceRuns=dao.observeLatestSourceRuns()

    suspend fun initialize()=withContext(Dispatchers.IO){
        dao.insertConfigs(InsightSourceType.entries.map{InsightSourceConfigEntity(it,false,false,null,0,false,SmsScanRange.ONE_YEAR,InsightAnalysisFrequency.DAILY)})
        WorkManager.getInstance(appContext).cancelUniqueWork("phone_insight_source_scan")
        MorningDigestScheduler.scheduleAll(appContext)
        InsightReminderScheduler.schedule(appContext,dao.activeNow())
    }

    suspend fun setSmsEnabled(enabled:Boolean,range:SmsScanRange=SmsScanRange.ONE_YEAR)=withContext(Dispatchers.IO){
        val current=dao.config(InsightSourceType.SMS)?:return@withContext
        dao.upsertConfig(current.copy(enabled=enabled,permissionGranted=collector.smsHasPermission(),scanRange=range))
    }

    suspend fun estimateSms(range:SmsScanRange)=withContext(Dispatchers.IO){
        val config=dao.config(InsightSourceType.SMS)?:error("문자 설정을 찾을 수 없습니다.")
        val since=range.days?.let{System.currentTimeMillis()-TimeUnit.DAYS.toMillis(it)}
        val raw=collector.collect(InsightSourceType.SMS,config,since,emptyMap());val candidates=collector.candidates(InsightSourceType.SMS,raw)
        SmsScanEstimate(range,raw.size,candidates.size,(candidates.size+19)/20)
    }

    suspend fun disable(type:InsightSourceType,deleteResults:Boolean)=withContext(Dispatchers.IO){
        dao.config(type)?.let{dao.upsertConfig(it.copy(enabled=false))}
        if(deleteResults){dao.deleteInsights(type);dao.deleteProcessed(type);dao.deleteCandidatesForSource(type)}
    }

    suspend fun setSourceEnabled(type:InsightSourceType,enabled:Boolean,setting:String?=null)=withContext(Dispatchers.IO){
        val current=dao.config(type)?:return@withContext
        val settings=if(setting==null)current.settings else current.settings.copy(accessUris=current.settings.accessUris+setting)
        dao.upsertConfig(current.copy(enabled=enabled,permissionGranted=collector.isReady(type,settings),settings=settings))
    }

    suspend fun prepareSource(type:InsightSourceType,range:SmsScanRange,setting:String?=null)=withContext(Dispatchers.IO){
        val current=dao.config(type)?:return@withContext;val settings=if(setting==null)current.settings else current.settings.copy(accessUris=current.settings.accessUris+setting)
        dao.upsertConfig(current.copy(scanRange=range,permissionGranted=collector.isReady(type,settings),settings=settings))
    }

    suspend fun configureSource(type:InsightSourceType,range:SmsScanRange,setting:String?=null){prepareSource(type,range,setting);setSourceEnabled(type,true)}

    suspend fun estimateAll():CombinedScanEstimate=withContext(Dispatchers.IO){
        val now=System.currentTimeMillis();val enabled=dao.allConfigs().filter{it.enabled};val names=if(enabled.any{it.type==InsightSourceType.CONTACTS})collector.contacts()else emptyMap();val details=mutableListOf<CombinedSourceEstimate>();var audio=0;var images=0;var plain=0
        enabled.forEach{config->val type=config.type
            if(type==InsightSourceType.CONTACTS){details+=CombinedSourceEstimate(type,names.size,0);return@forEach}
            if(type==InsightSourceType.NOTIFICATION){val count=dao.queuedCandidateCount(type);details+=CombinedSourceEstimate(type,count,count);plain+=count;return@forEach}
            if(!collector.isReady(type,config.settings)){details+=CombinedSourceEstimate(type,0,0);return@forEach}
            val since=config.lastScanAt?:config.scanRange.days?.let{now-TimeUnit.DAYS.toMillis(it)};val raw=collector.collect(type,config,since,names);val processed=dao.processedIds(type).toSet();val candidates=collector.candidates(type,raw.filterNot{it.sourceId in processed});details+=CombinedSourceEstimate(type,raw.size,candidates.size)
            when(type){InsightSourceType.CALL_RECORDING->audio+=candidates.size;InsightSourceType.SCREENSHOT,InsightSourceType.DOCUMENT->images+=candidates.size;else->plain+=candidates.size}
        }
        CombinedScanEstimate(details,details.sumOf{it.totalItems},details.sumOf{it.candidateItems},(audio+1)/2+(images+5)/6+(plain+19)/20)
    }

    suspend fun estimateSource(type:InsightSourceType,range:SmsScanRange):SourceScanEstimate=withContext(Dispatchers.IO){
        val config=dao.config(type)?:error("분석 설정을 찾을 수 없습니다.");val since=range.days?.let{System.currentTimeMillis()-TimeUnit.DAYS.toMillis(it)};val names=if(dao.config(InsightSourceType.CONTACTS)?.enabled==true)collector.contacts()else emptyMap();val raw=collector.collect(type,config,since,names);val candidates=collector.candidates(type,raw)
        val calls=when(type){InsightSourceType.CALL_RECORDING->(candidates.size+1)/2;InsightSourceType.SCREENSHOT,InsightSourceType.DOCUMENT->(candidates.size+5)/6;InsightSourceType.CONTACTS,InsightSourceType.NOTIFICATION->0;else->(candidates.size+19)/20};SourceScanEstimate(type,range,raw.size,candidates.size,calls)
    }

    suspend fun enableAndInitialScan(type:InsightSourceType):Int=withContext(Dispatchers.IO){val current=dao.config(type)?:error("분석 설정을 찾을 수 없습니다.");dao.upsertConfig(current.copy(enabled=true));scan(setOf(type),true)}
    suspend fun setUsageApps(packages:Set<String>)=setSelectedApps(InsightSourceType.APP_USAGE,packages)
    suspend fun setSelectedApps(type:InsightSourceType,packages:Set<String>)=withContext(Dispatchers.IO){require(type in setOf(InsightSourceType.APP_USAGE,InsightSourceType.NOTIFICATION));dao.config(type)?.let{dao.upsertConfig(it.copy(settings=it.settings.copy(selectedPackages=packages)))}}
    suspend fun scanSms(full:Boolean=false)=scan(setOf(InsightSourceType.SMS),full)
    suspend fun scanAll(full:Boolean=false)=scan(null,full)

    /** Pull-to-refresh: collect every enabled source and analyze only newly discovered candidates. */
    suspend fun refresh()=scan(null,false)

    private suspend fun scan(only:Set<InsightSourceType>?,full:Boolean):Int=withContext(Dispatchers.IO){InsightSyncGate.run{
        val started=System.currentTimeMillis();val runId=dao.insertRun(InsightAnalysisRunEntity(startedAt=started));var collected=0
        try{
            val configs=dao.allConfigs().filter{it.enabled&&(only==null||it.type in only)};val contactNames=if(dao.config(InsightSourceType.CONTACTS)?.enabled==true)collector.contacts()else emptyMap()
            configs.forEach{config->
                val sourceStarted=System.currentTimeMillis();val type=config.type
                if(full){dao.deleteProcessed(type);dao.deleteInsights(type);dao.deleteCandidatesForSource(type)}
                if(!collector.isReady(type,config.settings)){dao.upsertConfig(config.copy(permissionGranted=false));dao.insertSourceRun(InsightSourceRunEntity(runId=runId,sourceType=type,startedAt=sourceStarted,finishedAt=System.currentTimeMillis(),status=InsightRunStatus.SKIPPED,scannedCount=0,freshCount=0,candidateCount=0,error="접근 권한 또는 분석 대상 설정이 필요합니다."));return@forEach}
                if(type==InsightSourceType.NOTIFICATION){val queued=dao.queuedCandidateCount(type);collected+=queued;dao.insertSourceRun(InsightSourceRunEntity(runId=runId,sourceType=type,startedAt=sourceStarted,finishedAt=System.currentTimeMillis(),status=InsightRunStatus.SUCCESS,scannedCount=queued,freshCount=queued,candidateCount=queued));return@forEach}
                try{
                    val since=if(full||config.lastScanAt==null)config.scanRange.days?.let{started-TimeUnit.DAYS.toMillis(it)}else config.lastScanAt-TimeUnit.DAYS.toMillis(2);val raw=collector.collect(type,config,since,contactNames);val processed=dao.processedIds(type).toHashSet();val fresh=raw.filterNot{it.sourceId in processed};val candidates=collector.candidates(type,fresh);val candidateIds=candidates.mapTo(hashSetOf()){it.sourceId};val noise=fresh.filterNot{it.sourceId in candidateIds}.map{ProcessedInsightSourceEntity(type,it.sourceId,it.text.hashCode(),started)}
                    if(noise.isNotEmpty())dao.markProcessed(noise);if(candidates.isNotEmpty())dao.queueCandidates(candidates.map{it.toCandidate()});collected+=candidates.size;dao.upsertConfig(config.copy(permissionGranted=true,lastScanAt=started,lastScanItemCount=raw.size,initialScanCompleted=true));dao.insertSourceRun(InsightSourceRunEntity(runId=runId,sourceType=type,startedAt=sourceStarted,finishedAt=System.currentTimeMillis(),status=InsightRunStatus.SUCCESS,scannedCount=raw.size,freshCount=fresh.size,candidateCount=candidates.size))
                }catch(t:Throwable){dao.insertSourceRun(InsightSourceRunEntity(runId=runId,sourceType=type,startedAt=sourceStarted,finishedAt=System.currentTimeMillis(),status=InsightRunStatus.FAILED,scannedCount=0,freshCount=0,candidateCount=0,error=t.message?.take(500)))}
            }
            val summary=queueProcessor.process();summary.sourceUsage.forEach{(type,usage)->dao.updateSourceUsage(runId,type,usage.aiCalls,usage.model,usage.inputTokens,usage.outputTokens,usage.estimatedCostMicros)};maintenance.cleanup(started);dao.finishRun(runId,System.currentTimeMillis(),InsightRunStatus.SUCCESS,collected,summary.insights,summary.aiCalls,null,summary.model,summary.inputTokens,summary.outputTokens,summary.estimatedCostMicros);collected
        }catch(t:Throwable){val finished=System.currentTimeMillis();val failure=t as? InsightQueueException;val partial=failure?.partial?:InsightProcessSummary();partial.sourceUsage.forEach{(type,usage)->dao.updateSourceUsage(runId,type,usage.aiCalls,usage.model,usage.inputTokens,usage.outputTokens,usage.estimatedCostMicros)};failure?.sources?.forEach{dao.markSourceRunFailed(runId,it,t.message?.take(500)?:"AI 분석 실패",finished)};dao.finishRun(runId,finished,InsightRunStatus.FAILED,collected,partial.insights,partial.aiCalls,t.message?.take(500),partial.model,partial.inputTokens,partial.outputTokens,partial.estimatedCostMicros);throw t}
    }}

    private fun RawInsightItem.toCandidate()=InsightCandidateEntity(sourceType=sourceType,sourceId=sourceId,senderOrApp=sender,text=text,occurredAt=occurredAt,attachmentUri=attachmentUri)
    suspend fun updateStatus(id:String,status:InsightStatus)=withContext(Dispatchers.IO){dao.updateStatus(id,status,System.currentTimeMillis());PhoneInsightNotifier.cancel(appContext,id);PhoneInsightNotifier.clearDigest(appContext)}
    suspend fun markNotified(ids:List<String>)=withContext(Dispatchers.IO){if(ids.isNotEmpty())dao.markNotified(ids,System.currentTimeMillis())}
    suspend fun digestRelevant()=withContext(Dispatchers.IO){dao.activeNow().filter{PhoneInsightVisibility.digestVisible(it)}.sortedWith(PhoneInsightVisibility.comparator())}
}
