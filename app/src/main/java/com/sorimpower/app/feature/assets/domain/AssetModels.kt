package com.sorimpower.app.feature.assets.domain

import java.time.LocalDate
import java.util.UUID

enum class AssetClass(val label: String, val isLiability: Boolean = false) {
    STOCK_ETF("주식·ETF"),
    CRYPTO("코인"),
    REAL_ESTATE("부동산"),
    VEHICLE("자동차"),
    CASH("현금"),
    LIABILITY("부채", isLiability = true),
}

enum class ValuationBadge(val label: String) {
    LIVE("LIVE"),
    ESTIMATED("EST."),
    MANUAL("MANUAL"),
    STALE("STALE"),
}

enum class ValuationConfidence(val label: String) {
    HIGH("높음"),
    MEDIUM("보통"),
    LOW("낮음"),
}

data class AssetItem(
    val id: String = UUID.randomUUID().toString(),
    val assetClass: AssetClass,
    val name: String,
    val valueKrw: Long,
    val providerId: String,
    val providerName: String,
    val badge: ValuationBadge,
    val valuationDate: LocalDate,
    val valuationMethod: String,
    val algorithmVersion: String? = null,
    val confidence: ValuationConfidence? = null,
    val sourceStatus: String = "정상",
    val detail: String = "",
    val address: String = "",
    val lawdCd: String = "",
    val exclusiveAreaSqm: Double? = null,
    val ownershipPercent: Double = 100.0,
    val modelYear: Int? = null,
    val trim: String = "",
    val mileageKm: Int? = null,
    val comparableMinKrw: Long? = null,
    val comparableMaxKrw: Long? = null,
    val comparableCount: Int = 0,
    val latestComparableTradeDate: LocalDate? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val signedValueKrw: Long get() = if (assetClass.isLiability) -valueKrw else valueKrw
}

data class AssetSnapshot(
    val id: String,
    val date: LocalDate,
    val netWorthKrw: Long,
    val totalAssetsKrw: Long,
    val totalLiabilitiesKrw: Long,
    val stockProvider: String,
    val cryptoProvider: String,
    val realEstateDataset: String,
    val realEstateAlgorithm: String,
    val vehicleProvider: String,
    val fxProvider: String,
    val createdAt: Long,
)

data class AssetPortfolio(
    val items: List<AssetItem> = emptyList(),
    val snapshots: List<AssetSnapshot> = emptyList(),
) {
    val totalAssetsKrw: Long = items.filterNot { it.assetClass.isLiability }.sumOf(AssetItem::valueKrw)
    val totalLiabilitiesKrw: Long = items.filter { it.assetClass.isLiability }.sumOf(AssetItem::valueKrw)
    val netWorthKrw: Long = totalAssetsKrw - totalLiabilitiesKrw
}

data class AssetProviderPolicy(
    val assetClass: AssetClass,
    val providerId: String,
    val providerName: String,
    val valuationMethod: String,
    val connectionNote: String,
)

object AssetDataSourceRegistry {
    val policies = listOf(
        AssetProviderPolicy(AssetClass.STOCK_ETF, "KIS", "한국투자증권 KIS Open API", "시장 현재가", "KIS 앱 키 연결 후 자동 평가"),
        AssetProviderPolicy(AssetClass.CRYPTO, "COINGECKO", "CoinGecko", "시장 현재가", "CoinGecko 연동 대상"),
        AssetProviderPolicy(AssetClass.REAL_ESTATE, "MOLIT_RTMS", "국토교통부 실거래가", "최근 유사 실거래 기반 추정", "공공데이터 서비스 키 연결 후 자동 추정"),
        AssetProviderPolicy(AssetClass.VEHICLE, "KB_CAR_PRICE", "KB캐피탈 / KB차차차", "중고차 추정 시세", "제휴 API 승인 후 자동 조회"),
        AssetProviderPolicy(AssetClass.CASH, "MANUAL", "사용자 입력", "현재 잔액", "직접 입력"),
        AssetProviderPolicy(AssetClass.LIABILITY, "MANUAL", "사용자 입력", "현재 잔액", "직접 입력"),
    )

    fun policy(assetClass: AssetClass): AssetProviderPolicy = policies.first { it.assetClass == assetClass }
}

object RealEstateAptValuationV1 {
    const val VERSION = "REAL_ESTATE_APT_V1"

    data class Result(
        val estimatedValueKrw: Long,
        val comparableMinKrw: Long,
        val comparableMaxKrw: Long,
        val comparableCount: Int,
        val confidence: ValuationConfidence,
    )

    /** 최근 거래일 순으로 전달된 동일 단지·유사 면적 거래에서 극단값을 완화한 중앙값을 사용한다. */
    fun evaluate(recentComparablePricesKrw: List<Long>, ownershipPercent: Double = 100.0): Result? {
        val valid = recentComparablePricesKrw.filter { it > 0L }.sorted()
        if (valid.isEmpty()) return null
        val trimmed = if (valid.size >= 5) valid.drop(1).dropLast(1) else valid
        val median = if (trimmed.size % 2 == 1) {
            trimmed[trimmed.size / 2]
        } else {
            (trimmed[trimmed.size / 2 - 1] / 2L) + (trimmed[trimmed.size / 2] / 2L)
        }
        val share = ownershipPercent.coerceIn(0.0, 100.0) / 100.0
        return Result(
            estimatedValueKrw = (median * share).toLong(),
            comparableMinKrw = valid.first(),
            comparableMaxKrw = valid.last(),
            comparableCount = valid.size,
            confidence = when {
                valid.size >= 5 -> ValuationConfidence.HIGH
                valid.size >= 3 -> ValuationConfidence.MEDIUM
                else -> ValuationConfidence.LOW
            },
        )
    }
}
