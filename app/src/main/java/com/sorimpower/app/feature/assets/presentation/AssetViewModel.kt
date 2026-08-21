package com.sorimpower.app.feature.assets.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sorimpower.app.feature.assets.data.AssetRepository
import com.sorimpower.app.feature.assets.data.MolitRealEstateProvider
import com.sorimpower.app.feature.assets.domain.AssetClass
import com.sorimpower.app.feature.assets.domain.AssetDataSourceRegistry
import com.sorimpower.app.feature.assets.domain.AssetItem
import com.sorimpower.app.feature.assets.domain.AssetPortfolio
import com.sorimpower.app.feature.assets.domain.RealEstateAptValuationV1
import com.sorimpower.app.feature.assets.domain.ValuationBadge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

data class AssetUiState(
    val portfolio: AssetPortfolio = AssetPortfolio(),
    val saving: Boolean = false,
    val message: String? = null,
    val lookingUpRealEstate: Boolean = false,
)

data class AssetDraft(
    val id: String? = null,
    val assetClass: AssetClass = AssetClass.CASH,
    val name: String = "",
    val valueKrwText: String = "",
    val detail: String = "",
    val address: String = "",
    val lawdCd: String = "",
    val exclusiveAreaSqmText: String = "",
    val ownershipPercentText: String = "100",
    val modelYearText: String = "",
    val trim: String = "",
    val mileageKmText: String = "",
    val comparablePricesText: String = "",
    val comparablesFromMolit: Boolean = false,
    val latestComparableTradeDate: String = "",
)

class AssetViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AssetRepository(application)
    private val molitProvider = MolitRealEstateProvider()
    private val saving = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val lookingUpRealEstate = MutableStateFlow(false)

    val state = combine(repository.portfolio, saving, message, lookingUpRealEstate) { portfolio, isSaving, notice, isLookingUp ->
        AssetUiState(portfolio, isSaving, notice, isLookingUp)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssetUiState())

    init { viewModelScope.launch { repository.captureToday() } }

    fun save(draft: AssetDraft) {
        val existing = draft.id?.let { id -> state.value.portfolio.items.firstOrNull { it.id == id } }
        val name = draft.name.trim()
        val manualValue = draft.valueKrwText.onlyDigits().toLongOrNull()
        if (name.isBlank()) {
            message.value = "자산 이름을 입력해 주세요."
            return
        }
        val ownership = draft.ownershipPercentText.toDoubleOrNull()?.coerceIn(0.0, 100.0) ?: 100.0
        val comparablePrices = draft.comparablePricesText.split(',', '\n')
            .mapNotNull { it.onlyDigits().toLongOrNull() }
            .map { it * 10_000L }
        val realEstateResult = if (draft.assetClass == AssetClass.REAL_ESTATE) {
            RealEstateAptValuationV1.evaluate(comparablePrices, ownership)
        } else null
        val value = realEstateResult?.estimatedValueKrw ?: manualValue
        if (value == null || value < 0L) {
            message.value = "평가액을 원 단위 숫자로 입력해 주세요."
            return
        }
        val canonical = AssetDataSourceRegistry.policy(draft.assetClass)
        val isExternalEstimate = realEstateResult != null && draft.comparablesFromMolit
        val preservedValuation = existing?.takeIf { realEstateResult == null && it.valueKrw == value }
        val item = AssetItem(
            id = draft.id ?: UUID.randomUUID().toString(),
            assetClass = draft.assetClass,
            name = name,
            valueKrw = value,
            providerId = when {
                isExternalEstimate -> canonical.providerId
                preservedValuation != null -> preservedValuation.providerId
                else -> "MANUAL"
            },
            providerName = when {
                isExternalEstimate -> canonical.providerName
                preservedValuation != null -> preservedValuation.providerName
                else -> "사용자 입력"
            },
            badge = when {
                realEstateResult != null -> ValuationBadge.ESTIMATED
                preservedValuation != null -> preservedValuation.badge
                else -> ValuationBadge.MANUAL
            },
            valuationDate = LocalDate.now(),
            valuationMethod = when {
                isExternalEstimate -> canonical.valuationMethod
                realEstateResult != null -> "사용자가 입력한 유사 거래 중앙값 추정"
                preservedValuation != null -> preservedValuation.valuationMethod
                else -> "사용자 직접 평가"
            },
            algorithmVersion = if (realEstateResult != null) RealEstateAptValuationV1.VERSION else preservedValuation?.algorithmVersion,
            confidence = realEstateResult?.confidence ?: preservedValuation?.confidence,
            sourceStatus = when {
                isExternalEstimate -> "정상"
                realEstateResult != null -> "수동 비교거래"
                preservedValuation != null -> preservedValuation.sourceStatus
                else -> "수동 평가"
            },
            detail = draft.detail,
            address = draft.address,
            lawdCd = draft.lawdCd,
            exclusiveAreaSqm = draft.exclusiveAreaSqmText.toDoubleOrNull(),
            ownershipPercent = ownership,
            modelYear = draft.modelYearText.toIntOrNull(),
            trim = draft.trim,
            mileageKm = draft.mileageKmText.onlyDigits().toIntOrNull(),
            comparableMinKrw = realEstateResult?.comparableMinKrw ?: preservedValuation?.comparableMinKrw,
            comparableMaxKrw = realEstateResult?.comparableMaxKrw ?: preservedValuation?.comparableMaxKrw,
            comparableCount = realEstateResult?.comparableCount ?: preservedValuation?.comparableCount ?: 0,
            latestComparableTradeDate = if (isExternalEstimate) runCatching { LocalDate.parse(draft.latestComparableTradeDate) }.getOrNull() else preservedValuation?.latestComparableTradeDate,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        )
        viewModelScope.launch {
            saving.value = true
            runCatching { repository.save(item) }
                .onSuccess { message.value = "${item.name}을(를) 저장했어요." }
                .onFailure { message.value = "저장하지 못했어요." }
            saving.value = false
        }
    }

    fun lookupRealEstate(draft: AssetDraft, onSuccess: (AssetDraft) -> Unit) {
        val area = draft.exclusiveAreaSqmText.toDoubleOrNull()
        if (!Regex("\\d{5}").matches(draft.lawdCd)) {
            message.value = "법정동 지역코드 5자리를 입력해 주세요."
            return
        }
        if (draft.name.isBlank() || area == null || area <= 0.0) {
            message.value = "단지명과 전용면적을 먼저 입력해 주세요."
            return
        }
        viewModelScope.launch {
            lookingUpRealEstate.value = true
            runCatching { molitProvider.lookup(draft.lawdCd, draft.name.trim(), area) }
                .onSuccess { result ->
                    if (result.trades.isEmpty()) {
                        message.value = "최근 12개월 동일 단지·유사 면적 거래를 찾지 못했어요."
                    } else {
                        onSuccess(
                            draft.copy(
                                comparablePricesText = result.trades.joinToString(",") { (it.priceKrw / 10_000L).toString() },
                                comparablesFromMolit = true,
                                latestComparableTradeDate = result.trades.maxOf { it.tradeDate },
                            ),
                        )
                        message.value = "국토부 유사 실거래 ${result.trades.size}건을 불러왔어요."
                    }
                }
                .onFailure { error ->
                    message.value = when {
                        error.message?.contains("인증") == true -> "국토부 인증키 상태를 확인해 주세요."
                        else -> "국토부 실거래를 불러오지 못했어요. 잠시 후 다시 시도해 주세요."
                    }
                }
            lookingUpRealEstate.value = false
        }
    }

    fun delete(id: String) = viewModelScope.launch {
        runCatching { repository.delete(id) }
            .onSuccess { message.value = "자산을 삭제했어요." }
            .onFailure { message.value = "삭제하지 못했어요." }
    }

    fun consumeMessage() { message.value = null }
}

private fun String.onlyDigits(): String = filter(Char::isDigit)
