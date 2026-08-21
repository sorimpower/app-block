package com.sorimpower.app.feature.assets.data

import android.content.Context
import com.sorimpower.app.feature.assets.domain.AssetClass
import com.sorimpower.app.feature.assets.domain.AssetItem
import com.sorimpower.app.feature.assets.domain.AssetPortfolio
import com.sorimpower.app.feature.assets.domain.AssetSnapshot
import com.sorimpower.app.feature.assets.domain.ValuationBadge
import com.sorimpower.app.feature.assets.domain.ValuationConfidence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.time.LocalDate

class AssetRepository(context: Context) {
    private val dao = AssetDatabase.get(context).dao()

    val portfolio: Flow<AssetPortfolio> = combine(dao.observeAssets(), dao.observeSnapshots()) { assets, snapshots ->
        AssetPortfolio(assets.map(AssetEntity::toDomain), snapshots.map(AssetSnapshotEntity::toDomain))
    }

    suspend fun save(item: AssetItem) = withContext(Dispatchers.IO) {
        val updated = dao.getAssets().map(AssetEntity::toDomain).filterNot { it.id == item.id } + item
        dao.saveAssetAndSnapshot(item.toEntity(), snapshotFor(updated))
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val updated = dao.getAssets().map(AssetEntity::toDomain).filterNot { it.id == id }
        dao.deleteAssetAndSnapshot(id, snapshotFor(updated))
    }

    suspend fun captureToday() = withContext(Dispatchers.IO) {
        dao.upsertSnapshot(snapshotFor(dao.getAssets().map(AssetEntity::toDomain)))
    }

    private fun snapshotFor(items: List<AssetItem>): AssetSnapshotEntity {
        val date = LocalDate.now()
        val assets = items.filterNot { it.assetClass.isLiability }.sumOf(AssetItem::valueKrw)
        val liabilities = items.filter { it.assetClass.isLiability }.sumOf(AssetItem::valueKrw)
        return AssetSnapshotEntity(
            id = date.toString(),
            snapshotEpochDay = date.toEpochDay(),
            netWorthKrw = assets - liabilities,
            totalAssetsKrw = assets,
            totalLiabilitiesKrw = liabilities,
            stockProvider = items.sourceFor(AssetClass.STOCK_ETF),
            cryptoProvider = items.sourceFor(AssetClass.CRYPTO),
            realEstateDataset = items.sourceFor(AssetClass.REAL_ESTATE),
            realEstateAlgorithm = items.filter { it.assetClass == AssetClass.REAL_ESTATE }
                .mapNotNull(AssetItem::algorithmVersion).distinct().joinToString().ifBlank { "미사용" },
            vehicleProvider = items.sourceFor(AssetClass.VEHICLE),
            fxProvider = "미사용",
            createdAt = System.currentTimeMillis(),
        )
    }
}

private fun List<AssetItem>.sourceFor(assetClass: AssetClass): String =
    filter { it.assetClass == assetClass }.map(AssetItem::providerId).distinct().joinToString().ifBlank { "미사용" }

private fun AssetEntity.toDomain() = AssetItem(
    id = id,
    assetClass = enumValueOf(assetClass),
    name = name,
    valueKrw = valueKrw,
    providerId = providerId,
    providerName = providerName,
    badge = enumValueOf(badge),
    valuationDate = LocalDate.ofEpochDay(valuationEpochDay),
    valuationMethod = valuationMethod,
    algorithmVersion = algorithmVersion,
    confidence = confidence?.let { runCatching { enumValueOf<ValuationConfidence>(it) }.getOrNull() },
    sourceStatus = sourceStatus,
    detail = detail,
    address = address,
    lawdCd = lawdCd,
    exclusiveAreaSqm = exclusiveAreaSqm,
    ownershipPercent = ownershipPercent,
    modelYear = modelYear,
    trim = trim,
    mileageKm = mileageKm,
    comparableMinKrw = comparableMinKrw,
    comparableMaxKrw = comparableMaxKrw,
    comparableCount = comparableCount,
    latestComparableTradeDate = latestComparableTradeEpochDay?.let(LocalDate::ofEpochDay),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun AssetItem.toEntity() = AssetEntity(
    id = id,
    assetClass = assetClass.name,
    name = name.trim(),
    valueKrw = valueKrw.coerceAtLeast(0L),
    providerId = providerId,
    providerName = providerName,
    badge = badge.name,
    valuationEpochDay = valuationDate.toEpochDay(),
    valuationMethod = valuationMethod,
    algorithmVersion = algorithmVersion,
    confidence = confidence?.name,
    sourceStatus = sourceStatus,
    detail = detail.trim(),
    address = address.trim(),
    lawdCd = lawdCd.trim(),
    exclusiveAreaSqm = exclusiveAreaSqm,
    ownershipPercent = ownershipPercent.coerceIn(0.0, 100.0),
    modelYear = modelYear,
    trim = trim.trim(),
    mileageKm = mileageKm,
    comparableMinKrw = comparableMinKrw,
    comparableMaxKrw = comparableMaxKrw,
    comparableCount = comparableCount,
    latestComparableTradeEpochDay = latestComparableTradeDate?.toEpochDay(),
    createdAt = createdAt,
    updatedAt = System.currentTimeMillis(),
)

private fun AssetSnapshotEntity.toDomain() = AssetSnapshot(
    id = id,
    date = LocalDate.ofEpochDay(snapshotEpochDay),
    netWorthKrw = netWorthKrw,
    totalAssetsKrw = totalAssetsKrw,
    totalLiabilitiesKrw = totalLiabilitiesKrw,
    stockProvider = stockProvider,
    cryptoProvider = cryptoProvider,
    realEstateDataset = realEstateDataset,
    realEstateAlgorithm = realEstateAlgorithm,
    vehicleProvider = vehicleProvider,
    fxProvider = fxProvider,
    createdAt = createdAt,
)
