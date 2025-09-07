package com.gamboo.minbody.service

import com.gamboo.minbody.client.MCenterClient
import com.gamboo.minbody.client.dto.BuffRecordSave
import com.gamboo.minbody.client.dto.BuffRecordSaveRequest
import com.gamboo.minbody.client.dto.PlayerRecordSaveRequest
import com.gamboo.minbody.client.dto.SkillRecordSaveRequest
import com.gamboo.minbody.rest.dto.response.PersonalStatsResponse
import com.gamboo.minbody.service.damage.DamageStatsService
import com.gamboo.minbody.service.damage.RaidModeManager
import jakarta.annotation.PostConstruct
import mu.KotlinLogging
import org.springframework.stereotype.Service

@Service
class PlayerRecordSaveService(
    private val mCenterClient: MCenterClient,
    private val damageStatsService: DamageStatsService,
    private val raidModeManager: RaidModeManager,
    private val webSocketBroadcastService: WebSocketBroadcastService,
    private val loginService: LoginService
) {

    private val logger = KotlinLogging.logger {}

    @PostConstruct
    fun init() {
        // RaidModeManager의 Center 전용 종료 리스너에 save 메서드 등록
        raidModeManager.setOnRaidEndByCenterListener {
            logger.info { "Raid end detected by Center listener, saving player record..." }
            save()
        }
    }


    fun save() {
        val stats = damageStatsService.getPersonalStats()
        val type = getType(stats)
        logger.info { "=========================Type : ${type}=========================" }

        try {
            
            val request = convertRequest(type, stats)
            logger.info { "save -> token : ${loginService.token}" }
            val response = mCenterClient.savePlayerRecord(loginService.token, request)


            // WebSocket으로 결과 브로드캐스트
            webSocketBroadcastService.broadcastRecordSaved(response.data.id)
        } catch (e: Exception) {
            logger.error(e) { "Failed to save player record: ${e.message}" }
            // 401 에러인 경우 토큰 만료 가능성
            if (e.message?.contains("401") == true) {
                logger.warn { "Token might be expired. Please re-login." }
                loginService.logout()
            }
        }
    }

    fun getType(stats: PersonalStatsResponse): String? {
        // 1993441463 : (매어)파멸의 징조
        // 2134346801 : (매어)녹아내린 갑옷

        // 758766087 : (어려움)파멸의 징조
        return when {
            stats.buffs?.castBuffStats?.keys?.contains(1993441463) == true -> "GLAS_VERY_DIFF"
            stats.buffs?.castBuffStats?.keys?.contains(2134346801) == true -> "GLAS_VERY_DIFF"
            stats.buffs?.castBuffStats?.keys?.contains(758766087) == true -> "GLAS_DIFF"
            else -> null
        }
    }

    private fun convertRequest(type: String?, stats: PersonalStatsResponse): PlayerRecordSaveRequest {

        // BOSS 의 데이터만 전달되어야함.

        val targetStats =
            stats.targets.find { it.targetId == stats.bossId } ?: throw RuntimeException("not found boss id")

        return PlayerRecordSaveRequest(
            version = "2.6.7", // 버전 정보는 설정 값으로 관리하는 것이 좋습니다
            targetId = stats.bossId,
            playerId = stats.userId,
            type = type, // 타입 정보도 설정 값으로 관리하는 것이 좋습니다
            player = stats.jobName ?: "Unknown", // jobName을 player로 사용
            partyPlayer = stats.partyPlayers.filter { it.key != stats.userId }.values.toList(),
            duration = targetStats.durationSeconds,
            dps = targetStats.dps.toLong(),
            totalDamage = targetStats.totalDamage,
            skills = targetStats.skills.map { skill ->
                SkillRecordSaveRequest(
                    skillName = skill.skillName,
                    totalDamage = skill.totalDamage,
                    hitCount = skill.hitCount,
                    critCount = skill.critCount,
                    addhitCount = skill.addhitCount,
                    powerCount = skill.powerCount,
                    fastCount = skill.fastCount,
                    dotDamage = skill.dotDamage,
                    dotCount = skill.dotCount
                )
            },
            buffs = BuffRecordSave(
                castBuffs = stats.buffs?.let { buffs ->
                    buffs.castBuffStats.map { (buffId, buffStats) ->
                        BuffRecordSaveRequest(
                            buffId = buffId,
                            totalCount = buffStats.totalCount,
                            totalDuration = buffStats.totalDuration,
                            maxStack = buffStats.maxStack,
                            avgStack = buffStats.avgStack,
                            startCount = buffStats.startCount,
                            updateCount = buffStats.updateCount,
                            refreshCount = buffStats.refreshCount
                        )
                    }
                } ?: emptyList(),
                receivedBuffs = stats.buffs?.let { buffs ->
                    buffs.receivedBuffStats.map { (buffId, buffStats) ->
                        BuffRecordSaveRequest(
                            buffId = buffId,
                            totalCount = buffStats.totalCount,
                            totalDuration = buffStats.totalDuration,
                            maxStack = buffStats.maxStack,
                            avgStack = buffStats.avgStack,
                            startCount = buffStats.startCount,
                            updateCount = buffStats.updateCount,
                            refreshCount = buffStats.refreshCount
                        )
                    }
                } ?: emptyList()
            ),
            hitCount = targetStats.hitCount,
            critCount = targetStats.critCount,
            addhitCount = targetStats.addhitCount,
            powerCount = targetStats.powerCount,
            fastCount = targetStats.fastCount,
            bossSkills = raidModeManager.bossSkillCount
        )
    }
}