package com.sorimpower.app.feature.phoneinsight.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sorimpower.app.feature.phoneinsight.data.PhoneInsightRepository
import com.sorimpower.app.feature.phoneinsight.data.PhoneInsightVisibility
import com.sorimpower.app.feature.phoneinsight.domain.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class PhoneInsightViewModel(application: Application): AndroidViewModel(application) {
    private val repo=PhoneInsightRepository(application)
    private val minuteClock=flow{while(true){emit(System.currentTimeMillis());kotlinx.coroutines.delay(60_000)}}
    val configs=repo.configs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()); val insights=combine(repo.insights,minuteClock){values,_->values.filter{PhoneInsightVisibility.visible(it)}.sortedWith(PhoneInsightVisibility.comparator())}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val latestRun=repo.latestRun.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),null)
    val latestSourceRuns=repo.latestSourceRuns.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),emptyList())
    private val _working=MutableStateFlow(false); val working=_working.asStateFlow(); private val _message=MutableStateFlow<String?>(null); val message=_message.asStateFlow()
    private val _estimate=MutableStateFlow<SmsScanEstimate?>(null); val estimate=_estimate.asStateFlow()
    private val _sourceEstimate=MutableStateFlow<SourceScanEstimate?>(null);val sourceEstimate=_sourceEstimate.asStateFlow()
    init { viewModelScope.launch { repo.initialize() } }
    fun enableSms(range: SmsScanRange) = work { repo.setSmsEnabled(true,range); repo.scanSms(true); _message.value="문자 분석을 완료했습니다." }
    fun configureSms(range:SmsScanRange)=work{repo.setSmsEnabled(true,range);_message.value="문자를 분석 대상에 추가했습니다. 다음 자동 확인부터 포함됩니다."}
    fun estimateSms(range: SmsScanRange) = work { _estimate.value = repo.estimateSms(range) }
    fun clearEstimate(){_estimate.value=null}
    fun confirmSmsAnalysis(){ val value=_estimate.value?:return; _estimate.value=null; enableSms(value.range) }
    fun enableSource(type:InsightSourceType, setting:String?=null)=work { repo.setSourceEnabled(type,true,setting); _message.value="${type.label} 분석을 켰습니다." }
    fun configureSource(type:InsightSourceType,range:SmsScanRange,setting:String?=null)=work{repo.configureSource(type,range,setting);_message.value="${type.label}을 분석 대상에 추가했습니다. 다음 자동 확인부터 포함됩니다."}
    fun estimateSource(type:InsightSourceType,range:SmsScanRange,setting:String?=null)=work { repo.prepareSource(type,range,setting);_sourceEstimate.value=repo.estimateSource(type,range) }
    fun clearSourceEstimate(){_sourceEstimate.value=null}
    fun confirmSourceAnalysis(){val value=_sourceEstimate.value?:return;_sourceEstimate.value=null;work{val count=repo.enableAndInitialScan(value.sourceType);_message.value="${value.sourceType.label} 초기 분석을 완료했습니다. ${count}개를 확인했어요."}}
    fun disableSource(type:InsightSourceType)=work { repo.disable(type,false); _message.value="${type.label} 분석을 껐습니다." }
    fun setUsageApps(packages:Set<String>)=work { repo.setUsageApps(packages) }
    fun setSelectedApps(type:InsightSourceType,packages:Set<String>)=work{repo.setSelectedApps(type,packages)}
    fun disable(type:InsightSourceType, delete:Boolean)=work { repo.disable(type,delete) }
    fun status(id:String,status:InsightStatus)=viewModelScope.launch { repo.updateStatus(id,status) }
    fun clearMessage(){_message.value=null}
    private fun work(block:suspend()->Unit){if(_working.value)return;viewModelScope.launch{_working.value=true;runCatching{block()}.onFailure{_message.value=it.message?:"분석을 완료하지 못했어요."};_working.value=false}}
}
