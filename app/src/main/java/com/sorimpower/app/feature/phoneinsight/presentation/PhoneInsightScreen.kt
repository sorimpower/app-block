package com.sorimpower.app.feature.phoneinsight.presentation

import android.Manifest
import android.os.Build
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sorimpower.app.core.ui.AppCobalt
import com.sorimpower.app.core.ui.AppOrange
import com.sorimpower.app.feature.phoneinsight.data.*
import com.sorimpower.app.feature.phoneinsight.domain.*
import java.time.*
import java.time.format.DateTimeFormatter

private enum class InsightTab(val label:String){HOME("챙길 항목"),SOURCES("분석 데이터")}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PhoneInsightScreen(padding:PaddingValues, vm:PhoneInsightViewModel){
    val configs by vm.configs.collectAsStateWithLifecycle();val insights by vm.insights.collectAsStateWithLifecycle();val latestRun by vm.latestRun.collectAsStateWithLifecycle();val latestSourceRuns by vm.latestSourceRuns.collectAsStateWithLifecycle();val working by vm.working.collectAsStateWithLifecycle();val message by vm.message.collectAsStateWithLifecycle();var tab by remember{mutableStateOf(InsightTab.HOME)};var rangeDialog by remember{mutableStateOf(false)};var sourceRangeType by remember{mutableStateOf<InsightSourceType?>(null)};var pendingRange by remember{mutableStateOf(SmsScanRange.ONE_YEAR)};var pendingSourceRange by remember{mutableStateOf(SmsScanRange.ONE_MONTH)};var awaitingUsageEstimate by remember{mutableStateOf(false)};var awaitingDownloadsPermission by remember{mutableStateOf(false)};var pendingDownloadsRoot by remember{mutableStateOf(false)};var disableSms by remember{mutableStateOf(false)};var appSelectionType by remember{mutableStateOf<InsightSourceType?>(null)};var usageSelection by remember{mutableStateOf<Set<String>>(emptySet())}
    val context=androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner=androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner){val observer=androidx.lifecycle.LifecycleEventObserver{_,event->if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME){if(awaitingUsageEstimate){awaitingUsageEstimate=false;vm.configureSource(InsightSourceType.APP_USAGE,pendingSourceRange)};if(awaitingDownloadsPermission){awaitingDownloadsPermission=false;if(Build.VERSION.SDK_INT<30||android.os.Environment.isExternalStorageManager())vm.configureSource(InsightSourceType.DOCUMENT,pendingSourceRange,DeviceInsightSources.DOWNLOADS_ROOT)}}};lifecycleOwner.lifecycle.addObserver(observer);onDispose{lifecycleOwner.lifecycle.removeObserver(observer)}}
    val notificationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
    val calendarPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){if(it)vm.configureSource(InsightSourceType.CALENDAR,pendingSourceRange)}
    val contactsPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){if(it)vm.enableSource(InsightSourceType.CONTACTS)}
    val callLogPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){if(it)vm.configureSource(InsightSourceType.CALL_LOG,pendingSourceRange)}
    val photoTree=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){uri->if(uri!=null){context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);vm.configureSource(InsightSourceType.SCREENSHOT,pendingSourceRange,uri.toString())}}
    val documentTree=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){uri->if(uri!=null){context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);vm.configureSource(InsightSourceType.DOCUMENT,pendingSourceRange,uri.toString())}}
    val recordingTree=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()){uri->if(uri!=null){context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);vm.configureSource(InsightSourceType.CALL_RECORDING,pendingSourceRange,uri.toString())}}
    val smsPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted){vm.configureSms(pendingRange);if(Build.VERSION.SDK_INT>=33)notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)}}
    LaunchedEffect(Unit){if(Build.VERSION.SDK_INT>=33&&androidx.core.content.ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)}
    Column(Modifier.fillMaxSize().padding(padding)){PrimaryTabRow(InsightTab.entries.indexOf(tab)){InsightTab.entries.forEachIndexed{i,t->Tab(selected=tab==t,onClick={tab=t},text={Text(t.label)})}}
        if(tab==InsightTab.HOME) InsightHome(insights,configs.firstOrNull{it.type==InsightSourceType.SMS},vm::status)
        else SourceManagement(configs,latestRun,latestSourceRuns,working,onSmsToggle={enabled->if(enabled)rangeDialog=true else disableSms=true},onToggle={type,enabled->
            if(!enabled) vm.disableSource(type) else when(type){
                InsightSourceType.NOTIFICATION->{vm.enableSource(type);context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))}
                InsightSourceType.SCREENSHOT,InsightSourceType.DOCUMENT,InsightSourceType.CALL_RECORDING,InsightSourceType.CALENDAR,InsightSourceType.CALL_LOG,InsightSourceType.APP_USAGE->sourceRangeType=type
                InsightSourceType.CONTACTS->contactsPermission.launch(Manifest.permission.READ_CONTACTS)
                InsightSourceType.SMS->Unit
            }
        },onAddFolder={type->pendingDownloadsRoot=false;sourceRangeType=type},onAddDownloads={pendingDownloadsRoot=true;sourceRangeType=InsightSourceType.DOCUMENT},onEditApps={type,selected->usageSelection=selected;appSelectionType=type})
    }
    if(rangeDialog) AlertDialog(onDismissRequest={rangeDialog=false},title={Text("문자 분석 범위")},text={Column{SmsScanRange.entries.forEach{r->TextButton({pendingRange=r;rangeDialog=false;smsPermission.launch(Manifest.permission.READ_SMS)},Modifier.fillMaxWidth()){Text(r.label)}}}},confirmButton={})
    sourceRangeType?.let { type->AlertDialog(onDismissRequest={sourceRangeType=null;pendingDownloadsRoot=false},title={Text("${type.label} 데이터 범위")},text={Column{Text(if(type==InsightSourceType.CALENDAR)"선택한 과거 기간부터 향후 90일 일정까지 통합 분석 후보에 포함합니다." else "분석할 기존 데이터의 기간만 설정합니다. 여기서는 AI를 호출하지 않습니다.",style=MaterialTheme.typography.bodySmall);SmsScanRange.entries.forEach{r->TextButton({pendingSourceRange=r;sourceRangeType=null;when(type){InsightSourceType.SCREENSHOT->photoTree.launch(null);InsightSourceType.DOCUMENT->{if(pendingDownloadsRoot){pendingDownloadsRoot=false;if(Build.VERSION.SDK_INT<30||android.os.Environment.isExternalStorageManager())vm.configureSource(type,r,DeviceInsightSources.DOWNLOADS_ROOT)else{awaitingDownloadsPermission=true;context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,android.net.Uri.parse("package:${context.packageName}")))}}else documentTree.launch(null)};InsightSourceType.CALL_RECORDING->recordingTree.launch(null);InsightSourceType.CALENDAR->calendarPermission.launch(Manifest.permission.READ_CALENDAR);InsightSourceType.CALL_LOG->callLogPermission.launch(Manifest.permission.READ_CALL_LOG);InsightSourceType.APP_USAGE->{awaitingUsageEstimate=true;context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))};else->Unit}},Modifier.fillMaxWidth()){Text(r.label)}}}},confirmButton={})}
    if(disableSms) AlertDialog(onDismissRequest={disableSms=false},title={Text("문자 분석을 끌까요?")},text={Text("휴대폰의 원본 문자는 삭제하지 않습니다.")},confirmButton={Column{Button({vm.disable(InsightSourceType.SMS,false);disableSms=false}){Text("앞으로만 분석하지 않기")};TextButton({vm.disable(InsightSourceType.SMS,true);disableSms=false}){Text("기존 분석 결과도 삭제")}}},dismissButton={TextButton({disableSms=false}){Text("취소")}})
    appSelectionType?.let{selectionType->val apps=remember{context.packageManager.getInstalledApplications(0).filter{it.packageName!=context.packageName}.map{it.packageName to runCatching{context.packageManager.getApplicationLabel(it).toString()}.getOrDefault(it.packageName)}.sortedBy{it.second.lowercase()}};AlertDialog(onDismissRequest={appSelectionType=null},title={Text(if(selectionType==InsightSourceType.NOTIFICATION)"알림을 확인할 앱" else "사용 기록을 분석할 앱")},text={LazyColumn(Modifier.heightIn(max=460.dp)){if(selectionType==InsightSourceType.NOTIFICATION)item{Row(Modifier.fillMaxWidth().padding(vertical=3.dp),verticalAlignment=Alignment.CenterVertically){Checkbox(usageSelection.isEmpty(),{checked->if(checked)usageSelection=emptySet()});Column{Text("전체 앱",fontWeight=FontWeight.Bold);Text("기본 설정 · 새로 도착하는 모든 앱 알림",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}};HorizontalDivider()};items(apps,key={it.first}){(pkg,label)->Row(Modifier.fillMaxWidth().padding(vertical=3.dp),verticalAlignment=Alignment.CenterVertically){Checkbox(pkg in usageSelection,{checked->usageSelection=if(checked)usageSelection+pkg else usageSelection-pkg});Column{Text(label);Text(pkg,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}}},confirmButton={Button({vm.setSelectedApps(selectionType,usageSelection);appSelectionType=null}){Text("저장")}},dismissButton={TextButton({appSelectionType=null}){Text("취소")}})}
    message?.let{AlertDialog(onDismissRequest=vm::clearMessage,title={Text("알림")},text={Text(it)},confirmButton={Button(vm::clearMessage){Text("확인")}})}
}
@Composable
private fun InsightHome(values: List<PhoneInsightEntity>, sms: InsightSourceConfigEntity?, onStatus: (String, InsightStatus) -> Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current;val alarmManager=remember{context.getSystemService(android.app.AlarmManager::class.java)};val exactAllowed=Build.VERSION.SDK_INT<31||alarmManager.canScheduleExactAlarms()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!exactAllowed) item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(18.dp)) {
                    Text("정확한 일정 알림을 켜 주세요", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                    OutlinedButton({context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,android.net.Uri.parse("package:${context.packageName}")))},Modifier.fillMaxWidth()){Text("정확한 일정 알림 허용")}
                }
            }
        }
        if (values.isEmpty()) item { InfoCard("오늘 또는 곧 다가오는 챙길 항목이 없어요.") }
        items(values, key = { it.id }) { value ->
            InsightItemCard(value=value,onStatus=onStatus)
        }
    }
}

@Composable
private fun InsightItemCard(value:PhoneInsightEntity,onStatus:(String,InsightStatus)->Unit){
    val dayLabel=PhoneInsightVisibility.dayLabel(value)?:"기한 없음";val days=PhoneInsightVisibility.daysUntil(value);val isToday=days==0L;val isImminent=days!=null&&days in 1L..2L;val isImportant=value.importance==InsightImportance.HIGH;val accent=when{isToday->MaterialTheme.colorScheme.error;isImminent||isImportant->AppOrange;else->AppCobalt};val emphasisLabel=when{isToday->"가장 먼저";isImminent->"임박";isImportant->"중요";value.status==InsightStatus.REVIEW->"확인 필요";else->null}
    val typeLabel=value.type.label;val sourceLabel=value.sourceType.label
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),border=BorderStroke(1.dp,accent.copy(alpha=if(emphasisLabel!=null).5f else .2f)),elevation=CardDefaults.cardElevation(defaultElevation=if(emphasisLabel!=null)3.dp else 1.dp),shape=RoundedCornerShape(20.dp)){
        Column(Modifier.padding(horizontal=16.dp,vertical=15.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)){
                Surface(color=accent,shape=RoundedCornerShape(50)){Text(dayLabel,Modifier.padding(horizontal=10.dp,vertical=5.dp),color=Color.White,fontWeight=FontWeight.Black,style=MaterialTheme.typography.labelMedium)}
                Text(typeLabel,color=MaterialTheme.colorScheme.onSurfaceVariant,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                emphasisLabel?.let{Surface(color=accent.copy(alpha=.14f),shape=RoundedCornerShape(50)){Text(it,Modifier.padding(horizontal=9.dp,vertical=5.dp),color=accent,fontWeight=FontWeight.Black,style=MaterialTheme.typography.labelMedium)}}
            }
            Text(value.title,fontWeight=FontWeight.Black,style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onSurface)
            if(value.description.isNotBlank())Text(value.description,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            value.dueAt?.let{
                Surface(color=accent.copy(alpha=.08f),shape=RoundedCornerShape(12.dp)){
                    Row(Modifier.fillMaxWidth().padding(horizontal=11.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)){
                        Icon(Icons.Rounded.Schedule,contentDescription=null,tint=accent,modifier=Modifier.size(18.dp))
                        Text(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M월 d일 HH:mm 알림")),color=accent,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Text(listOf(sourceLabel,value.senderOrApp).filter{it.isNotBlank()}.joinToString(" · "),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color=accent.copy(alpha=.14f))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(9.dp)){
                Button({onStatus(value.id,InsightStatus.COMPLETED)},Modifier.weight(1f),colors=ButtonDefaults.buttonColors(containerColor=accent)){Text("완료")}
                OutlinedButton({onStatus(value.id,InsightStatus.DISMISSED)},Modifier.weight(1f),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline.copy(alpha=.55f))){Text("필요 없음")}
            }
        }
    }
}

@Composable
private fun SourceManagement(configs: List<InsightSourceConfigEntity>,latestRun:InsightAnalysisRunEntity?,sourceRuns:List<InsightSourceRunEntity>, working: Boolean, onSmsToggle: (Boolean) -> Unit, onToggle: (InsightSourceType, Boolean) -> Unit, onAddFolder: (InsightSourceType) -> Unit, onAddDownloads: () -> Unit,onEditApps:(InsightSourceType,Set<String>)->Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Column(verticalArrangement=Arrangement.spacedBy(5.dp)){latestRun?.let{run->val at=Instant.ofEpochMilli(run.startedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M월 d일 HH:mm"));Text(if(run.status==InsightRunStatus.SUCCESS)"최근 확인 $at · 후보 ${run.candidateCount}개 · AI ${run.aiCalls}회 · 토큰 ${run.inputTokens+run.outputTokens}개${run.model?.let{" · $it"}.orEmpty()} · ${aiCostLabel(run.estimatedCostMicros)}" else "최근 확인 실패 $at · ${run.error?:"원인 미상"}",style=MaterialTheme.typography.labelSmall,color=if(run.status==InsightRunStatus.SUCCESS)AppCobalt else MaterialTheme.colorScheme.error);if(sourceRuns.isNotEmpty()){Spacer(Modifier.height(5.dp));sourceRuns.forEach{source->val result=when(source.status){InsightRunStatus.SUCCESS->"성공";InsightRunStatus.FAILED->"실패";InsightRunStatus.SKIPPED->"건너뜀";InsightRunStatus.RUNNING->"진행 중"};Text("${source.sourceType.label} · $result · 후보 ${source.candidateCount}개 · AI ${source.aiCalls}회 · 토큰 ${source.inputTokens+source.outputTokens}개${source.model?.let{" · $it"}.orEmpty()} · ${aiCostLabel(source.estimatedCostMicros)}",style=MaterialTheme.typography.labelSmall,color=if(source.status==InsightRunStatus.FAILED)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant);source.error?.let{Text(it,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.error)}}}}} }
        items(InsightSourceType.entries) { type ->
            val config = configs.firstOrNull { it.type == type }; val available = true
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth()) { Column(Modifier.weight(1f)) { Text(type.label, fontWeight = FontWeight.Black); Text(type.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(config?.enabled == true, { if(type==InsightSourceType.SMS) onSmsToggle(it) else onToggle(type,it) }, enabled = available && !working) }
                    if (type == InsightSourceType.NOTIFICATION && config?.enabled == true&&!config.permissionGranted) Text("시스템 설정에서 ‘AI 챙김 알림 접근’을 허용해 주세요.", color = AppCobalt, style = MaterialTheme.typography.labelSmall)
                    if(type==InsightSourceType.NOTIFICATION&&config?.enabled==true){val selected=config.settings.selectedPackages;OutlinedButton({onEditApps(type,selected)},Modifier.fillMaxWidth()){Text(if(selected.isEmpty())"알림을 확인할 앱 · 전체 앱" else "알림을 확인할 앱 · ${selected.size}개")}}
                    if(config?.enabled==true&&!config.permissionGranted)Text("접근 권한을 확인해 주세요. 권한이 없으면 이 데이터는 건너뜁니다.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.labelSmall)
                    if (type == InsightSourceType.SCREENSHOT && config?.enabled == true) Text("선택한 사진 폴더의 새 이미지만 분석합니다.", color = AppCobalt, style = MaterialTheme.typography.labelSmall)
                    if (type == InsightSourceType.DOCUMENT && config?.enabled == true) Text("선택한 파일 폴더의 새 PDF·이미지만 분석합니다.", color = AppCobalt, style = MaterialTheme.typography.labelSmall)
                    if(type==InsightSourceType.DOCUMENT)OutlinedButton(onAddDownloads,Modifier.fillMaxWidth(),enabled=!working){Text("Downloads 전체 연결")}
                    if (type == InsightSourceType.CALL_RECORDING && config?.enabled == true) Text("선택한 녹음 폴더의 새 파일만 전사합니다. 파일당 최대 8MB입니다.", color = AppCobalt, style = MaterialTheme.typography.labelSmall)
                    if (type in setOf(InsightSourceType.SCREENSHOT,InsightSourceType.DOCUMENT,InsightSourceType.CALL_RECORDING) && config?.enabled == true) {
                        val count=config.settings.accessUris.size
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically){Text("선택한 폴더 ${count}개",style=MaterialTheme.typography.labelSmall);TextButton({onAddFolder(type)},enabled=!working){Text("폴더 추가")}}
                    }
                    if (type == InsightSourceType.CONTACTS && config?.enabled == true) Text("원본 연락처는 AI에 전송하지 않고 번호를 이름으로 바꾸는 데만 사용합니다.", color = AppCobalt, style = MaterialTheme.typography.labelSmall)
                    if (type == InsightSourceType.CALL_LOG && config?.enabled == true) Text("통화 내용이 아닌 시각·방향·통화 길이만 확인합니다.", color = AppCobalt, style = MaterialTheme.typography.labelSmall)
                    if (type == InsightSourceType.APP_USAGE && config?.enabled == true) Text("시스템 설정에서 사용 정보 접근을 허용한 뒤, 다음 분석부터 반영됩니다.", color = AppCobalt, style = MaterialTheme.typography.labelSmall)
                    if(type==InsightSourceType.APP_USAGE&&config?.enabled==true){val selected=config.settings.selectedPackages;OutlinedButton({onEditApps(type,selected)},Modifier.fillMaxWidth()){Text("분석할 앱 선택 · ${selected.size}개")}}
                    if (type == InsightSourceType.SMS && config?.enabled == true) {
                        Text("분석 범위 ${config.scanRange.label}", style = MaterialTheme.typography.labelSmall)
                        Text("최근 분석 ${config.lastScanItemCount}개", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

private fun aiCostLabel(value:Long?):String=value?.let{"예상 비용 $"+java.lang.String.format(java.util.Locale.US,"%.4f",it/1_000_000.0)}?:"비용 미산정"

@Composable private fun InfoCard(text: String) = Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) { Text(text, Modifier.padding(18.dp)) }
