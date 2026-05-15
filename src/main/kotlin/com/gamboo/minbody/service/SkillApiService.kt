package com.gamboo.minbody.service

import com.gamboo.minbody.client.MCenterClient
import com.gamboo.minbody.client.dto.SkillResponse
import mu.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class SkillApiService(
    private val mCenterClient: MCenterClient
) {
    private val logger = KotlinLogging.logger {}

    @Volatile
    private var cachedSkillMapping: Map<String, SkillResponse> = emptyMap()

    init {
        refreshSkillMapping()
    }

    @Scheduled(fixedDelay = REFRESH_INTERVAL_MS, initialDelay = REFRESH_INTERVAL_MS)
    fun refreshSkillMapping() {
        try {
            val fresh = mCenterClient.getAllSkills().data.associateBy { it.skillKey }
            cachedSkillMapping = fresh
            logger.info { "Loaded skill mappings from m-center: ${fresh.size} entries" }
        } catch (e: Exception) {
            if (cachedSkillMapping.isEmpty()) {
                logger.error(e) { "Failed to load skills from m-center; starting with empty map until next refresh" }
            } else {
                logger.warn(e) { "Failed to refresh skills from m-center; keeping previous ${cachedSkillMapping.size} entries" }
            }
        }
    }

    fun getSkillMapping(): Map<String, SkillResponse> {
        return cachedSkillMapping
    }

    fun getSkillByKey(skillKey: String): SkillResponse? {
        return cachedSkillMapping[skillKey]
    }

    companion object {
        private const val REFRESH_INTERVAL_MS = 10 * 60 * 1000L
    }
}
