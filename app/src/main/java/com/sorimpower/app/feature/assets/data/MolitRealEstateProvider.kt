package com.sorimpower.app.feature.assets.data

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await

data class MolitComparableTrade(
    val apartmentName: String,
    val exclusiveAreaSqm: Double,
    val priceKrw: Long,
    val tradeDate: String,
    val floor: String,
)

data class MolitTradeResult(
    val providerId: String,
    val algorithmVersion: String,
    val queriedAt: String,
    val trades: List<MolitComparableTrade>,
)

class MolitRealEstateProvider {
    suspend fun lookup(lawdCd: String, apartmentName: String, exclusiveAreaSqm: Double): MolitTradeResult {
        val result = try {
            FirebaseFunctions.getInstance("asia-northeast3")
                .getHttpsCallable("lookupMolitApartmentTrades")
                .call(
                    mapOf(
                        "lawdCd" to lawdCd,
                        "apartmentName" to apartmentName,
                        "exclusiveAreaSqm" to exclusiveAreaSqm,
                        "months" to 18,
                    ),
                )
                .await()
        } catch (error: FirebaseFunctionsException) {
            val message = when (error.code) {
                FirebaseFunctionsException.Code.UNAVAILABLE,
                FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
                FirebaseFunctionsException.Code.INTERNAL,
                -> "국토부 서버 연결 지연"
                else -> error.message ?: "국토부 실거래 조회 실패"
            }
            throw IllegalStateException(message, error)
        }
        val data = result.data as? Map<*, *> ?: error("국토부 응답 형식이 올바르지 않습니다.")
        val trades = (data["trades"] as? List<*>)?.mapNotNull { raw ->
            val item = raw as? Map<*, *> ?: return@mapNotNull null
            val priceKrw = (item["priceKrw"] as? Number)?.toLong() ?: return@mapNotNull null
            MolitComparableTrade(
                apartmentName = item["apartmentName"] as? String ?: apartmentName,
                exclusiveAreaSqm = (item["exclusiveAreaSqm"] as? Number)?.toDouble() ?: exclusiveAreaSqm,
                priceKrw = priceKrw,
                tradeDate = item["tradeDate"] as? String ?: "",
                floor = item["floor"] as? String ?: "",
            )
        } ?: emptyList()
        return MolitTradeResult(
            providerId = data["providerId"] as? String ?: "MOLIT_RTMS",
            algorithmVersion = data["algorithmVersion"] as? String ?: "REAL_ESTATE_APT_V1",
            queriedAt = data["queriedAt"] as? String ?: "",
            trades = trades,
        )
    }
}
