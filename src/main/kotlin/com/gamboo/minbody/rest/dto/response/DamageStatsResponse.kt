package com.gamboo.minbody.rest.dto.response

import com.gamboo.minbody.model.UserBuffStats

data class DamageStatsResponse(
    val totalDamage: Long = 0,
    val dps: Double = 0.0,
    val durationSeconds: Long = 0,
    val combatStartTime: Long = 0,
    val combatEndTime: Long = 0,
    val users: List<UserDamageStats> = emptyList(),
    val selfUserId: Long = 0,
    val bossId: Long? = null
)

data class UserDamageStats(
    val userId: Long,
    val buffs: UserBuffStats?,
    val totalDamage: Long,
    val damagePercent: Double,
    val dps: Double,
    val hitCount: Long,  // 스킬 사용 횟수
    val damageHitCount: Long,  // 실제 데미지 타격 횟수
    val critCount: Long,
    val critRate: Double,
    val addhitCount: Long,
    val addhitRate: Double,
    val powerCount: Long = 0,
    val powerRate: Double = 0.0,
    val fastCount: Long = 0,
    val fastRate: Double = 0.0,
    val minDps: Double = 0.0,
    val maxDps: Double = 0.0,
    val skills: List<SkillStats> = emptyList(),
    val jobName: String? = null
)

data class SkillStats(
    val skillName: String,
    val totalDamage: Long,
    val damagePercent: Double,
    val hitCount: Long,  // 스킬 사용 횟수
    val damageHitCount: Long,  // 실제 타격 횟수
    val critCount: Long,
    val critRate: Double,
    val addhitCount: Long,
    val addhitRate: Double,
    val powerCount: Long = 0,
    val powerRate: Double = 0.0,
    val fastCount: Long = 0,
    val fastRate: Double = 0.0,
    val avgDamage: Long,
    val maxDamage: Long,
    val minDamage: Long,
    val dotDamage: Long = 0,
    val dotCount: Long = 0,
    val dotAvgDamage: Long = 0,
    val dotMaxDamage: Long = 0,
    val dotMinDamage: Long = 0
)

data class TargetStats(
    val targetId: Long,
    val totalDamage: Long,
    val durationSeconds: Long,
    val targetStartTime: Long = 0,
    val targetEndTime: Long = 0,
    val dps: Double,
    val users: List<UserDamageStats> = emptyList()
)
