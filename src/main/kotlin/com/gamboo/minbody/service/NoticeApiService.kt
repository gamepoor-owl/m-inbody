package com.gamboo.minbody.service

import com.gamboo.minbody.client.MCenterClient
import com.gamboo.minbody.client.dto.NoticeResponse
import mu.KotlinLogging
import org.springframework.stereotype.Service

@Service
class NoticeApiService(
    private val mCenterClient: MCenterClient
) {
    private val logger = KotlinLogging.logger {}

    fun getActiveNoticesByType(type: String): List<NoticeResponse> {
        return try {
            logger.info { "Fetching active notices from m-center for type: $type" }
            mCenterClient.getActiveNoticesByType(type).data
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch notices from m-center for type: $type" }
            emptyList()
        }
    }
}