package com.gamboo.minbody.service.damage

import com.gamboo.minbody.model.DamageStats
import com.gamboo.minbody.model.SkillDetail
import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * 데미지 통계 계산을 담당하는 컴포넌트
 *
 * 주요 기능:
 * 1. 유저별, 타겟별, 스킬별 데미지 통계 집계
 * 2. DPS 계산 및 추적
 * 3. 치명타, 추가타, DOT 통계 처리
 */
@Component
class DamageStatisticsCalculator {

    private val logger = KotlinLogging.logger {}

    /** 60초 타임아웃 시 호출될 콜백 */
    var onCombatTimeout: (() -> Unit)? = null

    // ========== 전역 데미지 통계 데이터 구조 ==========

    /** 유저별 전체 데미지 통계 (userId -> DamageStats) */
    val damageStats = ConcurrentHashMap<Long, DamageStats>()

    /** 타겟별 데미지 통계 및 유저별 세부 정보 (targetId -> TargetDamageInfo) */
    val damageByTarget = ConcurrentHashMap<Long, TargetDamageInfo>()

    /** 유저별 스킬별 데미지 통계 (userId -> Map<skillName, SkillDetail>) */
    val damageByUserBySkill = ConcurrentHashMap<Long, MutableMap<String, SkillDetail>>()

    /** 유저별 자가 데미지 통계 (userId -> SelfDamageInfo) */
    val selfDamageByUser = ConcurrentHashMap<Long, SelfDamageInfo>()

    /** DPS 계산을 위한 유저별 데이터 (userId -> DPSInfo) */
    val damageForDPS = ConcurrentHashMap<Long, DPSInfo>()

    /** 자가 데미지가 가장 높은 유저 정보 */
    val selfData = SelfData()

    /** DPS 계산을 위한 시간 추적 데이터 */
    val timeForDPS = TimeForDPS()

    /**
     * 데미지 정보를 처리하여 각종 통계에 반영
     * Python과 동일한 DOT 판정 로직 적용: (dot1 && dot2 && dot3) || dot4
     */
    fun processDamage(
        damage: Long,
        userId: Long,
        targetId: Long,
        flags: Map<String, Boolean>,
        skillName: String
    ) {
        // Input validation and safety checks
        if (damage <= 0 || userId <= 0 || targetId <= 0) return
        // ========== 플래그 분석 (Python과 동일한 로직) ==========
        val isCrit = flags["crit_flag"] == true
        val isAddhit = flags["add_hit_flag"] == true
        val isDot = (flags["dot_flag"] == true && flags["dot_flag2"] == true &&
                flags["dot_flag3"] == true) || flags["dot_flag4"] == true
        val isPower = flags["power_flag"] == true
        val isFast = flags["fast_flag"] == true


        // ========== 1. 전역 데미지 통계 업데이트 ==========
        val userStats = damageStats.computeIfAbsent(userId) {
            DamageStats() 
        }

        // Prevent overflow on user stats
        if (userStats.total > Long.MAX_VALUE - damage) return
        
        val beforeDamage = userStats.total
        userStats.total += damage

        // Python과 동일: DOT가 아닌 경우에만 타격 횟수 카운트
        if (!isDot) {
            userStats.hitCount++
            if (isCrit) userStats.crit++
            if (isAddhit) userStats.addhit++
            if (isPower) userStats.power++
            if (isFast) userStats.fast++
        }

        // ========== 2. 타겟별 데미지 통계 업데이트 ==========
        val targetInfo = damageByTarget.computeIfAbsent(targetId) { TargetDamageInfo() }

        // Prevent overflow on target damage
        if (targetInfo.total > Long.MAX_VALUE - damage) return
        targetInfo.total += damage

        // 타겟별 유저 통계
        val targetUserStats = targetInfo.byUser.computeIfAbsent(userId) { DamageStats() }

        // Prevent overflow on target user stats
        if (targetUserStats.total > Long.MAX_VALUE - damage) return
        targetUserStats.total += damage

        // Python과 동일: DOT가 아닌 경우에만 타격 횟수 카운트
        if (!isDot) {
            targetUserStats.hitCount++
            if (isCrit) targetUserStats.crit++
            if (isAddhit) targetUserStats.addhit++
            if (isPower) targetUserStats.power++
            if (isFast) targetUserStats.fast++
        }

        // ========== 3. DPS 추적 업데이트 ==========
        updateDPSTracking(userId, targetId, damage)

        // ========== 4. 스킬별 데미지 통계 업데이트 ==========
//        updateSkillDamage(userId, targetId, damage, skillNameDetail, isCrit, isAddhit, isDot, isPower, isFast)
        updateSkillDamage(userId, targetId, damage, skillName, isCrit, isAddhit, isDot, isPower, isFast)
    }

    /**
     * 자가 데미지 처리
     */
    fun processSelfDamage(userId: Long, targetId: Long, damage: Long) {
        // Input validation and safety checks
        if (damage <= 0 || damage > 2095071572 || userId <= 0) return

        val userInfo = selfDamageByUser.computeIfAbsent(userId) {
            SelfDamageInfo(userId)
        }

        // Prevent overflow
        if (userInfo.total > Long.MAX_VALUE - damage) return

//        val beforeSelfDamage = userInfo.total
        userInfo.total += damage
//        logger.info { "[SELF DAMAGE] User $userId self damage: $beforeSelfDamage -> ${userInfo.total} (+$damage)" }

        if (selfData.total < userInfo.total) {
            selfData.id = userId
            selfData.total = userInfo.total
        }
    }

    /**
     * 스킬별 데미지 통계 업데이트
     */
    private fun updateSkillDamage(
        userId: Long,
        targetId: Long,
        damage: Long,
        skillName: String,
        isCrit: Boolean,
        isAddhit: Boolean,
        isDot: Boolean,
        isPower: Boolean,
        isFast: Boolean
    ) {
        val userSkills = damageByUserBySkill.computeIfAbsent(userId) { ConcurrentHashMap() }

        // 유저별 스킬 전체 통계
        val skillDetail = userSkills.computeIfAbsent(skillName) { SkillDetail() }

        // 유저별 스킬의 타겟별 통계  
        val targetDetail = skillDetail.byTarget.computeIfAbsent(targetId) { SkillDetail() }

        // 전체 통계와 타겟별 통계 모두 업데이트
        listOf(skillDetail, targetDetail).forEach { detail ->
            detail.total += damage

            if (isDot) {
                // ========== DOT 데미지 통계 ==========
                detail.dot += damage
                detail.cntDot++
                detail.maxDotDmg = maxOf(detail.maxDotDmg, damage)
                detail.minDotDmg = if (detail.minDotDmg == Long.MAX_VALUE) damage else minOf(detail.minDotDmg, damage)
            } else {
                // ========== 일반 데미지 통계 (Python과 동일하게 DOT가 아닌 경우에만) ==========
                detail.hitCount++
                detail.maxDmg = maxOf(detail.maxDmg, damage)
                detail.minDmg = if (detail.minDmg == Long.MAX_VALUE) damage else minOf(detail.minDmg, damage)
                if (isCrit) detail.cntCrit++
                if (isAddhit) detail.cntAddhit++
                if (isPower) detail.cntPower++
                if (isFast) detail.cntFast++
            }
        }
    }

    /**
     * DPS 추적 업데이트
     */
    private fun updateDPSTracking(userId: Long, targetId: Long, damage: Long) {
        val now = System.currentTimeMillis()

        // Reset all combat data if more than 60 seconds have passed
        if (now - timeForDPS.end > 60000) {
            logger.info { "Combat timeout: Resetting all damage statistics after 60 seconds of inactivity" }

            // Clear all combat statistics
            damageStats.clear()
            damageByTarget.clear()
            damageByUserBySkill.clear()
            selfDamageByUser.clear()

            // Clear DPS data
            timeForDPS.start = now
            timeForDPS.end = 0
            timeForDPS.timeByTarget.clear()
            damageForDPS.clear()


            selfData.total = 0
            selfData.id = 0

            // Call timeout callback (e.g., to clear jobMapping)
            onCombatTimeout?.invoke()
        }

        timeForDPS.end = now

        val targetTime = timeForDPS.timeByTarget.computeIfAbsent(targetId) {
            TargetTime(now, now)
        }
        targetTime.end = now

        val dpsInfo = damageForDPS.computeIfAbsent(userId) { DPSInfo() }
        dpsInfo.total += damage
        dpsInfo.totalByTarget[targetId] = (dpsInfo.totalByTarget[targetId] ?: 0) + damage
    }

    /**
     * 모든 통계 데이터 초기화
     */
    fun clearAll() {
        // Clear all data structures efficiently
        damageStats.clear()
        damageByTarget.clear()
        damageByUserBySkill.clear()
        selfDamageByUser.clear()

        // Clear DPS data efficiently 
        timeForDPS.start = 0
        timeForDPS.end = 0
        timeForDPS.timeByTarget.clear()
        damageForDPS.clear()  // More efficient than iterating and clearing individual objects
        selfData.total = 0
        selfData.id = 0
    }

    // Data classes for internal state
    data class TargetDamageInfo(
        var total: Long = 0,
        val byUser: MutableMap<Long, DamageStats> = ConcurrentHashMap()
    )

    data class SelfDamageInfo(
        val id: Long,
        var total: Long = 0
    )

    data class DPSInfo(
        var total: Long = 0,
        val totalByTarget: MutableMap<Long, Long> = ConcurrentHashMap(),
        var minDps: Double = Double.MAX_VALUE,
        var maxDps: Double = 0.0
    )

    data class BossData(
        var id: Long = 0
    )

    data class SelfData(
        var id: Long = 0,
        var total: Long = 0
    )

    data class TimeForDPS(
        var start: Long = 0,
        var end: Long = 0,
        val timeByTarget: MutableMap<Long, TargetTime> = ConcurrentHashMap()
    )

    data class TargetTime(
        var start: Long,
        var end: Long
    )
}