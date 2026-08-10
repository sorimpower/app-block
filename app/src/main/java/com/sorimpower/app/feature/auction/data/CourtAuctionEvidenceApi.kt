package com.sorimpower.app.feature.auction.data

import com.sorimpower.app.feature.auction.domain.AuctionDocumentType
import com.sorimpower.app.feature.auction.domain.AuctionEvidenceBundle
import com.sorimpower.app.feature.auction.domain.AuctionEvidenceDocument
import com.sorimpower.app.feature.auction.domain.AuctionItem
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

internal interface AuctionEvidenceSource {
    suspend fun fetch(item: AuctionItem): AuctionEvidenceBundle
}

/** Collects the public evidence exposed by the Court Auction WebSquare screens. */
internal class CourtAuctionEvidenceApi : AuctionEvidenceSource {
    override suspend fun fetch(item: AuctionItem): AuctionEvidenceBundle {
        require(item.courtCode.isNotBlank() && item.caseNumber.isNotBlank()) {
            "법원 코드와 사건번호가 없어 법원 문서를 조회할 수 없습니다."
        }

        val caseData = requestCaseDetail(item)
        val caseBase = caseData.optJSONObject("dma_csBasInf") ?: JSONObject()
        val courtItems = caseData.optJSONArray("dlt_dspslGdsDspslObjctLst") ?: JSONArray()
        val courtItem = courtItems.findItem(item.auctionItemNumber)
            ?: throw IOException("법원 사건 상세에서 해당 물건번호를 찾지 못했습니다.")
        val safeCourtItem = courtItem.copyWithout("orvParam", "orvParam1")
        val internalCaseNumber = caseBase.optString("csNo").ifBlank { item.internalCaseNumber }

        val documents = mutableListOf(
            AuctionEvidenceDocument(
                type = AuctionDocumentType.CASE_DETAIL,
                sourceId = internalCaseNumber,
                text = JSONObject().apply {
                    put("안내", "대한민국 법원 경매정보 사건상세 API 원문 데이터")
                    put("사건기본정보", caseBase)
                    put("대상물건", safeCourtItem)
                    put("매각목적물", caseData.optJSONArray("dlt_rletCsDspslObjctLst") ?: JSONArray())
                    put("배당요구종기", caseData.optJSONArray("dlt_dstrtDemnLstprdDts") ?: JSONArray())
                    put("이해관계인", caseData.optJSONArray("dlt_rletCsIntrpsLst") ?: JSONArray())
                    put("제시외건물", caseData.optJSONArray("dlt_rletCsSugtExclBldLst") ?: JSONArray())
                    put("기일내역", caseData.optJSONArray("dlt_rletCsGdsDtsDxdyInf") ?: JSONArray())
                }.toString(),
            ),
        )

        runCatching { fetchSaleSpecificationMetadata(item, internalCaseNumber, courtItem) }
            .getOrNull()?.let(documents::add)
        runCatching { fetchOccupancyReport(item) }.getOrNull()?.let(documents::add)
        runCatching { fetchAppraisalReport(item) }.getOrNull()?.let(documents::add)

        return AuctionEvidenceBundle(itemKey = item.itemKey, documents = documents)
    }

    private fun requestCaseDetail(item: AuctionItem): JSONObject = post(
        url = CASE_DETAIL_API_URL,
        programId = "PGJ15AF01",
        submissionId = "mf_wfm_mainFrame_sbm_selectCsDtlInf",
        referer = CASE_SEARCH_PAGE_URL,
        body = JSONObject().put(
            "dma_srchCsDtlInf",
            JSONObject().apply {
                put("cortOfcCd", item.courtCode)
                put("csNo", item.caseNumber)
            },
        ),
    ).getJSONObject("data")

    private fun fetchOccupancyReport(item: AuctionItem): AuctionEvidenceDocument? {
        val data = post(
            url = OCCUPANCY_API_URL,
            programId = "PGJ15BP01",
            submissionId = "sbm_selectCurstExmn",
            referer = CASE_SEARCH_PAGE_URL,
            body = JSONObject().put(
                "dma_srchCurstExmn",
                JSONObject().apply {
                    put("cortOfcCd", item.courtCode)
                    put("csNo", item.caseNumber)
                    put("auctnInfOriginDvsCd", "2")
                    put("ordTsCnt", "")
                },
            ),
        ).getJSONObject("data")
        if ((data.optJSONArray("dlt_spotExmnOrdCntLst")?.length() ?: 0) == 0) return null
        return AuctionEvidenceDocument(
            type = AuctionDocumentType.OCCUPANCY_REPORT,
            sourceId = "${item.courtCode}:${item.caseNumber}:occupancy",
            text = JSONObject().apply {
                put("안내", "대한민국 법원 경매정보 현황조사서 API 원문 데이터")
                put("조사관리정보", data.optJSONObject("dma_curstExmnMngInf") ?: JSONObject())
                put("점유관계", data.optJSONArray("dlt_ordTsRlet") ?: JSONArray())
                put("임대차관계", data.optJSONArray("dlt_ordTsLserLtn") ?: JSONArray())
            }.toString(),
        )
    }

    private fun fetchAppraisalReport(item: AuctionItem): AuctionEvidenceDocument? {
        val data = post(
            url = APPRAISAL_API_URL,
            programId = "PGJ15BP03",
            submissionId = "sbm_selectAeeWevlInfo",
            referer = CASE_SEARCH_PAGE_URL,
            body = JSONObject().put(
                "dma_srchAeeWevl",
                JSONObject().apply {
                    put("cortOfcCd", item.courtCode)
                    put("cortSptNm", item.courtName)
                    put("csNo", item.caseNumber)
                    put("auctnInfOriginDvsCd", "4")
                    put("dspslDxdyYmd", item.auctionDate.replace("-", ""))
                    put("pgmId", "PGJ15BP03")
                    put("ordTsCnt", "")
                },
            ),
        ).getJSONObject("data")
        if ((data.optJSONArray("dlt_aeeWevlOrdTsLst")?.length() ?: 0) == 0) return null
        val info = data.optJSONObject("dma_ordTsIndvdAeeWevlInf") ?: JSONObject()
        return AuctionEvidenceDocument(
            type = AuctionDocumentType.APPRAISAL_REPORT,
            sourceId = info.optString("aeeWevlNo"),
            text = JSONObject().apply {
                put("안내", "대한민국 법원 경매정보 감정평가서 API 제공 요약 데이터이며 PDF 원문은 아님")
                put("감정평가정보", info)
                put("감정평가회차", data.optJSONArray("dlt_aeeWevlOrdTsLst") ?: JSONArray())
            }.toString(),
        )
    }

    private fun fetchSaleSpecificationMetadata(
        item: AuctionItem,
        internalCaseNumber: String,
        courtItem: JSONObject,
    ): AuctionEvidenceDocument? {
        val documentId = courtItem.optString("dspslGdsSpcfcEcdocId")
        val orvParam = courtItem.optString("orvParam")
        if (documentId.isBlank() || orvParam.isBlank()) return null

        val logData = post(
            url = SALE_SPECIFICATION_LOG_API_URL,
            programId = "PGJ15AF01",
            submissionId = "mf_wfm_mainFrame_sbm_dspslSpcfcViewOpen",
            referer = CASE_SEARCH_PAGE_URL,
            body = JSONObject().put(
                "dma_dspslGdsSpecLog",
                JSONObject().apply {
                    put("cortOfcCd", item.courtCode)
                    put("csNo", internalCaseNumber)
                    put("dspslGdsSeq", item.auctionItemNumber)
                    put("orvParam", orvParam)
                    put("dspslGdsSpcfcEcdocId", documentId)
                    put("cortAuctnMbrsId", "NONUSER")
                    put("docFlag", "1")
                },
            ),
        ).getJSONObject("data").optJSONObject("dma_dspslSpcfcInfo") ?: return null
        val encryptedParameter = logData.optString("encParam")
        if (encryptedParameter.isBlank()) return null

        val viewerData = post(
            url = DOCUMENT_VIEWER_API_URL,
            programId = "SGVO201",
            submissionId = "sbm_docVwr",
            referer = DOCUMENT_VIEWER_PAGE_URL,
            body = JSONObject().put(
                "dma_parm",
                JSONObject().apply {
                    put("encParam", encryptedParameter)
                    put("sidParam", "NA")
                },
            ),
        ).getJSONObject("data")
        val viewerDocuments = viewerData.optJSONArray("dlt_rcrd") ?: JSONArray()
        if (viewerDocuments.length() == 0) return null
        return AuctionEvidenceDocument(
            type = AuctionDocumentType.SALE_SPECIFICATION,
            sourceId = documentId,
            text = JSONObject().apply {
                put("안내", "매각물건명세서 존재와 문서 메타데이터를 확인했으며, 아래 물건비고는 법원 사건상세 API 제공 내용임. PDF 원문 텍스트는 아님")
                put("물건비고", courtItem.optString("dspslGdsRmk"))
                put("대상물건", courtItem.copyWithout("orvParam", "orvParam1"))
                put("문서정보", viewerDocuments)
                put(
                    "사건정보",
                    (viewerData.optJSONObject("dma_csInf") ?: JSONObject()).copyWithout("ticket", "encUserId"),
                )
            }.toString(),
        )
    }

    private fun post(
        url: String,
        programId: String,
        submissionId: String,
        referer: String,
        body: JSONObject,
    ): JSONObject {
        val bytes = body.toString().toByteArray(Charsets.UTF_8)
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            doOutput = true
            setFixedLengthStreamingMode(bytes.size)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json;charset=UTF-8")
            setRequestProperty("User-Agent", MOBILE_USER_AGENT)
            setRequestProperty("SC-Userid", "NONUSER")
            setRequestProperty("SC-Pgmid", programId)
            setRequestProperty("submissionid", submissionId)
            setRequestProperty("Referer", referer)
        }
        return try {
            connection.outputStream.use { it.write(bytes) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IOException("법원 문서 API HTTP 오류: $status")
            val root = runCatching { JSONObject(response) }
                .getOrElse { throw IOException("법원 문서 API가 JSON이 아닌 응답을 반환했습니다.") }
            if (root.optInt("status") != 200) {
                throw IOException(root.optString("message").ifBlank { "법원 문서를 조회하지 못했습니다." })
            }
            val data = root.optJSONObject("data") ?: throw IOException("법원 문서 응답 데이터가 없습니다.")
            if (data.has("ipcheck") && !data.optBoolean("ipcheck", false)) {
                throw IOException("법원 사이트의 자동 요청 확인을 통과하지 못했습니다.")
            }
            root
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONArray.findItem(itemNumber: String): JSONObject? {
        val target = itemNumber.toIntOrNull()
        for (index in 0 until length()) {
            val value = optJSONObject(index) ?: continue
            if (value.optInt("dspslGdsSeq", -1) == target || value.optString("dspslGdsSeq") == itemNumber) return value
        }
        return null
    }

    private fun JSONObject.copyWithout(vararg keys: String): JSONObject = JSONObject(toString()).apply {
        keys.forEach(::remove)
    }

    private companion object {
        const val CASE_SEARCH_PAGE_URL =
            "https://www.courtauction.go.kr/pgj/index.on?w2xPath=/pgj/ui/pgj100/PGJ159M00.xml"
        const val CASE_DETAIL_API_URL = "https://www.courtauction.go.kr/pgj/pgj15A/selectAuctnCsSrchRslt.on"
        const val OCCUPANCY_API_URL = "https://www.courtauction.go.kr/pgj/pgj15B/selectCurstExmndc.on"
        const val APPRAISAL_API_URL = "https://www.courtauction.go.kr/pgj/pgj15B/selectAeeWevlInfo.on"
        const val SALE_SPECIFICATION_LOG_API_URL =
            "https://www.courtauction.go.kr/pgj/pgj15B/insertDspslGdsSpecArtcWdrwInf.on"
        const val DOCUMENT_VIEWER_PAGE_URL =
            "https://ecfs.scourt.go.kr/sgvo/websquare/websquare.html?w2xPath=/sgvo/ui/sgvo200/SGVO201M01.xml"
        const val DOCUMENT_VIEWER_API_URL = "https://ecfs.scourt.go.kr/sgvo/sgvomain/selectDocVwrInf.on"
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 25_000
        const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15; SorimPower) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Mobile Safari/537.36"
    }
}
