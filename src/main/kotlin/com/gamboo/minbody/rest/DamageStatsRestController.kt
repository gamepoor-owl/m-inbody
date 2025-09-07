package com.gamboo.minbody.rest

import com.gamboo.minbody.service.damage.DamageProcessingService
import com.gamboo.minbody.service.damage.DamageStatsService
import mu.KotlinLogging
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/damage")
class DamageStatsRestController(
    private val damageProcessingService: DamageProcessingService,
    private val damageStatsService: DamageStatsService,
) {
    
    private val logger = KotlinLogging.logger {}
    
    @PostMapping("/clear")
    fun clearStats(): Map<String, String> {
        logger.info { "Clearing all damage statistics" }
        damageProcessingService.clearAll()
        return mapOf("status" to "success", "message" to "All damage statistics cleared")
    }
    
    
}