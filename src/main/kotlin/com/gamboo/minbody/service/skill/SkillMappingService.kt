package com.gamboo.minbody.service.skill

import com.gamboo.minbody.client.dto.SkillResponse
import com.gamboo.minbody.service.SkillApiService
import mu.KotlinLogging
import org.springframework.stereotype.Service
import jakarta.annotation.PostConstruct

@Service
class SkillMappingService(
    private val skillApiService: SkillApiService
) {
    
    private val logger = KotlinLogging.logger {}
    
    private var skillMapping: Map<String, SkillResponse> = emptyMap()
    private val skillKey2Name = mutableMapOf<String, String>()
    
    @PostConstruct
    fun init() {
        loadSkillMappings()
        initializeDotSkillNames()
    }
    
    private fun loadSkillMappings() {
        try {
            skillMapping = skillApiService.getSkillMapping()
            logger.info { "Loaded skill mappings from m-center: ${skillMapping.size} entries" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to load skill mappings from m-center, using empty map" }
            skillMapping = emptyMap()
        }
    }
    
    private fun initializeDotSkillNames() {
        val dotTypes = listOf("(도트)", "(특수)")
        val dotNames = listOf(
            "출혈", "암흑", "화상", "신성", 
            "빙결", "감전", "중독", "정신", "무속성"
        )
        
        dotTypes.forEach { dotType ->
            dotNames.forEach { dotName ->
                val key = "$dotType $dotName"
                skillKey2Name[key] = key
            }
        }
    }
    
    fun getSkillMapping(): Map<String, SkillResponse> = skillMapping
    
    fun getSkillName(key: Long): String? {
        val keyStr = key.toString()
        return skillMapping[keyStr]?.skillName ?: skillKey2Name[keyStr]
    }
    
    fun getSkillNameFromAction(actionName: String): String? {
        return skillMapping[actionName]?.skillName
    }
    
    fun isSkillIgnored(actionName: String): Boolean {
        // m-center API에서 isExclude가 true인 경우 처리
        val skill = skillApiService.getSkillByKey(actionName)
        return skill?.isExclude ?: false
    }
}