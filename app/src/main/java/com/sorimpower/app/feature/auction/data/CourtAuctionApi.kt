package com.sorimpower.app.feature.auction.data

import com.sorimpower.app.feature.auction.domain.AuctionItem
import com.sorimpower.app.feature.auction.domain.MINIMUM_APPRAISAL_PRICE
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.round
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

internal data class CourtAuctionSnapshot(
    val items: List<AuctionItem>,
    val collectedAt: String,
)

internal data class CourtAuctionRawRow(
    val itemKey: String = "",
    val courtCode: String = "",
    val courtName: String = "",
    val internalCaseNumber: String = "",
    val caseNumber: String = "",
    val auctionItemNumber: String = "",
    val usageName: String = "",
    val appraisalPrice: Long = 0L,
    val notifiedMinimumPrice: Long = 0L,
    val minimumPrice: Long = 0L,
    val failedCount: Int = 0,
    val auctionDate: String = "",
    val auctionTime: String = "",
    val auctionPlace: String = "",
    val address: String = "",
    val sido: String = "",
    val representativeSidoCode: String = "",
    val searchSidoCode: String = "",
    val sigungu: String = "",
    val dong: String = "",
    val buildingName: String = "",
    val courtDepartment: String = "",
    val courtTel: String = "",
    val note: String = "",
    val interestCount: Int = 0,
    val isInProgress: Boolean = false,
)

private data class CourtAuctionPage(
    val page: Int,
    val totalPages: Int,
    val rows: List<CourtAuctionRawRow>,
)

/** Reads the same public court-auction search endpoint that the former GAS collector used. */
internal class CourtAuctionApi {
    suspend fun fetchSnapshot(now: ZonedDateTime = ZonedDateTime.now(SEOUL_ZONE_ID)): CourtAuctionSnapshot {
        val startDate = now.toLocalDate()
        val endDate = startDate.plusDays(SEARCH_DAYS)
        val collectedAt = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        val firstPage = requestPage(1, startDate, endDate)
        require(firstPage.page == 1) { "법원경매 첫 페이지 번호가 올바르지 않습니다." }
        require(firstPage.totalPages in 0..MAX_TOTAL_PAGES) { "법원경매 전체 페이지 수가 허용 범위를 벗어났습니다." }

        val allRows = firstPage.rows.toMutableList()
        for (page in 2..firstPage.totalPages) {
            delay(REQUEST_DELAY_MILLIS)
            val nextPage = requestPage(page, startDate, endDate)
            require(nextPage.page == page) { "요청한 법원경매 페이지와 응답 페이지가 다릅니다." }
            require(nextPage.totalPages == firstPage.totalPages) { "법원경매 동기화 중 전체 페이지 수가 변경되었습니다." }
            allRows += nextPage.rows
        }

        return CourtAuctionSnapshot(
            items = groupCourtAuctionRows(allRows, collectedAt),
            collectedAt = collectedAt,
        )
    }

    private fun requestPage(page: Int, startDate: LocalDate, endDate: LocalDate): CourtAuctionPage {
        val body = createSearchPayload(page, startDate, endDate).toString().toByteArray(Charsets.UTF_8)
        val connection = (URL(SEARCH_API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            instanceFollowRedirects = true
            doOutput = true
            setFixedLengthStreamingMode(body.size)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json;charset=UTF-8")
            setRequestProperty("User-Agent", MOBILE_USER_AGENT)
            setRequestProperty("SC-USERID", "SYSTEM")
            setRequestProperty("submissionid", "mf_wfm_mainFrame_sbm_selectGdsDtlSrch")
            setRequestProperty("Referer", SEARCH_PAGE_URL)
        }

        return try {
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IOException("법원경매 API HTTP 오류: $status")
            if (!response.trimStart().startsWith("{")) throw IOException("법원경매 API가 JSON이 아닌 응답을 반환했습니다.")
            parsePage(response, page)
        } finally {
            connection.disconnect()
        }
    }

    private fun parsePage(body: String, requestedPage: Int): CourtAuctionPage {
        val root = JSONObject(body)
        if (root.longValue("status") != 200L) {
            throw IOException(root.stringValue("message").ifBlank { "법원경매 검색에 실패했습니다." })
        }
        val data = root.optJSONObject("data") ?: throw IOException("법원경매 응답 데이터가 없습니다.")
        if (data.has("ipcheck") && !data.optBoolean("ipcheck", false)) {
            throw IOException("법원경매 사이트가 현재 요청 IP를 허용하지 않습니다.")
        }
        val pageInfo = data.optJSONObject("dma_pageInfo") ?: JSONObject()
        val totalCount = pageInfo.longValue("totalCnt").coerceAtLeast(0L)
        val totalPages = if (totalCount == 0L) 0 else ceil(totalCount.toDouble() / PAGE_SIZE).toInt()
        val array = data.optJSONArray("dlt_srchResult") ?: throw IOException("법원경매 응답에 검색 결과 배열이 없습니다.")
        return CourtAuctionPage(
            page = pageInfo.intValue("pageNo").takeIf { it > 0 } ?: requestedPage,
            totalPages = totalPages,
            rows = array.toRawRows(),
        )
    }

    private fun createSearchPayload(page: Int, startDate: LocalDate, endDate: LocalDate): JSONObject = JSONObject().apply {
        put("dma_pageInfo", JSONObject().apply {
            put("pageNo", page)
            put("pageSize", PAGE_SIZE.toString())
            put("bfPageNo", "")
            put("startRowNo", "")
            put("totalCnt", "")
            put("totalYn", "Y")
            put("groupTotalCount", "")
        })
        put("dma_srchGdsDtlSrchInfo", JSONObject().apply {
            put("rletDspslSpcCondCd", "")
            put("bidDvsCd", "000331")
            put("mvprpRletDvsCd", "00031R")
            put("cortAuctnSrchCondCd", "0004601")
            put("rprsAdongSdCd", SEOUL_CODE)
            put("rprsAdongSggCd", "")
            put("rprsAdongEmdCd", "")
            put("rdnmSdCd", "")
            put("rdnmSggCd", "")
            put("rdnmNo", "")
            put("mvprpDspslPlcAdongSdCd", "")
            put("mvprpDspslPlcAdongSggCd", "")
            put("mvprpDspslPlcAdongEmdCd", "")
            put("rdDspslPlcAdongSdCd", "")
            put("rdDspslPlcAdongSggCd", "")
            put("rdDspslPlcAdongEmdCd", "")
            put("cortOfcCd", "")
            put("jdbnCd", "")
            put("execrOfcDvsCd", "")
            put("lclDspslGdsLstUsgCd", "20000")
            put("mclDspslGdsLstUsgCd", "20100")
            put("sclDspslGdsLstUsgCd", "20104")
            put("cortAuctnMbrsId", "")
            put("aeeEvlAmtMin", MINIMUM_APPRAISAL_PRICE.toString())
            put("aeeEvlAmtMax", "")
            put("lwsDspslPrcRateMin", "")
            put("lwsDspslPrcRateMax", "")
            put("flbdNcntMin", "")
            put("flbdNcntMax", "")
            put("objctArDtsMin", "")
            put("objctArDtsMax", "")
            put("mvprpArtclKndCd", "")
            put("mvprpArtclNm", "")
            put("mvprpAtchmPlcTypCd", "")
            put("notifyLoc", "on")
            put("lafjOrderBy", "")
            put("pgmId", "PGJ151F01")
            put("csNo", "")
            put("cortStDvs", "2")
            put("statNum", 1)
            put("bidBgngYmd", startDate.format(DateTimeFormatter.BASIC_ISO_DATE))
            put("bidEndYmd", endDate.format(DateTimeFormatter.BASIC_ISO_DATE))
            put("dspslDxdyYmd", "")
            put("fstDspslHm", "")
            put("scndDspslHm", "")
            put("thrdDspslHm", "")
            put("fothDspslHm", "")
            put("dspslPlcNm", "")
            put("lwsDspslPrcMin", "")
            put("lwsDspslPrcMax", "")
            put("grbxTypCd", "")
            put("gdsVendNm", "")
            put("fuelKndCd", "")
            put("carMdyrMax", "")
            put("carMdyrMin", "")
            put("carMdlNm", "")
            put("sideDvsCd", "")
        })
    }

    private companion object {
        const val SEARCH_PAGE_URL = "https://www.courtauction.go.kr/pgj/index.on?w2xPath=/pgj/ui/pgj100/PGJ151F00.xml"
        const val SEARCH_API_URL = "https://www.courtauction.go.kr/pgj/pgjsearch/searchControllerMain.on"
        const val PAGE_SIZE = 40
        const val SEARCH_DAYS = 90L
        const val SEOUL_CODE = "11"
        const val MAX_TOTAL_PAGES = 50
        const val REQUEST_DELAY_MILLIS = 1_000L
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 20_000
        const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15; SorimPower) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Mobile Safari/537.36"
    }
}

internal fun groupCourtAuctionRows(rows: List<CourtAuctionRawRow>, collectedAt: String): List<AuctionItem> {
    val grouped = linkedMapOf<String, AuctionItem>()
    rows.asSequence()
        .filter(CourtAuctionRawRow::matchesSearchCriteria)
        .forEach { row ->
            val key = row.itemKey.ifBlank {
                if (listOf(row.courtCode, row.internalCaseNumber, row.auctionItemNumber).all(String::isBlank)) ""
                else listOf(row.courtCode, row.internalCaseNumber, row.auctionItemNumber).joinToString("-")
            }
            if (key.isBlank()) return@forEach
            val existing = grouped[key]
            if (existing != null) {
                grouped[key] = existing.copy(objectCount = existing.objectCount + 1)
                return@forEach
            }
            val minimumPrice = row.notifiedMinimumPrice.takeIf { it > 0L } ?: row.minimumPrice
            val minimumPriceRate = if (row.appraisalPrice > 0L) {
                round(minimumPrice.toDouble() / row.appraisalPrice.toDouble() * 1_000.0) / 10.0
            } else {
                0.0
            }
            grouped[key] = AuctionItem(
                itemKey = key,
                courtCode = row.courtCode,
                courtName = row.courtName,
                internalCaseNumber = row.internalCaseNumber,
                caseNumber = row.caseNumber,
                auctionItemNumber = row.auctionItemNumber,
                usageName = row.usageName,
                appraisalPrice = row.appraisalPrice,
                minimumPrice = minimumPrice,
                minimumPriceRate = minimumPriceRate,
                failedCount = row.failedCount,
                auctionDate = normalizeCourtDate(row.auctionDate),
                auctionTime = normalizeCourtTime(row.auctionTime),
                auctionPlace = row.auctionPlace,
                address = row.address,
                sido = row.sido.ifBlank { if (row.representativeSidoCode == "11" || row.searchSidoCode == "11") "서울특별시" else "" },
                sigungu = row.sigungu,
                dong = row.dong,
                buildingName = row.buildingName,
                courtDepartment = row.courtDepartment,
                courtTel = row.courtTel,
                note = row.note,
                interestCount = row.interestCount,
                isInProgress = row.isInProgress,
                objectCount = 1,
                collectedAt = collectedAt,
            )
        }
    return grouped.values.sortedWith(
        compareBy<AuctionItem> { it.auctionDate.isBlank() }
            .thenBy(AuctionItem::auctionDate)
            .thenBy(AuctionItem::auctionTime),
    )
}

private fun CourtAuctionRawRow.matchesSearchCriteria(): Boolean =
    (sido == "서울특별시" || representativeSidoCode == "11" || searchSidoCode == "11") &&
        usageName == "아파트" &&
        appraisalPrice >= MINIMUM_APPRAISAL_PRICE &&
        isInProgress

private fun normalizeCourtDate(value: String): String {
    val digits = value.trim().replace("-", "")
    return if (digits.matches(Regex("\\d{8}"))) {
        "${digits.substring(0, 4)}-${digits.substring(4, 6)}-${digits.substring(6, 8)}"
    } else {
        value.trim()
    }
}

private fun normalizeCourtTime(value: String): String {
    val digits = value.trim().replace(":", "")
    return if (digits.matches(Regex("\\d{4}"))) "${digits.substring(0, 2)}:${digits.substring(2, 4)}" else value.trim()
}

private fun JSONArray.toRawRows(): List<CourtAuctionRawRow> = buildList {
    for (index in 0 until length()) {
        val row = optJSONObject(index) ?: continue
        add(
            CourtAuctionRawRow(
                itemKey = row.stringValue("groupmaemulser"),
                courtCode = row.stringValue("boCd"),
                courtName = row.stringValue("jiwonNm"),
                internalCaseNumber = row.stringValue("saNo"),
                caseNumber = row.stringValue("srnSaNo"),
                auctionItemNumber = row.stringValue("maemulSer"),
                usageName = row.stringValue("dspslUsgNm"),
                appraisalPrice = row.longValue("gamevalAmt"),
                notifiedMinimumPrice = row.longValue("notifyMinmaePrice1"),
                minimumPrice = row.longValue("minmaePrice"),
                failedCount = row.intValue("yuchalCnt"),
                auctionDate = row.stringValue("maeGiil"),
                auctionTime = row.stringValue("maeHh1"),
                auctionPlace = row.stringValue("maePlace"),
                address = row.stringValue("printSt"),
                sido = row.stringValue("hjguSido"),
                representativeSidoCode = row.stringValue("daepyoSidoCd"),
                searchSidoCode = row.stringValue("srchHjguSidoCd"),
                sigungu = row.stringValue("hjguSigu"),
                dong = row.stringValue("hjguDong"),
                buildingName = row.stringValue("buldNm"),
                courtDepartment = row.stringValue("jpDeptNm"),
                courtTel = row.stringValue("tel"),
                note = row.stringValue("mulBigo"),
                interestCount = row.intValue("gwansMulRegCnt"),
                isInProgress = row.stringValue("mulJinYn") == "Y",
            ),
        )
    }
}

private fun JSONObject.stringValue(key: String): String = if (isNull(key)) "" else optString(key, "").trim()

private fun JSONObject.longValue(key: String): Long = when (val value = opt(key)) {
    is Number -> value.toLong()
    else -> value?.toString()?.replace(",", "")?.trim()?.toDoubleOrNull()?.toLong() ?: 0L
}

private fun JSONObject.intValue(key: String): Int = longValue(key).toInt()

private val SEOUL_ZONE_ID: ZoneId = ZoneId.of("Asia/Seoul")
