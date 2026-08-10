package com.sorimpower.app.feature.healthcheckup.presentation

import android.app.DatePickerDialog
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sorimpower.app.core.ui.AppCobalt
import com.sorimpower.app.core.ui.AppGreen
import com.sorimpower.app.core.ui.AppNavy
import com.sorimpower.app.core.ui.AppOrange
import com.sorimpower.app.feature.healthcheckup.data.HealthCheckupWithMetrics
import com.sorimpower.app.feature.healthcheckup.domain.HealthCategory
import com.sorimpower.app.feature.healthcheckup.domain.HealthCheckupDraft
import com.sorimpower.app.feature.healthcheckup.domain.HealthMetricDraft
import com.sorimpower.app.feature.healthcheckup.domain.HealthMetricStatus
import com.sorimpower.app.feature.healthcheckup.domain.LongTermHealthAnalysis
import com.sorimpower.app.feature.healthcheckup.domain.HealthScreeningRecommendation
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class HealthCheckupPage { HOME, ADD, DETAIL }
private enum class HealthCheckupHomeTab(val label: String) { RECORDS("검진 기록"), INSIGHTS("AI 인사이트"), ARCHIVE("보관함") }

@Composable
fun HealthCheckupScreen(
    padding: PaddingValues,
    viewModel: HealthCheckupViewModel,
) {
    val checkups by viewModel.checkups.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val isWorking by viewModel.isWorking.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val longTermAnalysis by viewModel.longTermAnalysis.collectAsStateWithLifecycle()
    val isTrendAnalyzing by viewModel.isTrendAnalyzing.collectAsStateWithLifecycle()
    val trendErrorMessage by viewModel.trendErrorMessage.collectAsStateWithLifecycle()
    val screeningRecommendation by viewModel.screeningRecommendation.collectAsStateWithLifecycle()
    val isScreeningAnalyzing by viewModel.isScreeningAnalyzing.collectAsStateWithLifecycle()
    val screeningErrorMessage by viewModel.screeningErrorMessage.collectAsStateWithLifecycle()
    var page by remember { mutableStateOf(HealthCheckupPage.HOME) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = checkups.firstOrNull { it.checkup.id == selectedId }

    when {
        draft != null -> HealthExtractionReview(
            padding = padding,
            draft = draft!!,
            isSaving = isWorking,
            errorMessage = errorMessage,
            onUpdateDraft = viewModel::updateDraft,
            onAddMetric = viewModel::addMetric,
            onUpdateMetric = viewModel::updateMetric,
            onDeleteMetric = viewModel::deleteMetric,
            onCancel = {
                viewModel.cancelDraft()
                page = if (selectedId == null) HealthCheckupPage.HOME else HealthCheckupPage.DETAIL
            },
            onSave = { viewModel.saveDraft { page = HealthCheckupPage.HOME; selectedId = null } },
        )
        page == HealthCheckupPage.ADD -> HealthCheckupAdd(
            padding = padding,
            isWorking = isWorking,
            errorMessage = errorMessage,
            onBack = { page = HealthCheckupPage.HOME },
            onExtract = viewModel::extractDocument,
        )
        page == HealthCheckupPage.DETAIL && selected != null -> HealthCheckupDetail(
            padding = padding,
            checkup = selected,
            viewModel = viewModel,
            onBack = { page = HealthCheckupPage.HOME; selectedId = null },
            onEdit = { viewModel.edit(selected) },
            onDeleted = { page = HealthCheckupPage.HOME; selectedId = null },
        )
        else -> HealthCheckupHome(
            padding = padding,
            checkups = checkups,
            longTermAnalysis = longTermAnalysis,
            isTrendAnalyzing = isTrendAnalyzing,
            trendErrorMessage = trendErrorMessage,
            onAnalyzeTrend = viewModel::analyzeLongTermHealthTrend,
            screeningRecommendation = screeningRecommendation,
            isScreeningAnalyzing = isScreeningAnalyzing,
            screeningErrorMessage = screeningErrorMessage,
            onAnalyzeOptions = viewModel::analyzeScreeningOptions,
            onAdd = { page = HealthCheckupPage.ADD },
            onOpen = { selectedId = it.checkup.id; page = HealthCheckupPage.DETAIL },
        )
    }
}

@Composable
private fun HealthCheckupHome(
    padding: PaddingValues,
    checkups: List<HealthCheckupWithMetrics>,
    longTermAnalysis: LongTermHealthAnalysis?,
    isTrendAnalyzing: Boolean,
    trendErrorMessage: String?,
    onAnalyzeTrend: () -> Unit,
    screeningRecommendation: HealthScreeningRecommendation?,
    isScreeningAnalyzing: Boolean,
    screeningErrorMessage: String?,
    onAnalyzeOptions: (android.net.Uri) -> Unit,
    onAdd: () -> Unit,
    onOpen: (HealthCheckupWithMetrics) -> Unit,
) {
    var selectedTab by remember { mutableStateOf(HealthCheckupHomeTab.RECORDS) }
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(HealthCheckupHomeTab.entries) { tab -> FilterChip(selectedTab == tab, { selectedTab = tab }, { Text(tab.label) }) } } }
        if (selectedTab == HealthCheckupHomeTab.RECORDS) {
        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp).background(AppOrange.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.FavoriteBorder, null, tint = AppOrange)
                        }
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text("건강검진 기록", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                            Text("원본과 검사값을 장기간 보관하고 비교해요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Button(onClick = onAdd, Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Text("건강검진 추가", Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
        }
        if (selectedTab == HealthCheckupHomeTab.INSIGHTS) {
        item { ScreeningOptionCard(screeningRecommendation, isScreeningAnalyzing, screeningErrorMessage, onAnalyzeOptions) }
        item {
            LongTermTrendCard(
                checkupCount = checkups.size,
                analysis = longTermAnalysis,
                isAnalyzing = isTrendAnalyzing,
                errorMessage = trendErrorMessage,
                onAnalyze = onAnalyzeTrend,
            )
        }
        }
        if (selectedTab != HealthCheckupHomeTab.INSIGHTS) {
        item {
            Text(if (selectedTab == HealthCheckupHomeTab.ARCHIVE) "원본 문서 보관함" else "등록된 건강검진", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        }
        if (checkups.isEmpty()) {
            item { SimpleInfoCard("아직 등록된 건강검진이 없어요. PDF나 결과표 이미지를 추가해 보세요.") }
        } else {
            items(checkups, key = { it.checkup.id }) { item ->
                Card(
                    Modifier.fillMaxWidth().clickable { onOpen(item) },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(1.dp),
                ) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.checkup.displayTitle(), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${LocalDate.ofEpochDay(item.checkup.checkupDateEpochDay).format(DATE_FORMAT)} · 검사 ${item.metrics.size}개",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            item.checkup.hospitalName.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun ScreeningOptionCard(
    recommendation: HealthScreeningRecommendation?,
    isAnalyzing: Boolean,
    errorMessage: String?,
    onAnalyze: (android.net.Uri) -> Unit,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onAnalyze) }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = AppCobalt.copy(alpha = .06f))) {
        Column(Modifier.padding(18.dp)) {
            Text("유료 선택검사 추천", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
            Text("검사기관의 선택항목 안내 PDF를 올리면 내 검진 기록과 함께 검토해요.", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { picker.launch(arrayOf("application/pdf")) }, enabled = !isAnalyzing, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                if (isAnalyzing) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(17.dp))
                Text(if (isAnalyzing) "선택항목 검토 중…" else "안내 PDF로 추천받기", Modifier.padding(start = 7.dp))
            }
            errorMessage?.let { Text(it, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            recommendation?.let { result ->
                Text(result.summary, Modifier.padding(top = 14.dp), fontWeight = FontWeight.Medium)
                result.recommendations.forEach { item ->
                    Card(Modifier.fillMaxWidth().padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${item.priority} · ${item.name}", fontWeight = FontWeight.Black, color = AppCobalt)
                            Text(item.reason, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall)
                            item.clinicalNote.takeIf(String::isNotBlank)?.let { Text("의학적 소견: $it", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = AppNavy) }
                        }
                    }
                }
                Text(result.caution, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LongTermTrendCard(
    checkupCount: Int,
    analysis: LongTermHealthAnalysis?,
    isAnalyzing: Boolean,
    errorMessage: String?,
    onAnalyze: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = AppCobalt)
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text("AI 건강 추이 분석", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                    Text("검수 완료된 ${checkupCount}건의 검사값만 비교합니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(
                onClick = onAnalyze,
                enabled = checkupCount >= 2 && !isAnalyzing,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            ) {
                if (isAnalyzing) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = Color.White)
                else Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(17.dp))
                Text(
                    when {
                        isAnalyzing -> "여러 해의 기록 분석 중…"
                        analysis == null -> "전체 건강 추이 분석하기"
                        else -> "현재 기록으로 다시 분석"
                    },
                    Modifier.padding(start = 7.dp),
                )
            }
            if (checkupCount < 2) {
                Text("서로 다른 연도의 건강검진을 2건 이상 저장하면 분석할 수 있어요.", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            errorMessage?.let { Text(it, Modifier.padding(top = 10.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            analysis?.let { result ->
                Card(Modifier.fillMaxWidth().padding(top = 16.dp), colors = CardDefaults.cardColors(containerColor = AppCobalt.copy(alpha = .08f)), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("핵심 요약", fontWeight = FontWeight.Black, color = AppCobalt)
                        Text(result.summary, Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                TrendSection("우선 확인할 포인트", result.attentionChanges, AppOrange)
                TrendSection("다음 행동", result.recommendations, AppNavy)
                TrendSection("긍정적인 변화", result.positiveChanges, AppGreen)
                if (result.missingInformation.isNotEmpty()) TrendSection("해석의 한계", result.missingInformation, MaterialTheme.colorScheme.onSurfaceVariant)
                if (result.medicalConsultationSuggested) {
                    Text("일부 변화는 다음 진료 또는 검진에서 의료진과 함께 확인해 볼 가치가 있습니다.", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelSmall, color = AppOrange)
                }
            }
        }
    }
}

@Composable
private fun TrendSection(title: String, items: List<String>, color: Color) {
    if (items.isEmpty()) return
    Text(title, Modifier.padding(top = 14.dp), fontWeight = FontWeight.Bold, color = color, style = MaterialTheme.typography.labelLarge)
    items.forEachIndexed { index, item ->
        Card(Modifier.fillMaxWidth().padding(top = 7.dp), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = .07f)), shape = RoundedCornerShape(14.dp)) {
            Row(Modifier.padding(11.dp), verticalAlignment = Alignment.Top) {
                Text("${index + 1}", fontWeight = FontWeight.Black, color = color)
                Text(item, Modifier.padding(start = 9.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun HealthCheckupAdd(
    padding: PaddingValues,
    isWorking: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onExtract: (android.net.Uri, LocalDate, String, String, String) -> Unit,
) {
    val context = LocalContext.current
    var date by remember { mutableStateOf(LocalDate.now()) }
    var hospital by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }
    var selectedUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var selectedName by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        selectedUri = uri
        selectedName = uri?.lastPathSegment?.substringAfterLast('/').orEmpty()
    }
    val openDatePicker = {
        DatePickerDialog(context, { _, year, month, day -> date = LocalDate.of(year, month + 1, day) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BackHeader("건강검진 추가", onBack) }
        item {
            OutlinedButton(onClick = openDatePicker, Modifier.fillMaxWidth()) { Text("검진일  ${date.format(DATE_FORMAT)}") }
        }
        item { OutlinedTextField(hospital, { hospital = it.take(100) }, Modifier.fillMaxWidth(), label = { Text("검진기관 (선택)") }, singleLine = true) }
        item { OutlinedTextField(title, { title = it.take(100) }, Modifier.fillMaxWidth(), label = { Text("검진명 (선택)") }, singleLine = true) }
        item { OutlinedTextField(memo, { memo = it.take(500) }, Modifier.fillMaxWidth(), label = { Text("메모 (선택)") }, minLines = 2, maxLines = 4) }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text("원본 문서", fontWeight = FontWeight.Black)
                    Text(selectedName.ifBlank { "PDF 또는 이미지 파일을 선택해 주세요." }, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { picker.launch(arrayOf("application/pdf", "image/*")) }, Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Icon(Icons.Rounded.Description, null, Modifier.size(18.dp))
                        Text("파일 선택", Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
        errorMessage?.let { item { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
        item {
            Button(
                onClick = { selectedUri?.let { onExtract(it, date, hospital, title, memo) } },
                enabled = selectedUri != null && !isWorking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isWorking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                else Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
                Text(if (isWorking) "문서 분석 중…" else "AI로 검사값 추출", Modifier.padding(start = 7.dp))
            }
        }
        item { Text("AI 추출 결과는 바로 저장되지 않으며 다음 화면에서 직접 확인하고 수정합니다.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun HealthExtractionReview(
    padding: PaddingValues,
    draft: HealthCheckupDraft,
    isSaving: Boolean,
    errorMessage: String?,
    onUpdateDraft: (HealthCheckupDraft) -> Unit,
    onAddMetric: () -> Unit,
    onUpdateMetric: (HealthMetricDraft) -> Unit,
    onDeleteMetric: (String) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val context = LocalContext.current
    var editingMetric by remember { mutableStateOf<HealthMetricDraft?>(null) }
    val checkupDate = LocalDate.ofEpochDay(draft.checkupDateEpochDay)
    val openDatePicker = {
        DatePickerDialog(
            context,
            { _, year, month, day ->
                onUpdateDraft(draft.copy(checkupDateEpochDay = LocalDate.of(year, month + 1, day).toEpochDay()))
            },
            checkupDate.year,
            checkupDate.monthValue - 1,
            checkupDate.dayOfMonth,
        ).show()
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BackHeader("추출 데이터 확인", onCancel) }
        item { Text("AI가 읽은 값이 원본과 같은지 확인한 뒤 저장해 주세요.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (draft.aiSummary.isNotBlank()) item { SimpleInfoCard(draft.aiSummary) }
        item {
            OutlinedButton(onClick = openDatePicker, Modifier.fillMaxWidth()) {
                Text("검진일  ${checkupDate.format(DATE_FORMAT)}")
            }
        }
        item { OutlinedTextField(draft.hospitalName, { onUpdateDraft(draft.copy(hospitalName = it)) }, Modifier.fillMaxWidth(), label = { Text("검진기관") }, singleLine = true) }
        item { OutlinedTextField(draft.title, { onUpdateDraft(draft.copy(title = it)) }, Modifier.fillMaxWidth(), label = { Text("검진명") }, singleLine = true) }
        item { OutlinedTextField(draft.memo, { onUpdateDraft(draft.copy(memo = it)) }, Modifier.fillMaxWidth(), label = { Text("메모") }, minLines = 2) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("검사 결과 ${draft.metrics.size}개", Modifier.weight(1f), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = onAddMetric) { Icon(Icons.Rounded.Add, null, Modifier.size(16.dp)); Text("항목", Modifier.padding(start = 4.dp)) }
            }
        }
        items(draft.metrics, key = { it.id }) { metric ->
            MetricReviewCard(metric, onEdit = { editingMetric = metric }, onDelete = { onDeleteMetric(metric.id) })
        }
        errorMessage?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        item {
            Button(onClick = onSave, enabled = !isSaving, modifier = Modifier.fillMaxWidth()) {
                if (isSaving) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = Color.White)
                Text(if (isSaving) "저장 중…" else "확인하고 저장", Modifier.padding(start = if (isSaving) 7.dp else 0.dp))
            }
        }
    }
    editingMetric?.let { metric ->
        MetricEditDialog(metric, onDismiss = { editingMetric = null }, onSave = { onUpdateMetric(it); editingMetric = null })
    }
}

@Composable
private fun HealthCheckupDetail(
    padding: PaddingValues,
    checkup: HealthCheckupWithMetrics,
    viewModel: HealthCheckupViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    val grouped = checkup.metrics.sortedBy { it.sortOrder }.groupBy { it.category }
    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { BackHeader(checkup.checkup.displayTitle(), onBack) }
        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text(LocalDate.ofEpochDay(checkup.checkup.checkupDateEpochDay).format(DATE_FORMAT), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                    checkup.checkup.hospitalName.takeIf(String::isNotBlank)?.let { Text(it, Modifier.padding(top = 4.dp)) }
                    checkup.checkup.aiSummary.takeIf(String::isNotBlank)?.let { Text(it, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Edit, null, Modifier.size(16.dp)); Text("수정", Modifier.padding(start = 4.dp)) }
                        if (checkup.checkup.originalFilePath.isNotBlank()) {
                            OutlinedButton(onClick = {
                                val uri = viewModel.documentUri(checkup.checkup.originalFilePath)
                                val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, checkup.checkup.originalMimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                runCatching { context.startActivity(Intent.createChooser(intent, "원본 문서 보기")) }
                                    .onFailure { Toast.makeText(context, "이 문서를 열 수 있는 앱이 없어요.", Toast.LENGTH_SHORT).show() }
                            }, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Description, null, Modifier.size(16.dp)); Text("원본", Modifier.padding(start = 4.dp)) }
                        }
                    }
                }
            }
        }
        grouped.forEach { (categoryName, metrics) ->
            item { Text(enumValues<HealthCategory>().firstOrNull { it.name == categoryName }?.label ?: "기타", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium) }
            items(metrics, key = { it.id }) { metric ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(metric.name, fontWeight = FontWeight.Bold)
                            metric.referenceText.takeIf(String::isNotBlank)?.let { Text("참고 $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(metric.displayValue(), fontWeight = FontWeight.Black, color = metric.statusColor())
                            Text(enumValues<HealthMetricStatus>().firstOrNull { it.name == metric.status }?.label ?: "미확인", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = { confirmDelete = true }, Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                Text("건강검진 삭제", Modifier.padding(start = 6.dp), color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("건강검진을 삭제할까요?") },
        text = { Text("검사값과 보관 중인 원본 파일이 함께 삭제됩니다.") },
        confirmButton = { Button(onClick = { confirmDelete = false; viewModel.delete(checkup, onDeleted) }) { Text("삭제") } },
        dismissButton = { OutlinedButton(onClick = { confirmDelete = false }) { Text("취소") } },
    )
}

@Composable
private fun MetricReviewCard(metric: HealthMetricDraft, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onEdit), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(metric.name.ifBlank { "새 검사 항목" }, fontWeight = FontWeight.Bold)
                Text(metric.category.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(metric.displayValue(), fontWeight = FontWeight.Black, color = AppCobalt)
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "항목 삭제", tint = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun MetricEditDialog(metric: HealthMetricDraft, onDismiss: () -> Unit, onSave: (HealthMetricDraft) -> Unit) {
    var value by remember(metric) { mutableStateOf(metric) }
    var number by remember(metric) { mutableStateOf(metric.value?.toString().orEmpty()) }
    var min by remember(metric) { mutableStateOf(metric.referenceMin?.toString().orEmpty()) }
    var max by remember(metric) { mutableStateOf(metric.referenceMax?.toString().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("검사 항목 수정", fontWeight = FontWeight.Black) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value.name, { value = value.copy(name = it) }, label = { Text("검사명") }, singleLine = true)
                OutlinedTextField(value.normalizedName, { value = value.copy(normalizedName = it) }, label = { Text("표준 검사명") }, singleLine = true)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(HealthCategory.entries) { category -> FilterChip(value.category == category, { value = value.copy(category = category) }, { Text(category.label) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(number, { number = decimalInput(it) }, Modifier.weight(1f), label = { Text("수치") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(value.unit, { value = value.copy(unit = it) }, Modifier.weight(1f), label = { Text("단위") }, singleLine = true)
                }
                OutlinedTextField(value.stringValue, { value = value.copy(stringValue = it) }, label = { Text("문자 결과") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(min, { min = decimalInput(it) }, Modifier.weight(1f), label = { Text("참고 최소") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedTextField(max, { max = decimalInput(it) }, Modifier.weight(1f), label = { Text("참고 최대") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                }
                OutlinedTextField(value.referenceText, { value = value.copy(referenceText = it) }, label = { Text("참고범위 원문") }, singleLine = true)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(HealthMetricStatus.entries) { status -> FilterChip(value.status == status, { value = value.copy(status = status) }, { Text(status.label) }) }
                }
                OutlinedTextField(value.sourceText, { value = value.copy(sourceText = it) }, label = { Text("문서 원문") }, minLines = 2)
            }
        },
        confirmButton = { Button(onClick = { onSave(value.copy(value = number.toDoubleOrNull(), referenceMin = min.toDoubleOrNull(), referenceMax = max.toDoubleOrNull())) }) { Text("적용") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun BackHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "뒤로") }
        Text(title, Modifier.padding(start = 4.dp), fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun SimpleInfoCard(text: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppCobalt.copy(alpha = .07f)), shape = RoundedCornerShape(18.dp)) {
        Text(text, Modifier.padding(16.dp), color = AppNavy)
    }
}

private fun com.sorimpower.app.feature.healthcheckup.data.HealthCheckupEntity.displayTitle(): String =
    title.ifBlank { "${LocalDate.ofEpochDay(checkupDateEpochDay).year} 건강검진" }

private fun HealthMetricDraft.displayValue(): String = value?.let { "${formatNumber(it)} $unit".trim() }
    ?: stringValue.ifBlank { "값 미입력" }

private fun com.sorimpower.app.feature.healthcheckup.data.HealthMetricEntity.displayValue(): String = value?.let { "${formatNumber(it)} $unit".trim() }
    ?: stringValue.ifBlank { "—" }

private fun com.sorimpower.app.feature.healthcheckup.data.HealthMetricEntity.statusColor(): Color = when (status) {
    HealthMetricStatus.NORMAL.name -> AppGreen
    HealthMetricStatus.HIGH.name, HealthMetricStatus.LOW.name, HealthMetricStatus.WARNING.name -> AppOrange
    else -> AppNavy
}

private fun formatNumber(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(value).trimEnd('0').trimEnd('.')
private fun decimalInput(value: String): String = value.filterIndexed { index, char -> char.isDigit() || char == '.' || (char == '-' && index == 0) }.take(20)
private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREAN)
