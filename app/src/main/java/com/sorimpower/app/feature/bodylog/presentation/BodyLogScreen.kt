package com.sorimpower.app.feature.bodylog.presentation

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.sorimpower.app.feature.bodylog.data.MealItemInput
import com.sorimpower.app.feature.bodylog.data.MealQuickTemplate
import com.sorimpower.app.feature.bodylog.data.MealWithDetails
import com.sorimpower.app.feature.bodylog.data.MealCalorieEstimateEntity
import com.sorimpower.app.feature.bodylog.data.MounjaroInjectionEntity
import com.sorimpower.app.feature.bodylog.data.WeightEntryEntity
import com.sorimpower.app.feature.bodylog.domain.BodyLogState
import com.sorimpower.app.feature.bodylog.domain.BodyLogAiAnalysis
import com.sorimpower.app.feature.bodylog.domain.ChartPeriod
import com.sorimpower.app.feature.bodylog.domain.ChartPoint
import com.sorimpower.app.feature.bodylog.domain.MealType
import com.sorimpower.app.feature.bodylog.domain.dailyRepresentatives
import com.sorimpower.app.feature.bodylog.domain.localDate
import com.sorimpower.app.core.ui.AppCobalt
import com.sorimpower.app.core.ui.AppNavy
import com.sorimpower.app.core.ui.AppOrange
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.launch

@Composable
fun BodyLogScreen(padding: PaddingValues, viewModel: BodyLogViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val aiAnalysis by viewModel.aiAnalysis.collectAsStateWithLifecycle()
    val isAiAnalyzing by viewModel.isAiAnalyzing.collectAsStateWithLifecycle()
    val aiAnalysisError by viewModel.aiAnalysisError.collectAsStateWithLifecycle()
    val period = ChartPeriod.MONTH
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var mealFilterDate by remember { mutableStateOf<LocalDate?>(LocalDate.now()) }
    var showDailyEntry by remember { mutableStateOf(false) }
    var showAllRecordHistory by remember { mutableStateOf(false) }
    var showWeightInput by remember { mutableStateOf(false) }
    var showMealInput by remember { mutableStateOf(false) }
    var showQuickMealTemplateInput by remember { mutableStateOf(false) }
    var showQuickMealTemplateManager by remember { mutableStateOf(false) }
    var quickMealEditMode by remember { mutableStateOf(false) }
    var deletingQuickMealTemplate by remember { mutableStateOf<MealQuickTemplate?>(null) }
    var showGoalInput by remember { mutableStateOf(false) }
    var showMounjaroInput by remember { mutableStateOf(false) }
    var editingMeal by remember { mutableStateOf<MealWithDetails?>(null) }
    var editingMounjaroInjection by remember { mutableStateOf<MounjaroInjectionEntity?>(null) }
    var editingMounjaroReminder by remember { mutableStateOf<MounjaroInjectionEntity?>(null) }
    var deletingMeal by remember { mutableStateOf<MealWithDetails?>(null) }
    var deletingMounjaroInjection by remember { mutableStateOf<MounjaroInjectionEntity?>(null) }
    var expandedPhotoPath by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val points = remember(state.weights, period, selectedDate) { chartPoints(state.weights, period, selectedDate) }
    val mealsByDate = remember(state.meals, mealFilterDate) {
        state.meals.asSequence()
            .filter { mealFilterDate == null || it.meal.localDate() == mealFilterDate }
            .groupBy { it.meal.localDate() }
            .mapValues { (_, meals) -> meals.sortedByDescending { it.meal.eatenAt } }
    }
    val injectionsByDate = remember(state.mounjaroInjections, mealFilterDate) {
        state.mounjaroInjections.asSequence()
            .filter { injection -> mealFilterDate == null || injection.localDate() == mealFilterDate }
            .groupBy { it.localDate() }
            .mapValues { (_, injections) -> injections.sortedByDescending { it.injectedAt } }
    }
    val recordDates = remember(mealsByDate, injectionsByDate) { (mealsByDate.keys + injectionsByDate.keys).distinct().sortedDescending() }
    val dailyCaloriesByDate = remember(state.dailyCalories) { state.dailyCalories.associateBy { LocalDate.ofEpochDay(it.dateEpochDay) } }
    val mealCaloriesById = remember(state.mealCalories) { state.mealCalories.associateBy(MealCalorieEstimateEntity::mealId) }

    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        state = listState,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            BodySummaryCard(
                state,
                onGoal = { showGoalInput = true },
                onWeightsHiddenChange = viewModel::setWeightsHidden,
            )
        }
        item {
            PeriodNavigation(period, selectedDate, onDateChange = {
                selectedDate = it
                if (!showAllRecordHistory) mealFilterDate = it
            })
            WeightChart(
                points = points,
                selectedMonth = selectedDate,
                dailyCalories = state.dailyCalories,
                latestWeightKg = state.latestWeight?.weightKg,
                targetWeightKg = state.activeGoal?.targetWeightKg,
                weightsHidden = state.weightsHidden,
            )
        }
        item {
            WeightProgressAiCard(
                analysis = aiAnalysis,
                isAnalyzing = isAiAnalyzing,
                errorMessage = aiAnalysisError,
                onAnalyze = viewModel::analyzeWeightProgress,
            )
        }
        item {
            MonthCalendar(
                date = selectedDate,
                state = state,
                onSelect = {
                    selectedDate = it
                    if (!showAllRecordHistory) mealFilterDate = it
                },
                weightsHidden = state.weightsHidden,
            )
        }
        item {
            OutlinedButton(
                onClick = { showDailyEntry = !showDailyEntry },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    if (showDailyEntry) "기록 추가 닫기" else "${selectedDate.monthValue}월 ${selectedDate.dayOfMonth}일 기록 추가",
                    Modifier.padding(start = 7.dp),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (showDailyEntry) item {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(1.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("일일 기록 추가", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                    Text(
                        "${selectedDate.monthValue}월 ${selectedDate.dayOfMonth}일 기록",
                        Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { showWeightInput = true },
                            modifier = Modifier.weight(1f).height(48.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                        ) {
                            Icon(Icons.Rounded.MonitorWeight, null, Modifier.size(17.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("체중", Modifier.padding(start = 4.dp), fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { showMealInput = true },
                            modifier = Modifier.weight(1f).height(48.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                        ) {
                            Icon(Icons.Rounded.Restaurant, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("식사", Modifier.padding(start = 4.dp), fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { showMounjaroInput = true },
                            modifier = Modifier.weight(1f).height(48.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                        ) {
                            Icon(Icons.Rounded.Medication, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("주사", Modifier.padding(start = 4.dp), fontWeight = FontWeight.Bold)
                        }
                    }
                if (state.quickMealTemplates.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("빠른 식사", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        OutlinedButton(onClick = { quickMealEditMode = !quickMealEditMode }, modifier = Modifier.height(32.dp)) {
                            Text(if (quickMealEditMode) "완료" else "편집", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(onClick = { showQuickMealTemplateInput = true }, modifier = Modifier.height(32.dp)) {
                            Text("+ 추가", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Column(Modifier.padding(top = 7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        state.quickMealTemplates.take(4).chunked(2).forEach { rowTemplates ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                rowTemplates.forEach { template ->
                                    OutlinedButton(
                                        onClick = {
                                            if (quickMealEditMode) {
                                                deletingQuickMealTemplate = template
                                            } else {
                                                viewModel.saveMeal(
                                                    eatenAt = timestampForDate(selectedDate, timeForTimestamp(System.currentTimeMillis())),
                                                    mealType = template.mealType,
                                                    items = template.items.map { MealItemInput(it) },
                                                    note = template.note,
                                                    tags = template.tags,
                                                    photoUris = emptyList(),
                                                ) { Toast.makeText(context, "식사 기록을 추가했어요.", Toast.LENGTH_SHORT).show() }
                                            }
                                        },
                                        modifier = Modifier.weight(1f).height(58.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    ) {
                                        Column(Modifier.fillMaxWidth()) {
                                            Text(if (quickMealEditMode) "삭제" else MealType.from(template.mealType).label, color = if (quickMealEditMode) MaterialTheme.colorScheme.error else AppCobalt, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                            Text(template.items.take(2).joinToString(" · ") + if (template.items.size > 2) " 외" else "", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                                if (rowTemplates.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    if (state.quickMealTemplates.size > 4) {
                        Text(
                            "전체 ${state.quickMealTemplates.size}개 보기",
                            Modifier.padding(top = 9.dp).clickable { showQuickMealTemplateManager = true },
                            color = AppCobalt,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                } else {
                    OutlinedButton(onClick = { showQuickMealTemplateInput = true }, modifier = Modifier.padding(top = 12.dp).height(36.dp)) {
                        Text("+ 빠른 식사 추가", style = MaterialTheme.typography.labelMedium)
                    }
                }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = {
                    showAllRecordHistory = !showAllRecordHistory
                    mealFilterDate = if (showAllRecordHistory) null else selectedDate
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (showAllRecordHistory) "선택 날짜 기록만 보기" else "전체 식사 · 주사 기록 보기")
            }
        }
        if (recordDates.isNotEmpty()) recordDates.forEach { date ->
            val injections = injectionsByDate[date].orEmpty()
            val meals = mealsByDate[date].orEmpty()
            item(key = "record-date-$date") {
                RecordDateHeader(
                    date,
                    injections.size,
                    meals.size,
                    dailyCaloriesByDate[date],
                )
            }
            items(injections, key = { it.id }) { injection ->
                MounjaroInjectionCard(
                    injection,
                    isLatest = injection.id == state.latestMounjaroInjection?.id,
                    onEdit = { editingMounjaroInjection = injection },
                    onDelete = { deletingMounjaroInjection = injection },
                    onEditReminder = { editingMounjaroReminder = injection },
                )
            }
            items(meals, key = { it.meal.id }) { meal ->
                MealCard(
                    meal,
                    calorieEstimate = mealCaloriesById[meal.meal.id],
                    onPhotoClick = { expandedPhotoPath = it },
                    onEdit = { editingMeal = meal },
                    onDelete = { deletingMeal = meal },
                )
            }
        }
    }

    expandedPhotoPath?.let { path -> ExpandedMealPhoto(path, onDismiss = { expandedPhotoPath = null }) }
    deletingMeal?.let { meal ->
        DeleteRecordDialog(
            title = "식사 기록을 삭제할까요?",
            message = "식사 내용과 연결된 사진도 함께 삭제되며 복구할 수 없어요.",
            onDismiss = { deletingMeal = null },
            onConfirm = { viewModel.deleteMeal(meal); deletingMeal = null },
        )
    }
    deletingQuickMealTemplate?.let { template ->
        DeleteRecordDialog(
            title = "빠른 식사를 삭제할까요?",
            message = "삭제해도 기존 식사 기록은 유지됩니다.",
            onDismiss = { deletingQuickMealTemplate = null },
            onConfirm = {
                viewModel.deleteQuickMealTemplate(template)
                deletingQuickMealTemplate = null
            },
        )
    }
    deletingMounjaroInjection?.let { injection ->
        DeleteRecordDialog(
            title = "주사 기록을 삭제할까요?",
            message = "투여 기록과 해당 기록의 알림 설정이 삭제되며 복구할 수 없어요.",
            onDismiss = { deletingMounjaroInjection = null },
            onConfirm = { viewModel.deleteMounjaroInjection(injection); deletingMounjaroInjection = null },
        )
    }

    if (showWeightInput) WeightInputDialog(
        initial = null,
        fallbackWeight = state.latestWeight?.weightKg,
        selectedDate = selectedDate,
        onDismiss = { showWeightInput = false },
        onSave = { weight, bodyFat, condition, note, measuredAt ->
            viewModel.saveWeight(weightKg = weight, measuredAt = measuredAt, bodyFatPercent = bodyFat, condition = condition, note = note)
            showWeightInput = false
        },
    )
    if (showMealInput) MealInputDialog(viewModel, existing = null, selectedDate = selectedDate, onDismiss = { showMealInput = false }) { showMealInput = false }
    if (showQuickMealTemplateInput) QuickMealTemplateInputDialog(
        onDismiss = { showQuickMealTemplateInput = false },
        onSave = { type, foods, note, tags ->
            viewModel.saveQuickMealTemplate(type.name, foods, note, tags) { showQuickMealTemplateInput = false }
        },
    )
    if (showQuickMealTemplateManager) QuickMealTemplateManagerDialog(
        templates = state.quickMealTemplates,
        onDismiss = { showQuickMealTemplateManager = false },
        onDelete = viewModel::deleteQuickMealTemplate,
    )
    editingMeal?.let { meal -> MealInputDialog(viewModel, existing = meal, selectedDate = meal.meal.localDate(), onDismiss = { editingMeal = null }) { editingMeal = null } }
    if (showGoalInput) GoalInputDialog(
        start = state.activeGoal?.startWeightKg ?: state.latestWeight?.weightKg,
        currentTarget = state.activeGoal?.targetWeightKg,
        currentTargetDate = state.activeGoal?.targetDateEpochDay?.let(LocalDate::ofEpochDay),
        onDismiss = { showGoalInput = false },
        onSave = { start, target, targetDate -> viewModel.saveGoal(start, target, targetDate); showGoalInput = false },
    )
    if (showMounjaroInput) MounjaroInputDialog(
        existing = null,
        onDismiss = { showMounjaroInput = false },
        onSave = { injectedAt, doseMg, sideEffects, note, reminderEnabled, reminderIntervalWeeks ->
            viewModel.saveMounjaroInjection(null, injectedAt, doseMg, sideEffects, note, reminderEnabled, reminderIntervalWeeks) {
                showMounjaroInput = false
                if (reminderEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
    )
    editingMounjaroInjection?.let { injection ->
        MounjaroInputDialog(
            existing = injection,
            onDismiss = { editingMounjaroInjection = null },
            onSave = { injectedAt, doseMg, sideEffects, note, reminderEnabled, reminderIntervalWeeks ->
                viewModel.saveMounjaroInjection(injection, injectedAt, doseMg, sideEffects, note, reminderEnabled, reminderIntervalWeeks) {
                    editingMounjaroInjection = null
                    if (reminderEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
        )
    }
    editingMounjaroReminder?.let { injection ->
        MounjaroReminderSettingsDialog(
            injection = injection,
            onDismiss = { editingMounjaroReminder = null },
            onSave = { enabled, intervalWeeks ->
                viewModel.updateMounjaroReminder(injection, enabled, intervalWeeks)
                editingMounjaroReminder = null
            },
        )
    }
}

@Composable
private fun QuickMealTemplateInputDialog(onDismiss: () -> Unit, onSave: (MealType, List<String>, String?, Set<String>) -> Unit) {
    var type by remember { mutableStateOf(MealType.LUNCH) }
    var foods by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf(emptySet<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("빠른 식사 추가", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(MealType.entries) { item -> FilterChip(type == item, { type = item }, label = { Text(item.label) }) } }
                OutlinedTextField(foods, { foods = it.take(500) }, Modifier.fillMaxWidth(), label = { Text("음식 (한 줄에 하나)") }, minLines = 3)
                OutlinedTextField(note, { note = it.take(200) }, Modifier.fillMaxWidth(), label = { Text("메모 (선택)") })
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(listOf("배고픔", "적당함", "과식", "외식", "야식")) { tag -> FilterChip(tag in tags, { tags = if (tag in tags) tags - tag else tags + tag }, label = { Text(tag) }) } }
            }
        },
        confirmButton = { Button(enabled = foods.lineSequence().any { it.isNotBlank() }, onClick = {
            onSave(type, foods.lineSequence().flatMap { it.split(',').asSequence() }.map(String::trim).filter(String::isNotBlank).toList(), note.ifBlank { null }, tags)
        }) { Text("추가") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun QuickMealTemplateManagerDialog(templates: List<MealQuickTemplate>, onDismiss: () -> Unit, onDelete: (MealQuickTemplate) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("빠른 식사 관리", fontWeight = FontWeight.Black) },
        text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(templates, key = MealQuickTemplate::id) { template ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${MealType.from(template.mealType).label} · ${template.items.joinToString(" · ")}", Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { onDelete(template) }) { Icon(Icons.Rounded.DeleteOutline, "빠른 식사 삭제", tint = MaterialTheme.colorScheme.error) }
            }
        } } },
        confirmButton = { Button(onClick = onDismiss) { Text("완료") } },
    )
}

@Composable
private fun BodySummaryCard(
    state: BodyLogState,
    onGoal: () -> Unit,
    onWeightsHiddenChange: (Boolean) -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary)))
                    .padding(24.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("CURRENT WEIGHT", color = Color.White.copy(alpha = .78f), fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("BODY LOG", color = Color.White.copy(alpha = .7f), style = MaterialTheme.typography.labelMedium)
                        IconButton(
                            onClick = { onWeightsHiddenChange(!state.weightsHidden) },
                            modifier = Modifier.size(32.dp).padding(start = 4.dp),
                        ) {
                            Icon(
                                if (state.weightsHidden) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                contentDescription = if (state.weightsHidden) "체중 표시" else "체중 숨기기",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(22.dp))
                Text(
                    state.latestWeight?.let { if (state.weightsHidden) HIDDEN_WEIGHT else "${formatWeight(it.weightKg)} kg" } ?: "첫 기록을 시작하세요",
                    color = Color.White,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                )
                state.activeGoal?.let {
                    Text(
                        if (state.weightsHidden) "목표 $HIDDEN_WEIGHT" else "목표 ${formatWeight(it.targetWeightKg)} kg",
                        color = Color.White.copy(alpha = .82f),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            if (state.latestWeight != null) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BodyMetric(if (state.weightsHidden) HIDDEN_WEIGHT else "${signed(state.startChange)} kg", "시작 대비", Modifier.weight(1f))
                    Column(
                        Modifier.weight(1f).clickable(onClick = onGoal).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)).padding(10.dp),
                    ) {
                        Text(if (state.weightsHidden) HIDDEN_WEIGHT else state.goalRemaining?.let { "${signed(it)} kg" } ?: "—", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black)
                        Text("목표까지 · 설정", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    BodyMetric(if (state.weightsHidden) HIDDEN_WEIGHT else state.sevenDayAverage?.let { "${formatWeight(it)} kg" } ?: "—", "7일 평균", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun BodyMetric(value: String, label: String, modifier: Modifier) {
    Column(modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)).padding(10.dp)) {
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Black)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun WeightProgressAiCard(
    analysis: BodyLogAiAnalysis?,
    isAnalyzing: Boolean,
    errorMessage: String?,
    onAnalyze: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text("AI 건강 경과 분석", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
                    Text("마운자로·식단·섭취 칼로리·체중 추세를 함께 검토해요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(
                onClick = onAnalyze,
                enabled = !isAnalyzing,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = Color.White)
                    Text("기록 분석 중…", Modifier.padding(start = 8.dp))
                } else {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(17.dp))
                    Text(if (analysis == null) "내 건강 기록 분석하기" else "현재 기록으로 다시 분석", Modifier.padding(start = 7.dp))
                }
            }
            errorMessage?.let {
                Text(it, Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            analysis?.let { result ->
                Text(result.headline, Modifier.padding(top = 16.dp), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                Text(result.trendSummary, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium)
                Text("핵심 판단", Modifier.padding(top = 14.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                Text(result.encouragement, Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                result.mealAssessment.takeIf(String::isNotBlank)?.let {
                    Text("식사 평가", Modifier.padding(top = 14.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Text(it, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium)
                }
                result.calorieAssessment.takeIf(String::isNotBlank)?.let {
                    Text("섭취 칼로리 평가", Modifier.padding(top = 14.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Text(it, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium)
                }
                if (result.nextSteps.isNotEmpty()) {
                    Text("다음 관리 포인트", Modifier.padding(top = 14.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    result.nextSteps.forEach { step ->
                        Text("• $step", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
                result.safetyNote.takeIf(String::isNotBlank)?.let {
                    Text(it, Modifier.padding(top = 14.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}

@Composable
private fun PeriodNavigation(period: ChartPeriod, date: LocalDate, onDateChange: (LocalDate) -> Unit) {
    val title = when (period) {
        ChartPeriod.DAY -> date.format(DateTimeFormatter.ofPattern("M월 d일"))
        ChartPeriod.WEEK -> {
            val weekNumber = weekIndexInMonth(date) + 1
            val ordinal = listOf("첫째", "둘째", "셋째", "넷째", "다섯째", "여섯째").getOrElse(weekNumber - 1) { "${weekNumber}번째" }
            "${date.monthValue}월 $ordinal 주"
        }
        ChartPeriod.MONTH -> date.format(DateTimeFormatter.ofPattern("yyyy년 M월"))
        ChartPeriod.YEAR -> "${date.year}년"
    }
    val move: (Long) -> Unit = { direction ->
        onDateChange(when (period) {
            ChartPeriod.DAY -> date.plusDays(direction)
            ChartPeriod.WEEK -> moveMonthWeek(date, direction.toInt())
            ChartPeriod.MONTH -> date.plusMonths(direction)
            ChartPeriod.YEAR -> date.plusYears(direction)
        })
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        OutlinedButton(onClick = { move(-1) }) { Text("‹") }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        OutlinedButton(onClick = { move(1) }, enabled = date.isBefore(LocalDate.now())) { Text("›") }
    }
}

private fun weekIndexInMonth(date: LocalDate): Int {
    val month = YearMonth.from(date)
    val firstWeekStart = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val currentWeekStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return (ChronoUnit.DAYS.between(firstWeekStart, currentWeekStart) / 7).toInt()
}

private fun lastWeekIndexInMonth(month: YearMonth): Int {
    val firstWeekStart = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val lastWeekStart = month.atEndOfMonth().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return (ChronoUnit.DAYS.between(firstWeekStart, lastWeekStart) / 7).toInt()
}

private fun dateForMonthWeek(month: YearMonth, weekIndex: Int): LocalDate {
    val weekStart = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(weekIndex.toLong())
    return if (weekStart.isBefore(month.atDay(1))) month.atDay(1) else weekStart
}

private fun moveMonthWeek(date: LocalDate, direction: Int): LocalDate {
    val month = YearMonth.from(date)
    val targetIndex = weekIndexInMonth(date) + direction
    return when {
        targetIndex < 0 -> {
            val previousMonth = month.minusMonths(1)
            dateForMonthWeek(previousMonth, lastWeekIndexInMonth(previousMonth))
        }
        targetIndex > lastWeekIndexInMonth(month) -> dateForMonthWeek(month.plusMonths(1), 0)
        else -> dateForMonthWeek(month, targetIndex)
    }
}

@Composable
private fun WeightChart(points: List<ChartPoint>, selectedMonth: LocalDate, dailyCalories: List<com.sorimpower.app.feature.bodylog.data.DailyCalorieSummaryEntity>, latestWeightKg: Double?, targetWeightKg: Double?, weightsHidden: Boolean) {
    var selectedPoint by remember(points) { mutableStateOf<ChartPoint?>(null) }
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val zone = ZoneId.systemDefault()
    val month = YearMonth.from(selectedMonth)
    val minTimestamp = remember(month) { month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli() }
    val maxTimestamp = remember(month) { month.atEndOfMonth().atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli() }
    val timestampRange = maxTimestamp - minTimestamp
    val xAxisLabels = remember(month) {
        List(7) { index ->
            val day = 1 + ((month.lengthOfMonth() - 1) * index / 6)
            day.toString() + "일"
        }.distinct()
    }
    Card(Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp)) {
            if (points.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) { Text("이 기간에는 체중 기록이 없어요.") }
            } else {
                val minValue = points.minOf(ChartPoint::value)
                val maxValue = points.maxOf(ChartPoint::value)
                val displayMin = kotlin.math.floor(minOf(60.0, minValue) / 5.0) * 5.0
                val displayMax = kotlin.math.ceil(maxOf(100.0, maxValue) / 5.0) * 5.0
                val displayRange = displayMax - displayMin
                val yAxisValues = generateSequence(displayMin) { it + 5.0 }
                    .takeWhile { it <= displayMax + 0.001 }
                    .toList()
                selectedPoint?.let { point ->
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(point.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (weightsHidden) HIDDEN_WEIGHT else "${formatWeight(point.value)} kg", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                    }
                }
                Canvas(
                    Modifier.fillMaxWidth().height(210.dp).pointerInput(points) {
                        detectTapGestures { tap ->
                            val left = 38.dp.toPx()
                            val right = 8.dp.toPx()
                            val usableWidth = size.width - left - right
                            val tapped = points.minByOrNull { point ->
                                val x = if (timestampRange == 0L) size.width / 2f
                                else left + usableWidth * ((point.timestamp - minTimestamp).toFloat() / timestampRange.toFloat())
                                abs(tap.x - x)
                            }
                            selectedPoint = tapped
                        }
                    },
                ) {
                    val left = 38.dp.toPx(); val right = 8.dp.toPx(); val top = 12.dp.toPx(); val bottom = size.height - 12.dp.toPx()
                    val usableWidth = size.width - left - right
                    fun xFor(point: ChartPoint): Float = if (timestampRange == 0L) size.width / 2f
                    else left + usableWidth * ((point.timestamp - minTimestamp).toFloat() / timestampRange.toFloat())
                    fun yForValue(value: Double): Float = bottom - ((value - displayMin) / displayRange).toFloat() * (bottom - top)
                    fun yFor(point: ChartPoint): Float = yForValue(point.value)

                    val axisLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.rgb(105, 102, 110)
                        textSize = 10.dp.toPx()
                        textAlign = Paint.Align.RIGHT
                    }
                    yAxisValues.forEach { value ->
                        val y = yForValue(value)
                        drawLine(Color(0xFFE8E5EC), Offset(left, y), Offset(size.width - right, y), strokeWidth = 1.dp.toPx())
                        drawContext.canvas.nativeCanvas.drawText(formatWeight(value), left - 6.dp.toPx(), y + 4.dp.toPx(), axisLabelPaint)
                    }
                    xAxisLabels.indices.forEach { index ->
                        val x = left + usableWidth * index / (xAxisLabels.size - 1).toFloat()
                        drawLine(Color(0xFFF1EEF3), Offset(x, top), Offset(x, bottom), strokeWidth = 1.dp.toPx())
                    }
                    val path = Path()
                    points.forEachIndexed { index, point ->
                        val x = xFor(point)
                        val y = yFor(point)
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, primaryColor, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                    selectedPoint?.let { point ->
                        val x = xFor(point)
                        drawLine(primaryColor.copy(alpha = .25f), Offset(x, top), Offset(x, bottom), strokeWidth = 1.dp.toPx())
                    }
                    points.forEach { point ->
                        val x = xFor(point)
                        val y = yFor(point)
                        drawCircle(if (point == selectedPoint) primaryColor else tertiaryColor, if (point == selectedPoint) 5.dp.toPx() else 3.dp.toPx(), Offset(x, y))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(start = 32.dp, end = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    xAxisLabels.forEach { label ->
                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                DailyCalorieBarChart(
                    selectedMonth = month,
                    dailyCalories = dailyCalories,
                    latestWeightKg = latestWeightKg,
                    targetWeightKg = targetWeightKg,
                )
            }
        }
    }
}

@Composable
private fun DailyCalorieBarChart(
    selectedMonth: YearMonth,
    dailyCalories: List<com.sorimpower.app.feature.bodylog.data.DailyCalorieSummaryEntity>,
    latestWeightKg: Double?,
    targetWeightKg: Double?,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val values = remember(selectedMonth, dailyCalories) {
        dailyCalories.filter { YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)) == selectedMonth }
            .sortedBy { it.dateEpochDay }
    }
    if (values.isEmpty()) return
    val calorieReference = remember(latestWeightKg, targetWeightKg) {
        latestWeightKg?.let { dailyCalorieReference(it, targetWeightKg) }
    }
    val maxCalories = remember(values, calorieReference) {
        val highestReference = calorieReference?.maintenanceCalories ?: 0
        (kotlin.math.ceil(maxOf(values.maxOf { it.estimatedCalories }, highestReference) / 500.0) * 500.0).toInt().coerceAtLeast(1_500)
    }
    val xAxisDays = remember(selectedMonth) {
        List(7) { index ->
            1 + ((selectedMonth.lengthOfMonth() - 1) * index / 6)
        }.distinct()
    }
    var selectedSummary by remember(values) { mutableStateOf<com.sorimpower.app.feature.bodylog.data.DailyCalorieSummaryEntity?>(null) }
    Text("일별 섭취 칼로리", Modifier.padding(top = 16.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
    calorieReference?.let { reference ->
        Text(
            "현재 체중 기준 · 최소 " + reference.minimumCalories + " / 감량 " + reference.dietCalories + " / 유지 " + reference.maintenanceCalories + " kcal",
            Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    selectedSummary?.let { summary ->
        val date = LocalDate.ofEpochDay(summary.dateEpochDay)
        Text(
            date.format(DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)) + summary.estimatedCalories + " kcal",
            Modifier.padding(top = 4.dp),
            color = primaryColor,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelMedium,
        )
    }
    Canvas(
        Modifier.fillMaxWidth().height(126.dp).padding(top = 6.dp).pointerInput(values, selectedMonth) {
            detectTapGestures { tap ->
                val left = 70.dp.toPx()
                val right = 8.dp.toPx()
                val usableWidth = size.width - left - right
                selectedSummary = values.minByOrNull { summary ->
                    val day = LocalDate.ofEpochDay(summary.dateEpochDay).dayOfMonth
                    val x = left + usableWidth * (day - 1).toFloat() / (selectedMonth.lengthOfMonth() - 1).coerceAtLeast(1)
                    abs(tap.x - x)
                }
            }
        },
    ) {
        val left = 70.dp.toPx()
        val right = 8.dp.toPx()
        val top = 5.dp.toPx()
        val bottom = size.height - 22.dp.toPx()
        val usableWidth = size.width - left - right
        val axisLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(105, 102, 110)
            textSize = 10.dp.toPx()
            textAlign = Paint.Align.RIGHT
        }
        listOf(0, maxCalories / 2, maxCalories).distinct().forEach { calories ->
            val y = bottom - (calories.toFloat() / maxCalories) * (bottom - top)
            drawLine(Color(0xFFE8E5EC), Offset(left, y), Offset(size.width - right, y), strokeWidth = 1.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText(calories.toString(), left - 6.dp.toPx(), y + 4.dp.toPx(), axisLabelPaint)
        }
        calorieReference?.let { reference ->
            listOf("최소" to reference.minimumCalories, "감량" to reference.dietCalories, "유지" to reference.maintenanceCalories).forEach { (label, calories) ->
                val y = bottom - (calories.toFloat() / maxCalories).coerceIn(0f, 1f) * (bottom - top)
                val color = when (label) {
                    "최소" -> Color(0xFFD14343)
                    "감량" -> tertiaryColor
                    else -> primaryColor
                }
                drawLine(color.copy(alpha = .72f), Offset(left, y), Offset(size.width - right, y), strokeWidth = 1.5.dp.toPx())
                axisLabelPaint.color = when (label) {
                    "최소" -> android.graphics.Color.rgb(209, 67, 67)
                    "감량" -> android.graphics.Color.rgb(216, 116, 36)
                    else -> android.graphics.Color.rgb(41, 92, 176)
                }
                drawContext.canvas.nativeCanvas.drawText(label + " " + calories, left - 6.dp.toPx(), y + 4.dp.toPx(), axisLabelPaint)
                axisLabelPaint.color = android.graphics.Color.rgb(105, 102, 110)
            }
        }
        values.forEach { summary ->
            val day = LocalDate.ofEpochDay(summary.dateEpochDay).dayOfMonth
            val x = left + usableWidth * (day - 1).toFloat() / (selectedMonth.lengthOfMonth() - 1).coerceAtLeast(1)
            val y = bottom - (summary.estimatedCalories.toFloat() / maxCalories).coerceIn(0f, 1f) * (bottom - top)
            val isSelected = summary.dateEpochDay == selectedSummary?.dateEpochDay
            drawLine(
                if (isSelected) primaryColor else tertiaryColor,
                Offset(x, bottom),
                Offset(x, y),
                strokeWidth = if (isSelected) 7.dp.toPx() else 5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        val dateLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(105, 102, 110)
            textSize = 10.dp.toPx()
            textAlign = Paint.Align.CENTER
        }
        xAxisDays.forEach { day ->
            val x = left + usableWidth * (day - 1).toFloat() / (selectedMonth.lengthOfMonth() - 1).coerceAtLeast(1)
            drawContext.canvas.nativeCanvas.drawText("${day}일", x, size.height - 3.dp.toPx(), dateLabelPaint)
        }
    }
}

private data class DailyCalorieReference(
    val minimumCalories: Int,
    val maintenanceCalories: Int,
    val dietCalories: Int,
)

private fun dailyCalorieReference(currentWeightKg: Double, targetWeightKg: Double?): DailyCalorieReference {
    // Mifflin–St Jeor: 1989년생, 171cm 남성. 활동량이 미입력인 현재는 가벼운 활동(1.35)을 적용한다.
    val age = LocalDate.now().year - 1989
    val basalMetabolicRate = 10.0 * currentWeightKg + 6.25 * 171.0 - 5.0 * age + 5.0
    val maintenance = ((basalMetabolicRate * 1.35) / 10.0).toInt() * 10
    val deficit = if (targetWeightKg != null && currentWeightKg > targetWeightKg) 500 else 0
    return DailyCalorieReference(
        minimumCalories = 1_500,
        maintenanceCalories = maintenance,
        dietCalories = (maintenance - deficit).coerceAtLeast(1_500),
    )
}

@Composable
private fun MonthCalendar(date: LocalDate, state: BodyLogState, onSelect: (LocalDate) -> Unit, weightsHidden: Boolean) {
    val first = date.withDayOfMonth(1)
    val start = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    val weights = state.weights.dailyRepresentatives()
    val mealDates = state.meals.map { it.meal.localDate() }.toSet()
    val injectionDates = state.mounjaroInjections.map { it.localDate() }.toSet()
    val calories = state.dailyCalories.associateBy { LocalDate.ofEpochDay(it.dateEpochDay) }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(date.format(DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN)), Modifier.weight(1f), fontWeight = FontWeight.Black)
                OutlinedButton(onClick = { onSelect(LocalDate.now()) }) { Text("오늘") }
            }
            Row(Modifier.fillMaxWidth()) { listOf("일", "월", "화", "수", "목", "금", "토").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center) } }
            repeat(6) { week ->
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { day ->
                        val current = start.plusDays((week * 7 + day).toLong())
                        val weight = weights[current]
                        Column(
                            Modifier.weight(1f).aspectRatio(.68f).padding(2.dp).clickable { onSelect(current) }
                                .background(if (current == date) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, RoundedCornerShape(10.dp))
                                .padding(3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("${current.dayOfMonth}", color = if (current.month == date.month) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
                            weight?.let { Text(if (weightsHidden) "•••" else formatWeight(it.weightKg), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                            calories[current]?.let { Text("${it.estimatedCalories / 100}k", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold) }
                            Row(Modifier.height(13.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                if (current in mealDates) Box(Modifier.size(5.dp).background(MaterialTheme.colorScheme.tertiary, CircleShape))
                                if (current in injectionDates) {
                                    Box(Modifier.size(13.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                                        Text("주", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MealCard(meal: MealWithDetails, calorieEstimate: MealCalorieEstimateEntity?, onPhotoClick: (String) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            val photo = meal.photos.minByOrNull { it.sortOrder }
            if (photo != null) {
                AsyncImage(
                    model = File(photo.thumbnailPath),
                    contentDescription = "식사 사진 확대 보기",
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(15.dp))
                        .clickable { onPhotoClick(photo.localPath) }
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Restaurant, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("${MealType.from(meal.meal.mealType).label} · ${formatRecordTime(meal.meal.eatenAt)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                Text(meal.items.sortedBy { it.sortOrder }.joinToString(" · ") { it.name }, fontWeight = FontWeight.Bold)
                meal.meal.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text(
                    calorieEstimate?.let { "${it.estimatedCalories} kcal" } ?: "AI 칼로리 분석 대기",
                    Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (calorieEstimate != null) AppOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row {
                IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, "수정", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "삭제", tint = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun ExpandedMealPhoto(path: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = .92f)).clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = File(path),
                contentDescription = "확대된 식사 사진",
                modifier = Modifier.fillMaxSize().padding(24.dp).clickable(onClick = {}),
                contentScale = ContentScale.Fit,
            )
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "사진 닫기", tint = Color.White)
            }
        }
    }
}

@Composable
private fun MounjaroInjectionCard(injection: MounjaroInjectionEntity, isLatest: Boolean, onEdit: () -> Unit, onDelete: () -> Unit, onEditReminder: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f)),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Medication, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("마운자로 · ${formatWeight(injection.doseMg)} mg · ${formatRecordTime(injection.injectedAt)}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                injection.sideEffects.takeIf(String::isNotBlank)?.let {
                    Text("부작용: ${it.replace("|", " · ")}", style = MaterialTheme.typography.bodySmall)
                }
                injection.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text(
                    if (injection.reminderEnabled) "알림 · ${injection.reminderIntervalWeeks}주마다" else "알림 꺼짐",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Row {
                    IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, "수정", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconButton(onClick = onDelete) { Icon(Icons.Rounded.DeleteOutline, "삭제", tint = MaterialTheme.colorScheme.error) }
                }
                if (isLatest) OutlinedButton(onClick = onEditReminder) { Text("알림 설정") }
            }
        }
    }
}

@Composable
private fun DeleteRecordDialog(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Black) },
        text = { Text(message) },
        confirmButton = { Button(onClick = onConfirm) { Text("삭제") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun RecordDateHeader(date: LocalDate, injectionCount: Int, mealCount: Int, calorieSummary: com.sorimpower.app.feature.bodylog.data.DailyCalorieSummaryEntity?, onShowAll: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                if (date == LocalDate.now()) "오늘 · ${date.format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN))}"
                else date.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 EEEE", Locale.KOREAN)),
                fontWeight = FontWeight.Bold,
            )
            Text(
                listOfNotNull(
                    injectionCount.takeIf { it > 0 }?.let { "주사 ${it}회" },
                    mealCount.takeIf { it > 0 }?.let { "식사 ${it}개" },
                    calorieSummary?.let { "${it.estimatedCalories} kcal" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        onShowAll?.let { OutlinedButton(onClick = it) { Text("전체 기록 보기") } }
    }
}

@Composable
private fun WeightInputDialog(
    initial: WeightEntryEntity?,
    fallbackWeight: Double?,
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (Double, Double?, String?, String?, Long) -> Unit,
) {
    var weight by remember(initial?.id) { mutableStateOf((initial?.weightKg ?: fallbackWeight)?.let(::formatWeight).orEmpty()) }
    var bodyFat by remember(initial?.id) { mutableStateOf(initial?.bodyFatPercent?.let(::formatWeight).orEmpty()) }
    var note by remember(initial?.id) { mutableStateOf(initial?.note.orEmpty()) }
    var condition by remember(initial?.id) { mutableStateOf(initial?.condition) }
    var recordDate by remember(initial?.id, selectedDate) { mutableStateOf(initial?.localDate() ?: selectedDate) }
    val value = weight.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("체중 기록", fontWeight = FontWeight.Black) },
        text = {
            Column {
                RecordDateButton(recordDate, onDateChange = { recordDate = it })
                OutlinedTextField(weight, { weight = decimalInput(it) }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("체중 (kg)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                OutlinedTextField(bodyFat, { bodyFat = decimalInput(it) }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("체지방률 (선택)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                LazyRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("기상 직후", "식전", "식후", "운동 전", "운동 후")) { item -> FilterChip(selected = condition == item, onClick = { condition = item }, label = { Text(item) }) }
                }
                OutlinedTextField(note, { note = it.take(200) }, Modifier.fillMaxWidth(), label = { Text("메모") })
            }
        },
        confirmButton = {
            Button(
                enabled = value != null && value in 20.0..400.0,
                onClick = {
                    val time = timestampForDate(recordDate, initial?.measuredAt)
                    onSave(value!!, bodyFat.toDoubleOrNull(), condition, note, time)
                },
            ) { Text("저장") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun GoalInputDialog(start: Double?, currentTarget: Double?, currentTargetDate: LocalDate?, onDismiss: () -> Unit, onSave: (Double, Double, LocalDate?) -> Unit) {
    var startText by remember { mutableStateOf(start?.let(::formatWeight).orEmpty()) }
    var targetText by remember { mutableStateOf(currentTarget?.let(::formatWeight).orEmpty()) }
    var targetDateText by remember { mutableStateOf(currentTargetDate?.toString().orEmpty()) }
    val startValue = startText.toDoubleOrNull(); val targetValue = targetText.toDoubleOrNull()
    val targetDate = targetDateText.takeIf(String::isNotBlank)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("체중 목표", fontWeight = FontWeight.Black) },
        text = { Column {
            OutlinedTextField(startText, { startText = decimalInput(it) }, Modifier.fillMaxWidth(), label = { Text("시작 체중 (kg)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(targetText, { targetText = decimalInput(it) }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("목표 체중 (kg)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(targetDateText, { targetDateText = it.filter { char -> char.isDigit() || char == '-' }.take(10) }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("목표일 (선택, YYYY-MM-DD)") }, singleLine = true)
        } },
        confirmButton = { Button(enabled = startValue != null && startValue in 20.0..400.0 && targetValue != null && targetValue in 20.0..400.0 && (targetDateText.isBlank() || targetDate != null), onClick = { onSave(startValue!!, targetValue!!, targetDate) }) { Text("저장") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun MounjaroInputDialog(
    existing: MounjaroInjectionEntity?,
    onDismiss: () -> Unit,
    onSave: (Long, Double, Set<String>, String?, Boolean, Int) -> Unit,
) {
    var doseText by remember(existing?.id) { mutableStateOf(existing?.doseMg?.let(::formatWeight).orEmpty()) }
    var injectedDate by remember(existing?.id) { mutableStateOf(existing?.injectedAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() } ?: LocalDate.now()) }
    var injectedTime by remember(existing?.id) { mutableStateOf(timeForTimestamp(existing?.injectedAt)) }
    var sideEffects by remember(existing?.id) { mutableStateOf(existing?.sideEffects?.split('|')?.filter(String::isNotBlank)?.toSet().orEmpty()) }
    var note by remember(existing?.id) { mutableStateOf(existing?.note.orEmpty()) }
    var reminderEnabled by remember(existing?.id) { mutableStateOf(existing?.reminderEnabled ?: true) }
    var reminderIntervalWeeks by remember(existing?.id) { mutableStateOf(existing?.reminderIntervalWeeks ?: 1) }
    val dose = doseText.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "마운자로 주사 기록" else "마운자로 주사 수정", fontWeight = FontWeight.Black) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                RecordDateButton(injectedDate, onDateChange = { injectedDate = it })
                RecordTimeButton(injectedTime, onTimeChange = { injectedTime = it }, modifier = Modifier.padding(top = 8.dp))
                OutlinedTextField(
                    doseText,
                    { doseText = decimalInput(it) },
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    label = { Text("용량 (mg)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                Text("부작용 (해당할 때 선택)", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelLarge)
                LazyRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("메스꺼움", "설사", "변비", "구토", "복통", "식욕 감소")) { effect ->
                        FilterChip(
                            selected = effect in sideEffects,
                            onClick = { sideEffects = if (effect in sideEffects) sideEffects - effect else sideEffects + effect },
                            label = { Text(effect) },
                        )
                    }
                }
                OutlinedTextField(
                    note,
                    { note = it.take(300) },
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    label = { Text("부작용 상세 또는 메모 (선택)") },
                    minLines = 2,
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("다음 기록 알림", fontWeight = FontWeight.Bold)
                        Text("선택한 주기마다 반복", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = reminderEnabled, onCheckedChange = { reminderEnabled = it })
                }
                if (reminderEnabled) {
                    Text("알림 반복 주기", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelLarge)
                    LazyRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items((1..4).toList()) { weeks ->
                            FilterChip(
                                selected = reminderIntervalWeeks == weeks,
                                onClick = { reminderIntervalWeeks = weeks },
                                label = { Text("${weeks}주마다") },
                            )
                        }
                    }
                }
                Text("심하거나 지속되는 증상, 걱정되는 증상은 의료진에게 상담해 주세요.", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(
                enabled = dose != null && dose in 0.1..20.0,
                onClick = { onSave(timestampForDate(injectedDate, injectedTime), dose!!, sideEffects, note, reminderEnabled, reminderIntervalWeeks) },
            ) { Text("저장") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun MounjaroReminderSettingsDialog(
    injection: MounjaroInjectionEntity,
    onDismiss: () -> Unit,
    onSave: (Boolean, Int) -> Unit,
) {
    var enabled by remember(injection.id) { mutableStateOf(injection.reminderEnabled) }
    var intervalWeeks by remember(injection.id) { mutableStateOf(injection.reminderIntervalWeeks) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("마운자로 알림 설정", fontWeight = FontWeight.Black) },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("반복 알림", fontWeight = FontWeight.Bold)
                        Text("투여 지시가 아닌 개인 기록 알림입니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                if (enabled) {
                    Text("알림 반복 주기", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.labelLarge)
                    LazyRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items((1..4).toList()) { weeks ->
                            FilterChip(
                                selected = intervalWeeks == weeks,
                                onClick = { intervalWeeks = weeks },
                                label = { Text("${weeks}주마다") },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(enabled, intervalWeeks) }) { Text("저장") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun MealInputDialog(viewModel: BodyLogViewModel, existing: MealWithDetails?, selectedDate: LocalDate, onDismiss: () -> Unit, onSaved: () -> Unit) {
    var type by remember(existing?.meal?.id) { mutableStateOf(existing?.meal?.mealType?.let(MealType::from) ?: MealType.LUNCH) }
    var foods by remember(existing?.meal?.id) { mutableStateOf(existing?.items?.sortedBy { it.sortOrder }?.joinToString("\n") { it.name }.orEmpty()) }
    var note by remember(existing?.meal?.id) { mutableStateOf(existing?.meal?.note.orEmpty()) }
    var tags by remember(existing?.meal?.id) { mutableStateOf(existing?.meal?.tags?.split('|')?.filter(String::isNotBlank)?.toSet().orEmpty()) }
    var photos by remember { mutableStateOf(emptyList<Uri>()) }
    var retainedPhotos by remember(existing?.meal?.id) { mutableStateOf(existing?.photos.orEmpty().sortedBy { it.sortOrder }) }
    var cameraFiles by remember { mutableStateOf(emptyList<File>()) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var recordDate by remember(existing?.meal?.id, selectedDate) { mutableStateOf(existing?.meal?.localDate() ?: selectedDate) }
    var recordTime by remember(existing?.meal?.id) { mutableStateOf(timeForTimestamp(existing?.meal?.eatenAt)) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(3)) { selected -> photos = (photos + selected).distinct().take((3 - retainedPhotos.size).coerceAtLeast(0)) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success -> if (success) pendingCameraUri?.let { photos = (photos + it).take((3 - retainedPhotos.size).coerceAtLeast(0)) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("식사 기록", fontWeight = FontWeight.Black) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { RecordDateButton(recordDate, onDateChange = { recordDate = it }) }
                item { RecordTimeButton(recordTime, onTimeChange = { recordTime = it }) }
                item { LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(MealType.entries) { item -> FilterChip(selected = type == item, onClick = { type = item }, label = { Text(item.label) }) } } }
                item { OutlinedTextField(foods, { foods = it.take(500) }, Modifier.fillMaxWidth(), label = { Text("먹은 음식 (한 줄에 하나)") }, minLines = 3) }
                item { OutlinedTextField(note, { note = it.take(200) }, Modifier.fillMaxWidth(), label = { Text("메모 (선택)") }) }
                item { LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(listOf("배고픔", "적당함", "과식", "외식", "야식")) { tag -> FilterChip(selected = tag in tags, onClick = { tags = if (tag in tags) tags - tag else tags + tag }, label = { Text(tag) }) } } }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = photos.size + retainedPhotos.size < 3) { Text("사진 선택") }
                        OutlinedButton(onClick = {
                            val (uri, file) = viewModel.createCameraUri()
                            pendingCameraUri = uri
                            cameraFiles = cameraFiles + file
                            camera.launch(uri)
                        }, enabled = photos.size + retainedPhotos.size < 3) { Text("촬영") }
                    }
                }
                if (retainedPhotos.isNotEmpty()) item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(retainedPhotos, key = { it.id }) { photo ->
                            Box {
                                AsyncImage(File(photo.thumbnailPath), "저장된 식사 사진", Modifier.size(82.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)))
                                Text("×", Modifier.align(Alignment.TopEnd).background(Color.Black.copy(alpha = .65f), CircleShape).clickable { retainedPhotos = retainedPhotos - photo }.padding(horizontal = 6.dp), color = Color.White)
                            }
                        }
                    }
                }
                if (photos.isNotEmpty()) item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(photos, key = Uri::toString) { uri ->
                            Box {
                                AsyncImage(uri, "선택한 식사 사진", Modifier.size(82.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)))
                                Text("×", Modifier.align(Alignment.TopEnd).background(Color.Black.copy(alpha = .65f), CircleShape).clickable { photos = photos - uri }.padding(horizontal = 6.dp), color = Color.White)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = foods.lineSequence().any { it.isNotBlank() }, onClick = {
                val items = foods.lineSequence().flatMap { it.split(',').asSequence() }.filter { it.isNotBlank() }.map { MealItemInput(it.trim()) }.toList()
                val eatenAt = timestampForDate(recordDate, recordTime)
                viewModel.saveMeal(existing, eatenAt, type.name, items, note, tags, photos, retainedPhotos.map { it.id }.toSet()) {
                    cameraFiles.forEach(File::delete)
                    onSaved()
                }
            }) { Text("저장") }
        },
        dismissButton = { OutlinedButton(onClick = { cameraFiles.forEach(File::delete); onDismiss() }) { Text("취소") } },
    )
}

@Composable
private fun RecordDateButton(date: LocalDate, onDateChange: (LocalDate) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            DatePickerDialog(
                context,
                { _, year, month, day -> onDateChange(LocalDate.of(year, month + 1, day)) },
                date.year,
                date.monthValue - 1,
                date.dayOfMonth,
            ).apply { datePicker.maxDate = System.currentTimeMillis() }.show()
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(19.dp))
        Text(date.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 EEEE", Locale.KOREAN)), Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun RecordTimeButton(time: LocalTime, onTimeChange: (LocalTime) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            TimePickerDialog(
                context,
                { _, hour, minute -> onTimeChange(LocalTime.of(hour, minute)) },
                time.hour,
                time.minute,
                true,
            ).show()
        },
        modifier = modifier.fillMaxWidth(),
    ) { Text("시간 · %02d:%02d".format(time.hour, time.minute)) }
}

@Composable private fun SectionHeading(title: String, subtitle: String, modifier: Modifier = Modifier) = Column(modifier) {
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun EmptyBodyCard(message: String) = Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
    Text(message, Modifier.fillMaxWidth().padding(18.dp))
}

private fun chartPoints(weights: List<WeightEntryEntity>, period: ChartPeriod, anchor: LocalDate): List<ChartPoint> {
    val zone = ZoneId.systemDefault()
    return when (period) {
        ChartPeriod.DAY -> weights.filter { it.localDate() == anchor }.sortedBy { it.measuredAt }.map {
            ChartPoint(Instant.ofEpochMilli(it.measuredAt).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm")), it.weightKg, it.measuredAt)
        }
        ChartPeriod.WEEK -> {
            val start = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); val end = start.plusDays(6)
            weights.filter { entry ->
                val date = entry.localDate()
                !date.isBefore(start) && !date.isAfter(end)
            }.sortedBy(WeightEntryEntity::measuredAt).map { entry ->
                ChartPoint(entry.localDate().format(DateTimeFormatter.ofPattern("M/d")), entry.weightKg, entry.measuredAt)
            }
        }
        ChartPeriod.MONTH -> weights.dailyRepresentatives().filterKeys { it.year == anchor.year && it.month == anchor.month }.toSortedMap().map { (date, entry) -> ChartPoint("${date.dayOfMonth}일", entry.weightKg, entry.measuredAt) }
        ChartPeriod.YEAR -> weights.filter { it.localDate().year == anchor.year }.sortedBy(WeightEntryEntity::measuredAt).map { entry ->
            ChartPoint(entry.localDate().format(DateTimeFormatter.ofPattern("M/d")), entry.weightKg, entry.measuredAt)
        }
    }
}

private fun formatWeight(value: Double) = "%.1f".format(value)
private fun formatRecordTime(timestamp: Long) = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
private const val HIDDEN_WEIGHT = "••• kg"
private fun timeForTimestamp(timestamp: Long?): LocalTime = timestamp
    ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime().withSecond(0).withNano(0) }
    ?: LocalTime.now().withSecond(0).withNano(0)
private fun timestampForDate(date: LocalDate, existingTimestamp: Long?): Long {
    val zone = ZoneId.systemDefault()
    val time = existingTimestamp?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() }
        ?: if (date == LocalDate.now()) java.time.LocalTime.now() else java.time.LocalTime.NOON
    return date.atTime(time).atZone(zone).toInstant().toEpochMilli()
}
private fun timestampForDate(date: LocalDate, time: LocalTime): Long = date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
private fun signed(value: Double?) = value?.let { (if (it > 0) "+" else "") + formatWeight(it) } ?: "—"
private fun decimalInput(value: String): String = value.filter { it.isDigit() || it == '.' }.let { filtered ->
    val firstDot = filtered.indexOf('.')
    if (firstDot < 0) filtered.take(3) else filtered.substring(0, firstDot).take(3) + "." + filtered.substring(firstDot + 1).filter(Char::isDigit).take(1)
}
