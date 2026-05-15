package com.gamboo.minbody.service.skill

import com.gamboo.minbody.client.dto.SkillResponse
import com.gamboo.minbody.service.SkillApiService
import mu.KotlinLogging
import org.springframework.stereotype.Service

@Service
class SkillMappingService(
    private val skillApiService: SkillApiService
) {

    private val logger = KotlinLogging.logger {}

    private val skillKey2Name: Map<String, String> = buildDotSkillNames()

    private fun buildDotSkillNames(): Map<String, String> {
        val dotTypes = listOf("(도트)", "(특수)")
        val dotNames = listOf(
            "출혈", "암흑", "화상", "신성",
            "빙결", "감전", "중독", "정신", "무속성"
        )
        return buildMap {
            dotTypes.forEach { dotType ->
                dotNames.forEach { dotName ->
                    val key = "$dotType $dotName"
                    put(key, key)
                }
            }
        }
    }

    fun getSkillMapping(): Map<String, SkillResponse> = skillApiService.getSkillMapping()

    fun getSkillName(key: Long): String? {
        val keyStr = key.toString()
        return skillApiService.getSkillByKey(keyStr)?.skillName ?: skillKey2Name[keyStr]
    }

    fun getSkillNameFromAction(actionName: String): String? {
        return skillApiService.getSkillByKey(actionName)?.skillName
    }

    fun isSkillIgnored(actionName: String): Boolean {
        return skillApiService.getSkillByKey(actionName)?.isExclude ?: false
    }
}
