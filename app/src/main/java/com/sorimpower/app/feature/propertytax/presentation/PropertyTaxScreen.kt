package com.sorimpower.app.feature.propertytax.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sorimpower.app.core.ui.AppCobalt
import com.sorimpower.app.core.ui.AppGreen
import com.sorimpower.app.core.ui.AppOrange
import com.sorimpower.app.feature.propertytax.data.*
import com.sorimpower.app.feature.propertytax.domain.*
import org.json.JSONArray
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private enum class PropertyTaxTab(val label: String) { PORTFOLIO("포트폴리오"), SIMULATIONS("매도 계산"), IMPACT("거래 영향"), RULES("세법 기준") }

@Composable
fun PropertyTaxAnalysisInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("세금은 이렇게 분석해요", fontWeight = FontWeight.Black) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { AnalysisGuideStep("1", "Rule Engine 계산", "입력한 주택·분양권·지분·취득가·매도가를 기준으로 취득세, 보유세, 양도세 예상값을 먼저 계산해요.") }
                item { AnalysisGuideStep("2", "공식 법령 실시간 확인", "분석 버튼을 누를 때마다 국가법령정보센터·국세청 등 공식 자료에서 시행일, 부칙, 경과규정과 유예기간을 다시 확인해요.") }
                item { AnalysisGuideStep("3", "현재 계산 기준과 비교", "GPT-5.6 Sol(max)이 최신 공식 법령과 앱의 Rule Engine을 비교해 일치, 법령 변경 감지, 검증 불완전으로 구분해요.") }
                item { AnalysisGuideStep("4", "직전 분석 재검증", "같은 매도 시뮬레이션의 이전 성공 분석과 비교해 잘못된 판단, 새로 생긴 차이, 그대로 유효한 판단을 따로 보여줘요.") }
                item { AnalysisGuideStep("5", "근거와 안전 상태 표시", "확인에 사용한 공식 링크를 제공하고, 변경이나 출처 부족이 있으면 계산값을 안전하다고 표시하지 않아요.") }
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = AppOrange.copy(alpha = .09f))) {
                        Text(
                            "AI가 세율이나 계산식을 자동으로 바꾸지는 않아요. 법령 변경이 발견되면 Rule Engine 검토와 업데이트가 필요하며, 실제 신고·거래 전에는 세무 전문가 확인이 필요해요.",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("확인") } },
    )
}

@Composable
private fun AnalysisGuideStep(number: String, title: String, description: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.size(28.dp).background(AppCobalt.copy(alpha = .12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text(number, color = AppCobalt, fontWeight = FontWeight.Black) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.Black)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PropertyTaxScreen(padding: PaddingValues, viewModel: PropertyTaxViewModel, onSwipeEdgeLeft: () -> Unit = {}, onSwipeEdgeRight: () -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(PropertyTaxTab.PORTFOLIO) }
    var propertyDialog by remember { mutableStateOf(false) }
    var editingProperty by remember { mutableStateOf<PropertyEntity?>(null) }
    var simulationDialog by remember { mutableStateOf(false) }
    var scenarioDialog by remember { mutableStateOf(false) }
    var acquisitionScenarioId by remember { mutableStateOf<String?>(null) }
    var saleScenarioId by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(padding)) {
        PrimaryTabRow(selectedTabIndex = PropertyTaxTab.entries.indexOf(tab)) {
            PropertyTaxTab.entries.forEach { value -> Tab(tab == value, { tab = value }, text = { Text(value.label, style = MaterialTheme.typography.labelMedium) }) }
        }
        Box(Modifier.fillMaxSize().weight(1f).horizontalSwipe(
            onSwipeLeft = { val index = PropertyTaxTab.entries.indexOf(tab); if (index == PropertyTaxTab.entries.lastIndex) onSwipeEdgeLeft() else tab = PropertyTaxTab.entries[index + 1] },
            onSwipeRight = { val index = PropertyTaxTab.entries.indexOf(tab); if (index == 0) onSwipeEdgeRight() else tab = PropertyTaxTab.entries[index - 1] },
        )) {
            when (tab) {
                PropertyTaxTab.PORTFOLIO -> PortfolioContent(state, { propertyDialog = true }, { editingProperty = it }, viewModel::deleteProperty, viewModel::setTaxYear)
                PropertyTaxTab.SIMULATIONS -> SimulationContent(state, { simulationDialog = true }, viewModel::recalculate, viewModel::analyze, viewModel::deleteSimulation)
                PropertyTaxTab.IMPACT -> ScenarioContent(state, { scenarioDialog = true }, { acquisitionScenarioId = it }, { saleScenarioId = it }, viewModel::deleteScenario)
                PropertyTaxTab.RULES -> RuleContent(state)
            }
            if (state.working) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = .55f)), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AppCobalt) }
        }
    }
    if (propertyDialog) PropertyDialog(onDismiss = { propertyDialog = false }) { viewModel.saveProperty(it); propertyDialog = false }
    editingProperty?.let { entity -> PropertyDialog(title = "부동산 정보 수정", initial = entity.screenDraft(), onDismiss = { editingProperty = null }) { viewModel.saveProperty(it); editingProperty = null } }
    acquisitionScenarioId?.let { scenarioId -> PropertyDialog(title = "가상 취득 단계 추가", onDismiss = { acquisitionScenarioId = null }) { viewModel.addScenarioAcquisition(scenarioId, it); acquisitionScenarioId = null } }
    if (simulationDialog) SimulationDialog(state.properties.filter { it.status == PropertyStatus.OWNED.name && it.propertyType in setOf(PropertyType.APARTMENT.name, PropertyType.HOUSE.name) }, onDismiss = { simulationDialog = false }) { viewModel.createSimulation(it); simulationDialog = false }
    if (scenarioDialog) ScenarioNameDialog({ scenarioDialog = false }) { viewModel.createScenario(it); scenarioDialog = false }
    saleScenarioId?.let { scenarioId -> ScenarioSaleDialog(state.properties.filter { it.status == PropertyStatus.OWNED.name }, { saleScenarioId = null }) { propertyId, date, price -> viewModel.addScenarioSale(scenarioId, propertyId, date, price); saleScenarioId = null } }
    state.aiAnalysis?.let { AiAnalysisDialog(it, viewModel::dismissAnalysis) }
    state.message?.let { AlertDialog(onDismissRequest = viewModel::clearMessage, title = { Text("부동산 세금") }, text = { Text(it) }, confirmButton = { TextButton(viewModel::clearMessage) { Text("확인") } }) }
}

@Composable
private fun ScenarioContent(
    state: PropertyTaxUiState,
    onAdd: () -> Unit,
    onAcquire: (String) -> Unit,
    onSell: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("거래 전후 영향", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                    Text("취득·매도를 순서대로 쌓아 포트폴리오 변화를 비교합니다.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                Button(onAdd) { Icon(Icons.Rounded.Add, null); Text(" 비교") }
            }
        }
        if (state.scenarios.isEmpty()) item { EmptyCard("저장된 거래 시나리오가 없습니다.", "가상 취득과 매도를 여러 단계로 추가해 현재 포트폴리오와 비교하세요.") }
        items(state.scenarios, key = { it.id }) { scenario ->
            val impact = state.scenarioImpacts[scenario.id]
            val transactions = state.scenarioTransactions.filter { it.scenarioId == scenario.id }.sortedBy { it.sequence }
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(scenario.name, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium); Text("${transactions.size}단계 · ${scenario.taxRuleVersionId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        IconButton({ onDelete(scenario.id) }) { Icon(Icons.Rounded.DeleteOutline, "삭제") }
                    }
                    if (transactions.isEmpty()) Text("아래에서 첫 거래를 추가하세요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    transactions.forEach { transaction ->
                        Surface(color = Color(0xFFF6F7FB), shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("${transaction.sequence}", color = AppCobalt, fontWeight = FontWeight.Black)
                                Text("  ${runCatching { ScenarioTransactionType.valueOf(transaction.type).label }.getOrDefault(transaction.type)} · ${transaction.transactionDate}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                Text(won(transaction.transactionPrice), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    impact?.let {
                        HorizontalDivider()
                        TaxRow("보유 부동산", "${it.beforePortfolio.size}개 → ${it.afterPortfolio.size}개")
                        TaxRow("연간 보유세", "${won(it.beforeHoldingTax)} → ${won(it.afterHoldingTax)}")
                        TaxRow("거래 단계 세금 합계", won(it.transactionTax))
                        TaxRow("거래+연간 보유세 증감", signedWon(it.totalTaxChange), true)
                        if (it.missingInputs.isNotEmpty()) Text("확인 필요 ${it.missingInputs.distinct().size}건", color = AppOrange, style = MaterialTheme.typography.labelSmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ onAcquire(scenario.id) }, Modifier.weight(1f)) { Icon(Icons.Rounded.AddHome, null); Text(" 가상 취득") }
                        Button({ onSell(scenario.id) }, Modifier.weight(1f), enabled = state.properties.isNotEmpty()) { Icon(Icons.Rounded.Sell, null); Text(" 가상 매도") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PortfolioContent(state: PropertyTaxUiState, onAdd: () -> Unit, onEdit: (PropertyEntity) -> Unit, onDelete: (String) -> Unit, onTaxYear: (Int) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TaxSummaryCard(state.holding, state.taxYear, onTaxYear)
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("내 부동산", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black); Text("실제 보유 자산을 기준으로 계산합니다.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                Button(onAdd) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("추가") }
            }
        }
        if (state.properties.isEmpty()) item { EmptyCard("등록된 부동산이 없습니다.", "취득가·공시가격을 입력하면 예상 취득세와 보유세를 계산합니다.") }
        items(state.properties, key = { it.id }) { property ->
            val acquisition = state.acquisitionTaxes[property.id]
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Apartment, null, tint = AppCobalt)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) { Text(property.name, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium); Text("${property.propertyType.propertyTypeLabel()} · ${property.acquisitionDate} 취득", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        IconButton({ onEdit(property) }) { Icon(Icons.Rounded.Edit, "수정") }
                        IconButton({ onDelete(property.id) }) { Icon(Icons.Rounded.DeleteOutline, "삭제") }
                    }
                    TaxRow("취득가", won(property.acquisitionPrice))
                    if (property.spouseOwnershipRatio > 0) TaxRow("부부 공동명의", "본인 ${(property.ownershipRatio * 100).toInt()}% · 배우자 ${(property.spouseOwnershipRatio * 100).toInt()}%")
                    if (property.propertyType == PropertyType.PRESALE_RIGHT.name) TaxRow("입주 예정", property.expectedCompletionDate ?: "미입력") else TaxRow("공시가격", property.officialAssessedValue?.let(::won) ?: "미입력")
                    TaxRow("예상 취득 단계 세금", when { acquisition == null -> "계산 대기"; !acquisition.calculationAvailable -> "지원 세법 없음"; else -> won(acquisition.result.totalTax) })
                    acquisition?.missingInputs?.firstOrNull()?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = AppOrange) }
                }
            }
        }
    }
}

@Composable
private fun TaxSummaryCard(calculation: TaxCalculation<HoldingTaxResult>?, year: Int, onYear: (Int) -> Unit) {
    val result = calculation?.result
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F3FF)), border = BorderStroke(1.dp, AppCobalt.copy(alpha = .18f))) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$year 예상 보유세", Modifier.weight(1f), color = AppCobalt, fontWeight = FontWeight.Bold)
                IconButton({ onYear(year - 1) }, Modifier.size(32.dp)) { Icon(Icons.Rounded.ChevronLeft, "이전 연도") }
                IconButton({ onYear(year + 1) }, Modifier.size(32.dp)) { Icon(Icons.Rounded.ChevronRight, "다음 연도") }
            }
            Text(when { calculation == null -> "계산 중"; !calculation.calculationAvailable -> "해당 연도 계산 불가"; else -> result?.totalTax?.let(::won) ?: "계산 중" }, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineMedium)
            HorizontalDivider(color = AppCobalt.copy(alpha = .15f))
            TaxRow("재산세", result?.propertyTax?.let(::won) ?: "-")
            TaxRow("종합부동산세", result?.comprehensiveRealEstateTax?.let(::won) ?: "-")
            result?.jointSpecialComprehensiveTax?.let {
                TaxRow("공동명의 개별 과세", won(result.separateComprehensiveTax))
                TaxRow("공동명의 1주택 특례", won(it))
                TaxRow("특례 고령·장기보유 공제", "${result.jointSpecialCreditPercent}%")
                Text("선택 계산 · ${result.selectedJointTaxMethod.label}", color = AppGreen, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            }
            TaxRow("도시지역분", result?.urbanAreaTax?.let(::won) ?: "-")
            TaxRow("지역자원시설세", result?.regionalResourceTax?.let(::won) ?: "-")
            TaxRow("지방교육세·종부세 농특세 등", result?.additionalTax?.let(::won) ?: "-")
            calculation?.missingInputs?.firstOrNull()?.let { Text("확인 필요 · $it", color = AppOrange, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun SimulationContent(state: PropertyTaxUiState, onAdd: () -> Unit, onRecalculate: (String) -> Unit, onAnalyze: (String) -> Unit, onDelete: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("양도 시뮬레이션", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black); Text("저장된 결과는 덮어쓰지 않고 Revision으로 보존합니다.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                Button(onAdd, enabled = state.properties.isNotEmpty()) { Icon(Icons.Rounded.Add, null); Text(" 새 계산") }
            }
        }
        if (state.simulations.isEmpty()) item { EmptyCard("저장된 시뮬레이션이 없습니다.", "매도일과 매도가를 입력해 예상 양도세를 저장하세요.") }
        items(state.simulations, key = { it.id }) { simulation ->
            val propertyName = state.properties.firstOrNull { it.id == simulation.propertyId }?.name ?: "삭제된 부동산"
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row { Column(Modifier.weight(1f)) { Text(simulation.name, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium); Text("$propertyName · ${simulation.expectedSaleDate} 매도 가정", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }; IconButton({ onDelete(simulation.id) }) { Icon(Icons.Rounded.DeleteOutline, "삭제") } }
                    TaxRow("예상 매도가", won(simulation.expectedSalePrice))
                    TaxRow("양도차익", won(simulation.capitalGain))
                    TaxRow("예상 양도세", won(simulation.totalEstimatedTax), true)
                    val countedHomes = traceAmount(simulation.calculationTraceJson, "세대 주택·분양권 수")
                    if (countedHomes != null) TaxRow("양도세상 주택·분양권", "${countedHomes}개")
                    when {
                        ruleApplied(simulation.appliedRulesJson, "ONE_HOME_PRESALE_SPECIAL") && ruleApplied(simulation.appliedRulesJson, "ONE_HOME_EXEMPTION") -> Text("1주택 + 1분양권 특례로 비과세 판정", color = AppGreen, fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodySmall)
                        ruleApplied(simulation.appliedRulesJson, "ONE_HOME_EXEMPTION") -> Text("1세대 1주택 비과세·고가주택 안분 적용", color = AppGreen, fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodySmall)
                        ruleApplied(simulation.appliedRulesJson, "MULTI_HOME_SURCHARGE") -> Text("조정대상지역 다주택 중과 적용", color = AppOrange, fontWeight = FontWeight.Black, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("적용 세법 · ${simulation.taxRuleVersionId} · 정확도 ${CalculationConfidence.valueOf(simulation.confidence).label}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TraceBlock(simulation.calculationTraceJson)
                    val missing = parseStrings(simulation.missingInputsJson)
                    if (missing.isNotEmpty()) Surface(color = AppOrange.copy(alpha = .1f), shape = RoundedCornerShape(12.dp)) { Column(Modifier.padding(12.dp)) { Text("추가 확인 필요", color = AppOrange, fontWeight = FontWeight.Bold); missing.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) } } }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ onRecalculate(simulation.id) }, Modifier.weight(1f)) { Text("최신 기준 재계산") }
                        Button({ onAnalyze(simulation.id) }, Modifier.weight(1f)) { Icon(Icons.Rounded.AutoAwesome, null); Text(" 최신 법령 + GPT") }
                    }
                    val revisionCount = state.revisions.count { it.simulationId == simulation.id }
                    Text("저장된 계산 이력 ${revisionCount}개", style = MaterialTheme.typography.labelSmall, color = AppCobalt)
                }
            }
        }
    }
}

@Composable
private fun TraceBlock(json: String) {
    var expanded by remember(json) { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth().clickable { expanded = !expanded }, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row { Text("계산 근거", Modifier.weight(1f), fontWeight = FontWeight.Bold); Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null) }
            if (expanded) runCatching { JSONArray(json) }.getOrNull()?.let { array -> (0 until array.length()).forEach { index -> val item = array.getJSONObject(index); TaxRow(item.optString("label"), won(item.optLong("amount"))) } }
        }
    }
}

@Composable
private fun RuleContent(state: PropertyTaxUiState) {
    val rule = state.activeRule
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { EmptyCard(rule?.name ?: "활성 세법을 준비 중입니다.", "시행일 ${rule?.effectiveFrom ?: "-"} · 근거 확인일 ${rule?.sourceUpdatedAt ?: "-"}") }
        item { WarningCard() }
        item { SourceCard("취득세", "지방세법 제11조·시행령 제28조의4", "전체 주택가액 세율 구간, 지분 과세표준, 세대 주택 수") }
        item { SourceCard("재산세", "지방세법 시행령 제109조·지방세법 제111조·제112조·제146조", "공정시장가액비율, 재산세, 지정 도시지역분, 지역자원시설세 분리") }
        item { SourceCard("종합부동산세", "종합부동산세법 제9조·시행령 제4조의3", "납세의무자별 주택 수와 공제할 재산세액 법정 산식") }
        item { SourceCard("양도소득세", "소득세법 제95조·제104조", "양도차익·장기보유특별공제·누진세율") }
        item { SourceCard("분양권·공동명의", "소득세법 시행령 제156조의3·종합부동산세법 제10조의2", "세목별 주택 수 판정, 1주택+1분양권 특례, 공동명의 1주택 특례") }
    }
}

@Composable private fun WarningCard() = Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF6E8)), shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(16.dp)) { Text("계산 범위", fontWeight = FontWeight.Black, color = AppOrange); Text("검증된 Rule 기간과 입력된 증빙 범위에서만 계산합니다. 법정 제외주택·감면·세부담상한·지자체 탄력세율처럼 자동 확정할 수 없는 항목은 합계에서 제외하거나 확인 필요로 표시하며 신고 자료로 사용할 수 없습니다.", style = MaterialTheme.typography.bodySmall) } }
@Composable private fun SourceCard(title: String, source: String, detail: String) = Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, fontWeight = FontWeight.Black); Text(source, color = AppCobalt, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall); Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) } }

@Composable
private fun ScenarioNameDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("새 거래 시나리오") },
        text = { OutlinedTextField(name, { name = it }, label = { Text("시나리오 이름") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { Button({ onSave(name) }) { Text("만들기") } },
        dismissButton = { TextButton(onDismiss) { Text("취소") } },
    )
}

@Composable
private fun ScenarioSaleDialog(properties: List<PropertyEntity>, onDismiss: () -> Unit, onSave: (String, LocalDate, Long) -> Unit) {
    var selected by remember { mutableStateOf(properties.firstOrNull()?.id) }
    var date by remember { mutableStateOf(LocalDate.now().plusYears(1).toString()) }
    var price by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("가상 매도 단계 추가") }, text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 480.dp)) {
        items(properties, key = { it.id }) { property -> FilterChip(selected == property.id, { selected = property.id }, { Text(property.name) }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(date, { date = it }, label = { Text("예상 매도일 YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { NumberField("예상 매도가 (원)", price) { price = it } }
    } }, confirmButton = { Button({ val id = selected ?: return@Button; val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull() ?: return@Button; val parsedPrice = price.toLongOrNull() ?: return@Button; onSave(id, parsedDate, parsedPrice) }, enabled = selected != null && price.toLongOrNull() != null) { Text("단계 추가") } }, dismissButton = { TextButton(onDismiss) { Text("취소") } })
}

@Composable
private fun PropertyDialog(title: String = "내 부동산 추가", initial: PropertyDraft? = null, onDismiss: () -> Unit, onSave: (PropertyDraft) -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var address by remember { mutableStateOf(initial?.address.orEmpty()) }
    var date by remember { mutableStateOf(initial?.acquisitionDate?.toString() ?: LocalDate.now().toString()) }
    var contractDate by remember { mutableStateOf(initial?.acquisitionContractDate?.toString().orEmpty()) }
    var price by remember { mutableStateOf(initial?.acquisitionPrice?.toString().orEmpty()) }
    var assessed by remember { mutableStateOf(initial?.officialAssessedValue?.toString().orEmpty()) }
    var ratio by remember { mutableStateOf(((initial?.ownershipRatio ?: 1.0) * 100).toInt().toString()) }
    var spouseRatio by remember { mutableStateOf(((initial?.spouseOwnershipRatio ?: 0.0) * 100).toInt().toString()) }
    var ownerBirthDate by remember { mutableStateOf(initial?.ownerBirthDate?.toString().orEmpty()) }
    var spouseBirthDate by remember { mutableStateOf(initial?.spouseBirthDate?.toString().orEmpty()) }
    var type by remember { mutableStateOf(initial?.propertyType ?: PropertyType.APARTMENT) }
    var regulated by remember { mutableStateOf(initial?.regulatedAreaAtAcquisition) }
    var completion by remember { mutableStateOf(initial?.expectedCompletionDate?.toString().orEmpty()) }
    var residenceStart by remember { mutableStateOf(initial?.residenceStartDate?.toString().orEmpty()) }
    var residenceEnd by remember { mutableStateOf(initial?.residenceEndDate?.toString().orEmpty()) }
    var urban by remember { mutableStateOf(initial?.urbanAreaTaxApplicable) }
    var regionalTax by remember { mutableStateOf(initial?.annualRegionalResourceTax?.toString().orEmpty()) }
    var ruralTax by remember { mutableStateOf(initial?.acquisitionRuralSpecialTax?.toString().orEmpty()) }
    var acquisitionCount by remember { mutableStateOf(initial?.acquisitionHouseCountTreatment ?: TaxTreatment.AUTO) }
    var capitalCount by remember { mutableStateOf(initial?.capitalGainsHouseCountTreatment ?: TaxTreatment.AUTO) }
    var comprehensive by remember { mutableStateOf(initial?.comprehensiveTaxTreatment ?: TaxTreatment.AUTO) }
    var surchargeTreatment by remember { mutableStateOf(initial?.capitalGainsSurchargeTreatment ?: TaxTreatment.AUTO) }
    var acquisitionRelief by remember { mutableStateOf(initial?.acquisitionSurchargeRelief ?: AcquisitionSurchargeRelief.NONE) }
    var priorDisposal by remember { mutableStateOf(initial?.previousHomeDispositionDate?.toString().orEmpty()) }
    var residenceExempt by remember { mutableStateOf(initial?.residenceRequirementExempt == true) }
    var jointSpecial by remember { mutableStateOf(initial?.jointComprehensiveTaxSpecialRequested == true) }
    var specialTaxpayer by remember { mutableStateOf(initial?.jointSpecialTaxpayer) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.heightIn(max = 560.dp)) {
            item { OutlinedTextField(name, { name = it }, label = { Text("이름") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(address, { address = it }, label = { Text("주소") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { PropertyType.entries.chunked(4).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { row.forEach { FilterChip(type == it, { type = it }, { Text(it.label) }) } } } }
            item { OutlinedTextField(date, { date = it }, label = { Text("취득일 YYYY-MM-DD") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(contractDate, { contractDate = it }, label = { Text("취득 계약일 YYYY-MM-DD (경과규정용)") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { NumberField("전체 주택 취득가 (원)", price) { price = it } }
            if (type !in setOf(PropertyType.PRESALE_RIGHT, PropertyType.ASSOCIATION_RIGHT)) item { NumberField("공시가격 (원)", assessed) { assessed = it } }
            if (type in setOf(PropertyType.PRESALE_RIGHT, PropertyType.ASSOCIATION_RIGHT)) item { OutlinedTextField(completion, { completion = it }, label = { Text("완공 예정일 YYYY-MM-DD") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { NumberField("본인 소유 지분 (%)", ratio) { ratio = it } }
            item { NumberField("배우자 소유 지분 (%)", spouseRatio) { spouseRatio = it } }
            if ((spouseRatio.toDoubleOrNull() ?: 0.0) > 0) item { OutlinedTextField(ownerBirthDate, { ownerBirthDate = it }, label = { Text("본인 생년월일 YYYY-MM-DD") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            if ((spouseRatio.toDoubleOrNull() ?: 0.0) > 0) item { OutlinedTextField(spouseBirthDate, { spouseBirthDate = it }, label = { Text("배우자 생년월일 YYYY-MM-DD") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { BooleanSelector("취득 당시 조정대상지역", regulated) { regulated = it } }
            item { BooleanSelector("재산세 도시지역분 대상", urban) { urban = it } }
            item { NumberField("연간 지역자원시설세 (고지서 금액)", regionalTax) { regionalTax = it } }
            item { NumberField("취득 농어촌특별세 (확인된 금액)", ruralTax) { ruralTax = it } }
            item { TreatmentSelector("취득세 주택 수", acquisitionCount) { acquisitionCount = it } }
            item { TreatmentSelector("양도세 주택 수", capitalCount) { capitalCount = it } }
            item { TreatmentSelector("종부세 합산", comprehensive) { comprehensive = it } }
            item { TreatmentSelector("다주택 중과 대상 주택", surchargeTreatment) { surchargeTreatment = it } }
            item { Text("취득세 중과 특례", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { AcquisitionSurchargeRelief.entries.forEach { FilterChip(acquisitionRelief == it, { acquisitionRelief = it }, { Text(it.label) }) } } }
            if (acquisitionRelief == AcquisitionSurchargeRelief.TEMPORARY_TWO_HOME) item { OutlinedTextField(priorDisposal, { priorDisposal = it }, label = { Text("종전주택 처분일 YYYY-MM-DD") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            if (type !in setOf(PropertyType.PRESALE_RIGHT, PropertyType.ASSOCIATION_RIGHT)) {
                item { OutlinedTextField(residenceStart, { residenceStart = it }, label = { Text("실거주 시작일 YYYY-MM-DD") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(residenceEnd, { residenceEnd = it }, label = { Text("실거주 종료일 YYYY-MM-DD") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                item { LabeledCheckbox("계약일 등 법정 사유로 2년 거주요건 면제 확인", residenceExempt) { residenceExempt = it } }
            }
            if ((spouseRatio.toDoubleOrNull() ?: 0.0) > 0) {
                item { LabeledCheckbox("공동명의 1주택 특례 신청/신청 예정", jointSpecial) { jointSpecial = it } }
                if (jointSpecial && ratio.toDoubleOrNull() == spouseRatio.toDoubleOrNull()) item { Text("특례 납세의무자", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { OwnerRole.entries.forEach { FilterChip(specialTaxpayer == it, { specialTaxpayer = it }, { Text(it.label) }) } } }
            }
        } },
        confirmButton = { Button({
            val parsedDate = parseDate(date) ?: return@Button
            val parsedPrice = price.toLongOrNull() ?: return@Button
            val own = ((ratio.toDoubleOrNull() ?: 100.0) / 100).coerceIn(0.0, 1.0)
            val spouse = ((spouseRatio.toDoubleOrNull() ?: 0.0) / 100).coerceIn(0.0, 1.0)
            if (own + spouse > 1.0001 || own + spouse <= 0) return@Button
            val ownerBirth = parseOptionalDate(ownerBirthDate)
            val spouseBirth = parseOptionalDate(spouseBirthDate)
            onSave(PropertyDraft(
                id = initial?.id, name = name, propertyType = type, address = address,
                acquisitionDate = parsedDate, acquisitionPrice = parsedPrice, ownershipRatio = own,
                officialAssessedValue = assessed.toLongOrNull(), currentEstimatedValue = initial?.currentEstimatedValue,
                actualAcquisitionTax = initial?.actualAcquisitionTax, brokerageFee = initial?.brokerageFee ?: 0,
                legalFee = initial?.legalFee ?: 0, renovationCost = initial?.renovationCost ?: 0,
                otherNecessaryExpenses = initial?.otherNecessaryExpenses ?: 0,
                residenceStartDate = parseOptionalDate(residenceStart), residenceEndDate = parseOptionalDate(residenceEnd),
                spouseOwnershipRatio = spouse, regulatedAreaAtAcquisition = regulated,
                expectedCompletionDate = parseOptionalDate(completion), ownerBirthYear = ownerBirth?.year,
                spouseBirthYear = spouseBirth?.year, ownerBirthDate = ownerBirth, spouseBirthDate = spouseBirth,
                acquisitionContractDate = parseOptionalDate(contractDate), urbanAreaTaxApplicable = urban,
                annualRegionalResourceTax = regionalTax.toLongOrNull(), acquisitionRuralSpecialTax = ruralTax.toLongOrNull(),
                acquisitionHouseCountTreatment = acquisitionCount, capitalGainsHouseCountTreatment = capitalCount,
                comprehensiveTaxTreatment = comprehensive, capitalGainsSurchargeTreatment = surchargeTreatment,
                acquisitionSurchargeRelief = acquisitionRelief, previousHomeDispositionDate = parseOptionalDate(priorDisposal),
                residenceRequirementExempt = residenceExempt, jointComprehensiveTaxSpecialRequested = jointSpecial,
                jointSpecialTaxpayer = specialTaxpayer,
            ))
        }, enabled = name.isNotBlank() && price.toLongOrNull() != null && parseDate(date) != null) { Text("저장") } },
        dismissButton = { TextButton(onDismiss) { Text("취소") } },
    )
}

@Composable
private fun SimulationDialog(properties: List<PropertyEntity>, onDismiss: () -> Unit, onSave: (SaleSimulationDraft) -> Unit) {
    val defaultSaleDate = minOf(LocalDate.now().plusMonths(3), LocalDate.of(KoreanPropertyTaxRules2026.version.effectiveUntil!!.year, 12, 31))
    var selected by remember { mutableStateOf(properties.firstOrNull()?.id) }
    var name by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(defaultSaleDate.toString()) }
    var price by remember { mutableStateOf("") }
    var expenses by remember { mutableStateOf("") }
    var regulated by remember { mutableStateOf<Boolean?>(null) }
    var contract by remember { mutableStateOf("") }
    var deposit by remember { mutableStateOf(false) }
    var permitRequired by remember { mutableStateOf<Boolean?>(null) }
    var permitApplication by remember { mutableStateOf("") }
    var permitApproved by remember { mutableStateOf(false) }
    var extendedRegion by remember { mutableStateOf(false) }
    var moveIn by remember { mutableStateOf("") }
    var residenceEnd by remember { mutableStateOf("") }
    var ownerBasicUsed by remember { mutableStateOf("") }
    var spouseBasicUsed by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("새 양도 시뮬레이션") }, text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.heightIn(max = 560.dp)) {
        items(properties, key = { it.id }) { property -> FilterChip(selected == property.id, { selected = property.id }, { Text(property.name) }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(name, { name = it }, label = { Text("시나리오 이름") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { OutlinedTextField(date, { date = it }, label = { Text("예상 매도일 YYYY-MM-DD · 현재 2026 Rule 지원") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { NumberField("예상 매도가 (원)", price) { price = it } }
        item { NumberField("추가 필요경비 (원)", expenses) { expenses = it } }
        item { BooleanSelector("양도 당시 조정대상지역", regulated) { regulated = it } }
        item { Text("2026년 다주택 중과 유예 검증", fontWeight = FontWeight.Black, color = AppCobalt) }
        item { OutlinedTextField(contract, { contract = it }, label = { Text("매매계약일 YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { LabeledCheckbox("계약금 수령 증빙 있음", deposit) { deposit = it } }
        item { BooleanSelector("토지거래허가 대상", permitRequired) { permitRequired = it } }
        if (permitRequired == true) {
            item { OutlinedTextField(permitApplication, { permitApplication = it }, label = { Text("토지거래허가 신청일 YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
            item { LabeledCheckbox("토지거래허가 승인 완료", permitApproved) { permitApproved = it } }
        }
        item { LabeledCheckbox("법정 6개월 연장 대상 지역", extendedRegion) { extendedRegion = it } }
        item { Text("완공 후 1주택+1분양권 특례 날짜", fontWeight = FontWeight.Black, color = AppCobalt) }
        item { OutlinedTextField(moveIn, { moveIn = it }, label = { Text("신축주택 이사일 YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { OutlinedTextField(residenceEnd, { residenceEnd = it }, label = { Text("1년 계속 거주 확인일 YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        item { Text("같은 과세연도 기본공제 사용액", fontWeight = FontWeight.Black, color = AppCobalt) }
        item { NumberField("본인 기사용 기본공제 (최대 250만원)", ownerBasicUsed) { ownerBasicUsed = it } }
        item { NumberField("배우자 기사용 기본공제 (최대 250만원)", spouseBasicUsed) { spouseBasicUsed = it } }
    } }, confirmButton = { Button({
        val id = selected ?: return@Button
        val parsedDate = parseDate(date) ?: return@Button
        val parsedPrice = price.toLongOrNull() ?: return@Button
        onSave(SaleSimulationDraft(
            id, name, parsedDate, parsedPrice, expenses.toLongOrNull() ?: 0L, regulated,
            parseOptionalDate(contract), deposit, permitRequired, parseOptionalDate(permitApplication), permitApproved,
            extendedRegion, parseOptionalDate(moveIn), parseOptionalDate(residenceEnd),
            (ownerBasicUsed.toLongOrNull() ?: 0L).coerceIn(0L, 2_500_000L),
            (spouseBasicUsed.toLongOrNull() ?: 0L).coerceIn(0L, 2_500_000L),
        ))
    }, enabled = selected != null && price.toLongOrNull() != null && parseDate(date) != null) { Text("계산 및 저장") } }, dismissButton = { TextButton(onDismiss) { Text("취소") } })
}

@Composable
private fun BooleanSelector(label: String, value: Boolean?, onChange: (Boolean?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(value == null, { onChange(null) }, { Text("모름") })
            FilterChip(value == true, { onChange(true) }, { Text("해당") })
            FilterChip(value == false, { onChange(false) }, { Text("비해당") })
        }
    }
}

@Composable
private fun TreatmentSelector(label: String, value: TaxTreatment, onChange: (TaxTreatment) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { TaxTreatment.entries.forEach { FilterChip(value == it, { onChange(it) }, { Text(it.label) }) } }
    }
}

@Composable
private fun LabeledCheckbox(label: String, checked: Boolean, onChange: (Boolean) -> Unit) = Row(verticalAlignment = Alignment.CenterVertically) {
    Checkbox(checked, onChange)
    Text(label, style = MaterialTheme.typography.bodySmall)
}

private fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()
private fun parseOptionalDate(value: String): LocalDate? = value.takeIf(String::isNotBlank)?.let(::parseDate)

@Composable private fun NumberField(label: String, value: String, showWon: Boolean = true, onChange: (String) -> Unit) = OutlinedTextField(value, { onChange(it.filter(Char::isDigit)) }, label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth(), supportingText = { if (showWon && value.toLongOrNull() != null) Text(won(value.toLong())) })

@Composable
private fun AiAnalysisDialog(value: PropertyTaxAiAnalysis, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val statusColor = when (value.verificationStatus) {
        TaxLawVerificationStatus.CURRENT -> AppGreen
        TaxLawVerificationStatus.CHANGE_DETECTED -> MaterialTheme.colorScheme.error
        TaxLawVerificationStatus.INCONCLUSIVE -> AppOrange
    }
    val statusTitle = when (value.verificationStatus) {
        TaxLawVerificationStatus.CURRENT -> "공식 법령 확인 완료"
        TaxLawVerificationStatus.CHANGE_DETECTED -> "세법 변경 감지 · 계산값 확정 금지"
        TaxLawVerificationStatus.INCONCLUSIVE -> "공식 법령 검증 불완전"
    }
    val checkedAt = value.checkedAt?.let {
        DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm").withZone(ZoneId.of("Asia/Seoul")).format(Instant.ofEpochMilli(it))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("최신 법령 기반 세금 분석", fontWeight = FontWeight.Black) },
        text = { LazyColumn(Modifier.heightIn(max = 580.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = .1f)), border = BorderStroke(1.dp, statusColor.copy(alpha = .45f))) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(if (value.calculationSafe) Icons.Rounded.Verified else Icons.Rounded.Warning, null, tint = statusColor)
                            Text(statusTitle, fontWeight = FontWeight.Black, color = statusColor)
                        }
                        Text(value.verificationSummary, style = MaterialTheme.typography.bodySmall)
                        checkedAt?.let { Text("확인 시각 $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            if (value.previousCheckedAt != null) item {
                Card(colors = CardDefaults.cardColors(containerColor = AppCobalt.copy(alpha = .07f)), border = BorderStroke(1.dp, AppCobalt.copy(alpha = .25f))) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.History, null, tint = AppCobalt)
                            Text("이전 분석과 비교", fontWeight = FontWeight.Black, color = AppCobalt)
                        }
                        Text(
                            "비교 기준 ${DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm").withZone(ZoneId.of("Asia/Seoul")).format(Instant.ofEpochMilli(value.previousCheckedAt))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (value.comparisonSummary.isNotBlank()) Text(value.comparisonSummary, style = MaterialTheme.typography.bodySmall)
                        ComparisonItems("이전 분석에서 바로잡은 내용", value.correctedPreviousFindings, MaterialTheme.colorScheme.error)
                        ComparisonItems("이번에 새로 확인된 차이", value.newlyDetectedDifferences, AppOrange)
                        ComparisonItems("그대로 유효한 판단", value.unchangedFindings, AppGreen)
                    }
                }
            }
            if (value.detectedLawChanges.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("감지된 법령 변경", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.error)
                    value.detectedLawChanges.forEach { change ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = .45f))) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(change.title, fontWeight = FontWeight.Bold)
                                if (change.ruleId.isNotBlank()) Text("대상 ${change.ruleId}", style = MaterialTheme.typography.labelSmall)
                                if (change.effectiveDate.isNotBlank()) Text("시행일 ${change.effectiveDate}", style = MaterialTheme.typography.bodySmall)
                                if (change.transitionRule.isNotBlank()) Text("경과규정 ${change.transitionRule}", style = MaterialTheme.typography.bodySmall)
                                if (change.impact.isNotBlank()) Text("영향 ${change.impact}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            item { Text(value.summary) }
            analysisSection("주요 변화", value.majorChanges)
            analysisSection("계산 결과의 이유", value.reasons)
            analysisSection("주의할 점", value.risks)
            analysisSection("추가 확인 정보", value.missingInformation)
            analysisSection("추가 비교 시나리오", value.suggestedScenarios)
            if (value.officialSources.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("이번 분석의 공식 근거", fontWeight = FontWeight.Black, color = AppCobalt)
                    value.officialSources.forEach { source ->
                        Text(
                            text = "• ${source.title}",
                            modifier = Modifier.clickable { runCatching { uriHandler.openUri(source.url) } },
                            style = MaterialTheme.typography.bodySmall,
                            color = AppCobalt,
                            textDecoration = TextDecoration.Underline,
                        )
                    }
                }
            }
            item { Text("공식 법령과 Engine이 다르거나 검증이 불완전하면 계산값을 확정하지 않습니다. AI가 계산식이나 Rule을 자동 변경하지는 않습니다.", style = MaterialTheme.typography.labelSmall, color = AppOrange) }
        } },
        confirmButton = { Button(onDismiss) { Text("확인") } },
    )
}

@Composable
private fun ComparisonItems(title: String, values: List<String>, color: Color) {
    if (values.isEmpty()) return
    Text(title, fontWeight = FontWeight.Bold, color = color, style = MaterialTheme.typography.bodySmall)
    values.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
}

private fun androidx.compose.foundation.lazy.LazyListScope.analysisSection(title: String, values: List<String>) { if (values.isNotEmpty()) item { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, fontWeight = FontWeight.Black, color = AppCobalt); values.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) } } } }
@Composable private fun TaxRow(label: String, value: String, strong: Boolean = false) = Row(Modifier.fillMaxWidth()) { Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, fontWeight = if (strong) FontWeight.Black else FontWeight.Bold, color = if (strong) AppCobalt else MaterialTheme.colorScheme.onSurface) }
@Composable private fun EmptyCard(title: String, description: String) = Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, fontWeight = FontWeight.Black); Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
private fun parseStrings(json: String) = runCatching { JSONArray(json) }.getOrNull()?.let { array -> (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) } } ?: emptyList()
private fun ruleApplied(json: String, ruleId: String): Boolean = runCatching { JSONArray(json) }.getOrNull()?.let { array -> (0 until array.length()).any { index -> array.getJSONObject(index).optString("ruleId") == ruleId && array.getJSONObject(index).optBoolean("applied") } } ?: false
private fun traceAmount(json: String, label: String): Long? = runCatching { JSONArray(json) }.getOrNull()?.let { array -> (0 until array.length()).firstNotNullOfOrNull { index -> array.getJSONObject(index).takeIf { it.optString("label") == label }?.optLong("amount") } }
private fun String.propertyTypeLabel() = runCatching { PropertyType.valueOf(this).label }.getOrDefault(this)
private fun won(value: Long) = NumberFormat.getNumberInstance(Locale.KOREA).format(value) + "원"
private fun signedWon(value: Long) = (if (value > 0) "+" else "") + won(value)
private fun PropertyEntity.screenDraft() = PropertyDraft(
    id, name, PropertyType.valueOf(propertyType), address, LocalDate.parse(acquisitionDate), acquisitionPrice,
    ownershipRatio, officialAssessedValue, currentEstimatedValue, actualAcquisitionTax, brokerageFee, legalFee,
    renovationCost, otherNecessaryExpenses, residenceStartDate?.let(LocalDate::parse), residenceEndDate?.let(LocalDate::parse),
    spouseOwnershipRatio, regulatedAreaAtAcquisition, expectedCompletionDate?.let(LocalDate::parse), ownerBirthYear,
    spouseBirthYear, ownerBirthDate?.let(LocalDate::parse), spouseBirthDate?.let(LocalDate::parse),
    acquisitionContractDate?.let(LocalDate::parse), urbanAreaTaxApplicable, annualRegionalResourceTax,
    acquisitionRuralSpecialTax, runCatching { TaxTreatment.valueOf(acquisitionHouseCountTreatment) }.getOrDefault(TaxTreatment.AUTO),
    runCatching { TaxTreatment.valueOf(capitalGainsHouseCountTreatment) }.getOrDefault(TaxTreatment.AUTO),
    runCatching { TaxTreatment.valueOf(comprehensiveTaxTreatment) }.getOrDefault(TaxTreatment.AUTO),
    runCatching { TaxTreatment.valueOf(capitalGainsSurchargeTreatment) }.getOrDefault(TaxTreatment.AUTO),
    runCatching { AcquisitionSurchargeRelief.valueOf(acquisitionSurchargeRelief) }.getOrDefault(AcquisitionSurchargeRelief.NONE),
    previousHomeDispositionDate?.let(LocalDate::parse), residenceRequirementExempt,
    jointComprehensiveTaxSpecialRequested, jointSpecialTaxpayer?.let { runCatching { OwnerRole.valueOf(it) }.getOrNull() },
)
private fun Modifier.horizontalSwipe(onSwipeLeft: () -> Unit, onSwipeRight: () -> Unit): Modifier = pointerInput(Unit) { awaitPointerEventScope { while (true) { val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial); var dx = 0f; var dy = 0f; while (true) { val event = awaitPointerEvent(PointerEventPass.Initial); val change = event.changes.firstOrNull { it.id == down.id } ?: break; if (!change.pressed) { if (abs(dx) > 100f && abs(dx) > abs(dy) * 1.2f) { if (dx < 0) onSwipeLeft() else onSwipeRight() }; break }; val delta = change.positionChange(); dx += delta.x; dy += delta.y } } } }
