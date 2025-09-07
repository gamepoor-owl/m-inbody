package com.gamboo.minbody.service.damage

import com.gamboo.minbody.model.SkillDetail
import com.gamboo.minbody.rest.dto.response.*
import com.gamboo.minbody.service.buff.UserBuffStatisticsService
import mu.KotlinLogging
import org.springframework.stereotype.Service
import kotlin.math.roundToInt

@Service
class DamageStatsService(
    private val damageProcessingService: DamageProcessingService,
    private val userBuffStatisticsService: UserBuffStatisticsService,
    private val raidModeManager: RaidModeManager
) {

    private val logger = KotlinLogging.logger {}
    
    /**
     * 자신의 유저 ID 가져오기
     */
    fun getSelfUserId(): Long {
        return damageProcessingService.selfData.id
    }


    private fun calculateSkillStats(userId: Long): List<SkillStats> {
        val userSkills = damageProcessingService.damageByUserBySkill[userId] ?: return emptyList()
        val userTotalDamage = damageProcessingService.damageStats[userId]?.total ?: 0

        return userSkills.map { (skillName, skillDetail) ->
            val damagePercent = if (userTotalDamage > 0)
                (skillDetail.total.toDouble() / userTotalDamage * 100)
            else 0.0

            // hitCount는 이제 DOT를 포함한 모든 타격 횟수
            val avgDamage = if (skillDetail.hitCount > 0)
                skillDetail.total / skillDetail.hitCount
            else 0L

            // 치명타율 계산 (모든 타격 횟수 기준)
            val critRate = if (skillDetail.hitCount > 0) {
                val rate = (skillDetail.cntCrit.toDouble() / skillDetail.hitCount * 100)
                minOf(rate, 100.0)
            } else 0.0

            val dotAvgDamage = if (skillDetail.cntDot > 0)
                skillDetail.dot / skillDetail.cntDot
            else 0L

            // DOT와 일반 데미지의 최대/최소값 통합
            val combinedMaxDamage = maxOf(
                if (skillDetail.maxDmg == 0L) 0L else skillDetail.maxDmg,
                if (skillDetail.maxDotDmg == 0L) 0L else skillDetail.maxDotDmg
            )
            val combinedMinDamage = minOf(
                if (skillDetail.minDmg == Long.MAX_VALUE) Long.MAX_VALUE else skillDetail.minDmg,
                if (skillDetail.minDotDmg == Long.MAX_VALUE) Long.MAX_VALUE else skillDetail.minDotDmg
            ).let { if (it == Long.MAX_VALUE) 0L else it }

            // 추가타 확률 계산 - Python 프로젝트와 동일한 공식
            val addhitRate = if (skillDetail.hitCount > skillDetail.cntAddhit) {
                val rate = (skillDetail.cntAddhit.toDouble() / (skillDetail.hitCount - skillDetail.cntAddhit) * 100)
                minOf(rate, 100.0)
            } else 0.0

            // 강타율 계산
            val powerRate = if (skillDetail.hitCount > 0) {
                val rate = (skillDetail.cntPower.toDouble() / skillDetail.hitCount * 100)
                minOf(rate, 100.0)
            } else 0.0

            // 연타율 계산
            val fastRate = if (skillDetail.hitCount > 0) {
                val rate = (skillDetail.cntFast.toDouble() / skillDetail.hitCount * 100)
                minOf(rate, 100.0)
            } else 0.0

            SkillStats(
                skillName = skillName,
                totalDamage = skillDetail.total,
                damagePercent = (damagePercent * 100).roundToInt() / 100.0,
                hitCount = skillDetail.hitCount.toLong(),  // 모든 타격 횟수 (DOT 포함)
                damageHitCount = 0L,  // Deprecated
                critCount = skillDetail.cntCrit.toLong(),
                critRate = (critRate * 100).roundToInt() / 100.0,
                addhitCount = skillDetail.cntAddhit.toLong(),
                addhitRate = (addhitRate * 100).roundToInt() / 100.0,
                powerCount = skillDetail.cntPower.toLong(),
                powerRate = (powerRate * 100).roundToInt() / 100.0,
                fastCount = skillDetail.cntFast.toLong(),
                fastRate = (fastRate * 100).roundToInt() / 100.0,
                avgDamage = avgDamage,
                maxDamage = combinedMaxDamage,
                minDamage = combinedMinDamage,
                dotDamage = skillDetail.dot,
                dotCount = skillDetail.cntDot.toLong(),
                dotAvgDamage = dotAvgDamage,
                dotMaxDamage = if (skillDetail.maxDotDmg == 0L) 0L else skillDetail.maxDotDmg,
                dotMinDamage = if (skillDetail.minDotDmg == Long.MAX_VALUE) 0L else skillDetail.minDotDmg
            )
        }.sortedByDescending { it.totalDamage }
    }


    private fun calculateDuration(): Long {
        val timeForDPS = damageProcessingService.timeForDPS
        return if (timeForDPS.start > 0 && timeForDPS.end > 0) {
            // 전투가 시작되었으면 마지막 데미지 시간까지의 경과 시간 계산
            (timeForDPS.end - timeForDPS.start) / 1000
        } else {
            0
        }
    }

    private fun calculateTargetDuration(targetId: Long): Long {
        val targetTime = damageProcessingService.timeForDPS.timeByTarget[targetId]
        return if (targetTime != null && targetTime.start > 0 && targetTime.end > 0) {
            // 타겟 전투가 시작되었으면 마지막 데미지 시간까지의 경과 시간 계산
            (targetTime.end - targetTime.start) / 1000
        } else {
            calculateDuration()
        }
    }

    private fun calculateUserDPS(userId: Long): Double {
        val dpsInfo = damageProcessingService.damageForDPS[userId] ?: return 0.0

        // 전투 시작부터 마지막 데미지까지의 경과 시간
        val duration = calculateDuration()

        // 전체 누적 데미지를 전체 경과 시간으로 나눔
        return if (duration > 0) {
            dpsInfo.total.toDouble() / duration
        } else {
            0.0
        }
    }

    /**
     * 개인 모드 전용 통계 조회 - 특정 유저의 데이터만 반환
     */
    fun getPersonalStats(): PersonalStatsResponse {
        val targetUserId = getSelfUserId()
        val userStats = damageProcessingService.damageStats[targetUserId]
            ?: return createEmptyPersonalStats(targetUserId)

        val duration = calculateDuration()
        val timeForDPS = damageProcessingService.timeForDPS

        // 개인 DPS 계산
        val personalDps = calculateUserDPS(targetUserId)

        // 개인 스킬 통계
        val personalSkills = calculateSkillStats(targetUserId)

        // 개인 타겟별 통계
        val personalTargets = calculatePersonalTargetStats(targetUserId)

        // 자가 데미지
        val selfDamageInfo = damageProcessingService.selfDamageByUser[targetUserId]
        val selfDamageTotal = selfDamageInfo?.total ?: 0

        // DPS 정보
        val dpsInfo = damageProcessingService.damageForDPS[targetUserId]
        val minDps = if (dpsInfo?.minDps == Double.MAX_VALUE) 0.0 else (dpsInfo?.minDps ?: 0.0)
        val maxDps = dpsInfo?.maxDps ?: 0.0

        // 전투 시간 정보를 사용하여 가동률 계산
        val buffsWithUptime = userBuffStatisticsService.getUserBuffStatsWithUptime(
            targetUserId,
            timeForDPS.start,
            timeForDPS.end
        )
        
        return PersonalStatsResponse(
            bossId = raidModeManager.getBossId(),
            partyPlayers = damageProcessingService.jobMapping,
            userId = targetUserId,
            buffs = buffsWithUptime,
            totalDamage = userStats.total,
            dps = (personalDps * 100).roundToInt() / 100.0,
            durationSeconds = duration,
            combatStartTime = timeForDPS.start,
            combatEndTime = timeForDPS.end,
            hitCount = userStats.hitCount.toLong(),
            critCount = userStats.crit.toLong(),
            critRate = if (userStats.hitCount > 0) {
                val rate = (userStats.crit.toDouble() / userStats.hitCount * 100)
                minOf(rate, 100.0).let { ((it * 100).roundToInt() / 100.0) }
            } else 0.0,
            addhitCount = userStats.addhit.toLong(),
            addhitRate = if (userStats.hitCount > userStats.addhit) {
                val rate = (userStats.addhit.toDouble() / (userStats.hitCount - userStats.addhit) * 100)
                minOf(rate, 100.0).let { ((it * 100).roundToInt() / 100.0) }
            } else 0.0,
            powerCount = userStats.power.toLong(),
            powerRate = if (userStats.hitCount > 0) {
                val rate = (userStats.power.toDouble() / userStats.hitCount * 100)
                minOf(rate, 100.0).let { ((it * 100).roundToInt() / 100.0) }
            } else 0.0,
            fastCount = userStats.fast.toLong(),
            fastRate = if (userStats.hitCount > 0) {
                val rate = (userStats.fast.toDouble() / userStats.hitCount * 100)
                minOf(rate, 100.0).let { ((it * 100).roundToInt() / 100.0) }
            } else 0.0,
            minDps = (minDps * 100).roundToInt() / 100.0,
            maxDps = (maxDps * 100).roundToInt() / 100.0,
            skills = personalSkills,
            targets = personalTargets,
            jobName = damageProcessingService.jobMapping[targetUserId],
            selfDamageTotal = selfDamageTotal
        )
    }

    /**
     * 개인 타겟별 통계 계산
     */
    private fun calculatePersonalTargetStats(userId: Long): List<PersonalTargetStats> {
        return damageProcessingService.damageByTarget.mapNotNull targetLoop@{ (targetId, targetInfo) ->
            val userDamageToTarget = targetInfo.byUser[userId] ?: return@targetLoop null

            val targetDuration = calculateTargetDuration(targetId)
            val targetDps = if (targetDuration > 0) userDamageToTarget.total.toDouble() / targetDuration else 0.0

            // 해당 유저의 전체 데미지 대비 이 타겟에 대한 데미지 비율
            val userTotalDamage = damageProcessingService.damageStats[userId]?.total ?: 0
            val damagePercent = if (userTotalDamage > 0)
                (userDamageToTarget.total.toDouble() / userTotalDamage * 100) else 0.0

            // 치명타 통계
            val critRate = if (userDamageToTarget.hitCount > 0) {
                val rate = (userDamageToTarget.crit.toDouble() / userDamageToTarget.hitCount * 100)
                minOf(rate, 100.0)
            } else 0.0

            // 추가타 통계
            val addhitRate = if (userDamageToTarget.hitCount > userDamageToTarget.addhit) {
                val rate = (userDamageToTarget.addhit.toDouble() / 
                           (userDamageToTarget.hitCount - userDamageToTarget.addhit) * 100)
                minOf(rate, 100.0)
            } else 0.0

            // 강타 통계
            val powerRate = if (userDamageToTarget.hitCount > 0) {
                val rate = (userDamageToTarget.power.toDouble() / userDamageToTarget.hitCount * 100)
                minOf(rate, 100.0)
            } else 0.0

            // 연타 통계
            val fastRate = if (userDamageToTarget.hitCount > 0) {
                val rate = (userDamageToTarget.fast.toDouble() / userDamageToTarget.hitCount * 100)
                minOf(rate, 100.0)
            } else 0.0

            // 타겟별 DPS 범위 계산
            val targetDpsRange = calculateTargetDpsRange(userId, targetId, targetDuration)

            // 해당 타겟에 대한 스킬별 통계
            val targetSkills =
                damageProcessingService.damageByUserBySkill[userId]?.mapNotNull skillLoop@{ (skillName, skillDetail) ->
                    val targetSkillDetail = skillDetail.byTarget[targetId] ?: return@skillLoop null
                    createSkillStatsFromDetail(skillName, targetSkillDetail, userDamageToTarget.total)
                } ?: emptyList()

            val targetTime = damageProcessingService.timeForDPS.timeByTarget[targetId]
            
            PersonalTargetStats(
                targetId = targetId,
                totalDamage = userDamageToTarget.total,
                damagePercent = (damagePercent * 100).roundToInt() / 100.0,
                dps = (targetDps * 100).roundToInt() / 100.0,
                durationSeconds = targetDuration,
                targetStartTime = targetTime?.start ?: 0,
                targetEndTime = targetTime?.end ?: 0,
                hitCount = userDamageToTarget.hitCount.toLong(),
                
                // 새로 추가된 필드들
                critCount = userDamageToTarget.crit.toLong(),
                critRate = (critRate * 100).roundToInt() / 100.0,
                addhitCount = userDamageToTarget.addhit.toLong(),
                addhitRate = (addhitRate * 100).roundToInt() / 100.0,
                powerCount = userDamageToTarget.power.toLong(),
                powerRate = (powerRate * 100).roundToInt() / 100.0,
                fastCount = userDamageToTarget.fast.toLong(),
                fastRate = (fastRate * 100).roundToInt() / 100.0,
                minDps = targetDpsRange.first,
                maxDps = targetDpsRange.second,
                
                skills = targetSkills.sortedByDescending { it.totalDamage }
            )
        }.sortedByDescending { it.totalDamage }
    }

    /**
     * 스킬 상세 정보를 SkillStats로 변환
     */
    private fun createSkillStatsFromDetail(
        skillName: String,
        skillDetail: SkillDetail,
        totalDamage: Long
    ): SkillStats {
        val damagePercent = if (totalDamage > 0)
            (skillDetail.total.toDouble() / totalDamage * 100)
        else 0.0

        val avgDamage = if (skillDetail.hitCount > 0)
            skillDetail.total / skillDetail.hitCount
        else 0L

        val critRate = if (skillDetail.hitCount > 0) {
            val rate = (skillDetail.cntCrit.toDouble() / skillDetail.hitCount * 100)
            minOf(rate, 100.0)
        } else 0.0

        val dotAvgDamage = if (skillDetail.cntDot > 0)
            skillDetail.dot / skillDetail.cntDot
        else 0L

        val combinedMaxDamage = maxOf(
            if (skillDetail.maxDmg == 0L) 0L else skillDetail.maxDmg,
            if (skillDetail.maxDotDmg == 0L) 0L else skillDetail.maxDotDmg
        )
        val combinedMinDamage = minOf(
            if (skillDetail.minDmg == Long.MAX_VALUE) Long.MAX_VALUE else skillDetail.minDmg,
            if (skillDetail.minDotDmg == Long.MAX_VALUE) Long.MAX_VALUE else skillDetail.minDotDmg
        ).let { if (it == Long.MAX_VALUE) 0L else it }

        val addhitRate = if (skillDetail.hitCount > skillDetail.cntAddhit) {
            val rate = (skillDetail.cntAddhit.toDouble() / (skillDetail.hitCount - skillDetail.cntAddhit) * 100)
            minOf(rate, 100.0)
        } else 0.0

        val powerRate = if (skillDetail.hitCount > 0) {
            val rate = (skillDetail.cntPower.toDouble() / skillDetail.hitCount * 100)
            minOf(rate, 100.0)
        } else 0.0

        val fastRate = if (skillDetail.hitCount > 0) {
            val rate = (skillDetail.cntFast.toDouble() / skillDetail.hitCount * 100)
            minOf(rate, 100.0)
        } else 0.0

        return SkillStats(
            skillName = skillName,
            totalDamage = skillDetail.total,
            damagePercent = (damagePercent * 100).roundToInt() / 100.0,
            hitCount = skillDetail.hitCount.toLong(),
            damageHitCount = 0L,
            critCount = skillDetail.cntCrit.toLong(),
            critRate = (critRate * 100).roundToInt() / 100.0,
            addhitCount = skillDetail.cntAddhit.toLong(),
            addhitRate = (addhitRate * 100).roundToInt() / 100.0,
            powerCount = skillDetail.cntPower.toLong(),
            powerRate = (powerRate * 100).roundToInt() / 100.0,
            fastCount = skillDetail.cntFast.toLong(),
            fastRate = (fastRate * 100).roundToInt() / 100.0,
            avgDamage = avgDamage,
            maxDamage = combinedMaxDamage,
            minDamage = combinedMinDamage,
            dotDamage = skillDetail.dot,
            dotCount = skillDetail.cntDot.toLong(),
            dotAvgDamage = dotAvgDamage,
            dotMaxDamage = if (skillDetail.maxDotDmg == 0L) 0L else skillDetail.maxDotDmg,
            dotMinDamage = if (skillDetail.minDotDmg == Long.MAX_VALUE) 0L else skillDetail.minDotDmg
        )
    }

    /**
     * 타겟별 DPS 범위 계산
     * 현재는 단순 계산값 반환, 추후 타겟별 DPS 추적 로직 추가 가능
     */
    private fun calculateTargetDpsRange(
        userId: Long,
        targetId: Long,
        duration: Long
    ): Pair<Double, Double> {
        // 타겟별 DPS 정보가 없으면 기본값 반환
        val targetDamage = damageProcessingService.damageByTarget[targetId]
            ?.byUser?.get(userId)?.total ?: 0L
        
        if (duration <= 0 || targetDamage == 0L) {
            return 0.0 to 0.0
        }
        
        // 기본 DPS 계산
        val currentDps = targetDamage.toDouble() / duration
        
        // 현재는 단순 계산값 반환
        // 추후 DamageProcessingService에서 타겟별 DPS 추적 로직 추가 시
        // 실제 최소/최대 DPS 값 반환 가능
        val roundedDps = (currentDps * 100).roundToInt() / 100.0
        return roundedDps to roundedDps
    }

    /**
     * 빈 개인 통계 응답 생성
     */
    private fun createEmptyPersonalStats(userId: Long): PersonalStatsResponse {
        // 전투 시간 정보를 사용하여 가동률 계산 (전투가 시작되지 않았더라도)
        val buffsWithUptime = userBuffStatisticsService.getUserBuffStatsWithUptime(
            userId,
            damageProcessingService.timeForDPS.start,
            damageProcessingService.timeForDPS.end
        )
        
        return PersonalStatsResponse(
            userId = userId,
            buffs = buffsWithUptime,
            jobName = damageProcessingService.jobMapping[userId],
            bossId = raidModeManager.getBossId()
        )
    }
}