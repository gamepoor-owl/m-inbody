package com.gamboo.minbody.service

import com.gamboo.minbody.client.MCenterClient
import com.gamboo.minbody.client.dto.SkillResponse
import mu.KotlinLogging
import org.springframework.stereotype.Service

@Service
class SkillApiService(
    private val mCenterClient: MCenterClient
) {
    private val logger = KotlinLogging.logger {}

    private var cachedSkillMapping: Map<String, SkillResponse> = emptyMap()

    init {
        cachedSkillMapping = mCenterClient.getAllSkills()
            .data.associateBy { it.skillKey }
    }

    fun getSkillMapping(): Map<String, SkillResponse> {
        return cachedSkillMapping
    }
    

    fun getSkillByKey(skillKey: String): SkillResponse? {
        return cachedSkillMapping[skillKey]
    }
}