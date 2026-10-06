package com.sorimpower.app.feature.propertytracker.data

import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class NaverLandListing(
    val articleNo: String,
    val priceKrw: Long,
    val priceText: String,
    val supplyAreaSqm: Double?,
    val exclusiveAreaSqm: Double?,
    val floorInfo: String,
    val direction: String,
    val buildingName: String,
    val description: String,
    val tags: String,
    val confirmDate: String,
    val sourceUrl: String,
)

data class NaverLandComplex(
    val complexNo: String,
    val complexName: String,
    val cortarNo: String,
    val address: String,
    val totalHouseholdCount: Int?,
    val useApproveYmd: String,
)

data class NaverLandArea(
    val areaNo: String,
    val pyeongName: String,
    val supplyAreaSqm: Double?,
    val exclusiveAreaSqm: Double,
    val householdCount: Int?,
)

data class NaverLandComplexDetail(
    val complex: NaverLandComplex,
    val areas: List<NaverLandArea>,
)

class NaverLandProvider {
    private val sessionLock = Any()
    private val sessionCookies = linkedMapOf<String, String>()
    private var sessionToken = ""
    private var sessionCreatedAt = 0L

    suspend fun searchComplexes(keyword: String): List<NaverLandComplex> = withContext(Dispatchers.IO) {
        val query = keyword.trim()
        require(query.length >= 2) { "단지명이나 주소를 두 글자 이상 입력해 주세요." }
        val endpoint = "https://new.land.naver.com/api/search?keyword=${encode(query)}"
        parseComplexSearch(requestJson(endpoint, "https://new.land.naver.com/complexes"))
    }

    suspend fun fetchComplexDetail(complexNo: String): NaverLandComplexDetail = withContext(Dispatchers.IO) {
        require(complexNo.all(Char::isDigit) && complexNo.isNotBlank()) { "단지번호를 확인해 주세요." }
        val endpoint = "https://new.land.naver.com/api/complexes/$complexNo?sameAddressGroup=false"
        parseComplexDetail(requestJson(endpoint, "https://new.land.naver.com/complexes/$complexNo"), complexNo)
    }

    suspend fun fetchListings(target: PropertyWatchTargetEntity): List<NaverLandListing> = withContext(Dispatchers.IO) {
        val listings = mutableListOf<NaverLandListing>()
        var matchingArticleCount = 0
        var invalidPriceCount = 0
        var page = 1
        var hasMore: Boolean
        do {
            val endpoint = buildEndpoint(target, page)
            val body = requestJson(endpoint, "https://new.land.naver.com/complexes/${target.complexNo}")
            val root = JSONObject(body)
            if (!root.has("articleList") || root.isNull("articleList")) {
                throw NaverLandException("네이버 부동산 응답 형식이 변경되어 이번 기록을 건너뜁니다.")
            }
            val articles = root.optJSONArray("articleList")
            if (articles != null) {
                for (index in 0 until articles.length()) {
                    val item = articles.optJSONObject(index) ?: continue
                    val area = item.optDoubleOrNull("area2")
                    if (target.areaNo.isBlank() && area != null && abs(area - target.exclusiveAreaSqm) > 0.8) continue
                    matchingArticleCount++
                    val articleNo = item.optString("articleNo").trim()
                    val priceText = item.optString("dealOrWarrantPrc").trim()
                    val price = parseKoreanPrice(priceText)
                    if (price == null) {
                        invalidPriceCount++
                        continue
                    }
                    if (articleNo.isBlank()) continue
                    val tags = item.optJSONArray("tagList")?.let { array ->
                        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }.joinToString(" · ")
                    }.orEmpty()
                    listings += NaverLandListing(
                        articleNo = articleNo,
                        priceKrw = price,
                        priceText = priceText,
                        supplyAreaSqm = item.optDoubleOrNull("area1"),
                        exclusiveAreaSqm = area,
                        floorInfo = item.optString("floorInfo"),
                        direction = item.optString("direction"),
                        buildingName = item.optString("buildingName"),
                        description = item.optString("articleFeatureDesc"),
                        tags = tags,
                        confirmDate = item.optString("articleConfirmYmd"),
                        sourceUrl = item.optString("cpPcArticleBridgeUrl").ifBlank {
                            "https://new.land.naver.com/complexes/${target.complexNo}?articleNo=$articleNo"
                        },
                    )
                }
            }
            hasMore = root.optBoolean("isMoreData", false) && page < MAX_PAGES
            page++
            if (hasMore) delay(350)
        } while (hasMore)
        if (matchingArticleCount > 0 && invalidPriceCount == matchingArticleCount) {
            throw NaverLandException("네이버 호가 형식을 읽지 못해 이번 기록을 건너뜁니다.")
        }
        listings.distinctBy(NaverLandListing::articleNo)
    }

    private fun buildEndpoint(target: PropertyWatchTargetEntity, page: Int): String {
        val params = linkedMapOf(
            "realEstateType" to "APT:ABYG:JGC",
            "tradeType" to "A1",
            "tag" to ":::::::",
            "rentPriceMin" to "0",
            "rentPriceMax" to "900000000",
            "priceMin" to "0",
            "priceMax" to "900000000",
            "areaMin" to "0",
            "areaMax" to "900000000",
            "oldBuildYears" to "",
            "recentlyBuildYears" to "",
            "minHouseHoldCount" to "",
            "maxHouseHoldCount" to "",
            "showArticle" to "false",
            "sameAddressGroup" to "false",
            "minMaintenanceCost" to "",
            "maxMaintenanceCost" to "",
            "priceType" to "RETAIL",
            "directions" to "",
            "page" to page.toString(),
            "complexNo" to target.complexNo,
            "buildingNos" to "",
            "areaNos" to target.areaNo,
            "type" to "list",
            "order" to "rank",
        )
        val query = params.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        return "https://new.land.naver.com/api/articles/complex/${target.complexNo}?$query"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

    private fun requestJson(endpoint: String, referer: String): String {
        ensureSession()
        var response = executeRequest(endpoint, referer, acceptJson = true)
        if (response.status == 401 || response.status == 429) {
            invalidateSession()
            ensureSession()
            response = executeRequest(endpoint, referer, acceptJson = true)
        }
        if (response.status == 401) throw NaverLandException("네이버 부동산 인증 세션을 갱신하지 못했습니다. 잠시 후 다시 시도해 주세요.")
        if (response.status == 429) throw NaverLandException("네이버가 조회를 잠시 제한했습니다. 잠시 후 다시 시도해 주세요.")
        if (response.status !in 200..299) throw NaverLandException("네이버 부동산 응답 오류(${response.status})")
        return response.body
    }

    private fun ensureSession() = synchronized(sessionLock) {
        val sessionFresh = sessionCookies.isNotEmpty() && sessionToken.isNotBlank() &&
            System.currentTimeMillis() - sessionCreatedAt < SESSION_MAX_AGE_MS
        if (sessionFresh) return@synchronized
        sessionCookies.clear()
        sessionToken = ""
        val response = executeRequest(
            endpoint = "https://new.land.naver.com/complexes",
            referer = "https://new.land.naver.com/",
            acceptJson = false,
        )
        if (response.status !in 200..399) {
            throw NaverLandException("네이버 부동산 연결을 준비하지 못했습니다(${response.status}).")
        }
        sessionToken = extractSessionToken(response.body).orEmpty()
        if (sessionCookies.isEmpty() || sessionToken.isBlank()) {
            throw NaverLandException("네이버 부동산 검색 세션을 만들지 못했습니다. 잠시 후 다시 시도해 주세요.")
        }
        sessionCreatedAt = System.currentTimeMillis()
    }

    private fun invalidateSession() = synchronized(sessionLock) {
        sessionCookies.clear()
        sessionToken = ""
        sessionCreatedAt = 0L
    }

    private fun executeRequest(endpoint: String, referer: String, acceptJson: Boolean): HttpResponse {
        val connection = URI(endpoint).toURL().openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 12_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Accept", if (acceptJson) "application/json, text/plain, */*" else "text/html,application/xhtml+xml")
            connection.setRequestProperty("Accept-Language", "ko-KR,ko;q=0.9")
            connection.setRequestProperty("Referer", referer)
            connection.setRequestProperty("User-Agent", DESKTOP_USER_AGENT)
            synchronized(sessionLock) {
                if (sessionCookies.isNotEmpty()) {
                    connection.setRequestProperty("Cookie", sessionCookies.entries.joinToString("; ") { (name, value) -> "$name=$value" })
                }
                if (sessionToken.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer $sessionToken")
            }
            val status = connection.responseCode
            captureCookies(connection)
            val stream = if (status in 200..399) connection.inputStream else connection.errorStream
            HttpResponse(status, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }

    private fun captureCookies(connection: HttpURLConnection) = synchronized(sessionLock) {
        connection.headerFields.entries
            .filter { (name, _) -> name?.equals("Set-Cookie", ignoreCase = true) == true }
            .flatMap { it.value.orEmpty() }
            .forEach { header ->
                val pair = header.substringBefore(';')
                val separator = pair.indexOf('=')
                if (separator > 0) sessionCookies[pair.substring(0, separator).trim()] = pair.substring(separator + 1).trim()
            }
    }

    companion object {
        private const val MAX_PAGES = 20
        private const val SESSION_MAX_AGE_MS = 20 * 60 * 1_000L
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

        fun parseKoreanPrice(raw: String): Long? {
            val normalized = raw.replace(",", "").replace(" ", "").trim()
            if (normalized.isBlank()) return null
            val billionParts = normalized.split("억", limit = 2)
            return if (billionParts.size == 2) {
                val eok = billionParts[0].filter { it.isDigit() }.toLongOrNull() ?: return null
                val remainder = billionParts[1].filter { it.isDigit() }.toLongOrNull() ?: 0L
                eok * 100_000_000L + remainder * 10_000L
            } else {
                normalized.filter { it.isDigit() }.toLongOrNull()?.times(10_000L)
            }
        }

        fun extractComplexNo(input: String): String? {
            val trimmed = input.trim()
            if (trimmed.all(Char::isDigit) && trimmed.isNotBlank()) return trimmed
            return Regex("(?:complexes/|complex/info/|complex/)([0-9]+)").find(trimmed)?.groupValues?.getOrNull(1)
                ?: Regex("complexNo=([0-9]+)").find(trimmed)?.groupValues?.getOrNull(1)
        }

        fun extractAreaNo(input: String): String? =
            Regex("(?:areaNos?|areaNo)=([0-9]+)").find(input)?.groupValues?.getOrNull(1)

        fun extractSessionToken(html: String): String? =
            Regex("\\\"token\\\"\\s*:\\s*\\{\\s*\\\"token\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
                .find(html)?.groupValues?.getOrNull(1)

        fun parseComplexSearch(body: String): List<NaverLandComplex> {
            val root = JSONObject(body)
            val complexes = root.optJSONArray("complexes")
                ?: root.optJSONObject("result")?.optJSONArray("complexes")
                ?: JSONArray()
            return (0 until complexes.length()).mapNotNull { index ->
                complexes.optJSONObject(index)?.toComplex()
            }.distinctBy(NaverLandComplex::complexNo)
        }

        fun parseComplexDetail(body: String, fallbackComplexNo: String): NaverLandComplexDetail {
            val root = JSONObject(body)
            val detail = root.optJSONObject("complexDetail") ?: root
            val complex = detail.toComplex(fallbackComplexNo)
                ?: throw NaverLandException("네이버 단지 정보를 읽지 못했습니다. 직접 입력해 주세요.")
            val pyeongs = root.optJSONArray("complexPyeongDetailList")
                ?: detail.optJSONArray("complexPyeongDetailList")
                ?: JSONArray()
            val areas = (0 until pyeongs.length()).mapNotNull { index ->
                val item = pyeongs.optJSONObject(index) ?: return@mapNotNull null
                val exclusiveArea = item.optDoubleOrNull("exclusiveArea") ?: return@mapNotNull null
                val areaNo = item.optString("pyeongNo").ifBlank { item.optString("areaNo") }
                NaverLandArea(
                    areaNo = areaNo,
                    pyeongName = item.optString("pyeongName"),
                    supplyAreaSqm = item.optDoubleOrNull("supplyArea"),
                    exclusiveAreaSqm = exclusiveArea,
                    householdCount = item.optIntOrNull("householdCountByPyeong"),
                )
            }.distinctBy { it.areaNo to it.exclusiveAreaSqm }
                .sortedWith(compareBy(NaverLandArea::exclusiveAreaSqm, NaverLandArea::areaNo))
            return NaverLandComplexDetail(complex, areas)
        }

        private fun JSONObject.toComplex(fallbackComplexNo: String = ""): NaverLandComplex? {
            val complexNo = optString("complexNo").ifBlank { fallbackComplexNo }
            if (complexNo.isBlank()) return null
            val complexName = optString("complexName").ifBlank { optString("name") }
            val cortarNo = optString("cortarNo")
            val address = listOf(
                optString("cortarAddress").ifBlank { optString("address") },
                optString("detailAddress"),
            ).filter(String::isNotBlank).joinToString(" ").ifBlank { optString("roadAddress") }
            return NaverLandComplex(
                complexNo = complexNo,
                complexName = complexName,
                cortarNo = cortarNo,
                address = address,
                totalHouseholdCount = optIntOrNull("totalHouseholdCount"),
                useApproveYmd = optString("useApproveYmd"),
            )
        }
    }
}

private data class HttpResponse(val status: Int, val body: String)

class NaverLandException(message: String) : Exception(message)

private fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (!has(key) || isNull(key)) null else optDouble(key).takeUnless(Double::isNaN)

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (!has(key) || isNull(key)) null else optInt(key).takeIf { it > 0 }
