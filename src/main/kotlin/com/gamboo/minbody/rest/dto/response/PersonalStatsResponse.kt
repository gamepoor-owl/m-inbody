package com.gamboo.minbody.rest.dto.response

import com.gamboo.minbody.model.UserBuffStats

/**
 * 개인 모드에서 사용되는 개인 통계 응답 DTO
 * 특정 유저 한 명의 데이터만 포함하여 전송량 최적화
 */
data class PersonalStatsResponse(
    val userId: Long,
    val partyPlayers: Map<Long, String> = emptyMap<Long, String>(),
    val bossId: Long? = null,
    val buffs: UserBuffStats? = null,
    val totalDamage: Long = 0,
    val dps: Double = 0.0,
    val durationSeconds: Long = 0,
    val combatStartTime: Long = 0,
    val combatEndTime: Long = 0,
    val hitCount: Long = 0,
    val critCount: Long = 0,
    val critRate: Double = 0.0,
    val addhitCount: Long = 0,
    val addhitRate: Double = 0.0,
    val powerCount: Long = 0,
    val powerRate: Double = 0.0,
    val fastCount: Long = 0,
    val fastRate: Double = 0.0,
    val minDps: Double = 0.0,
    val maxDps: Double = 0.0,
    val skills: List<SkillStats> = emptyList(),
    val targets: List<PersonalTargetStats> = emptyList(),
    val jobName: String? = null,
    val selfDamageTotal: Long = 0
)

/**
 * 개인 모드에서 타겟별 통계 정보
 */
data class PersonalTargetStats(
    val targetId: Long,
    val totalDamage: Long,
    val damagePercent: Double,
    val dps: Double,
    val durationSeconds: Long,
    val targetStartTime: Long = 0,
    val targetEndTime: Long = 0,
    val hitCount: Long = 0,
    
    // 치명타 통계
    val critCount: Long = 0,
    val critRate: Double = 0.0,
    
    // 추가타 통계
    val addhitCount: Long = 0,
    val addhitRate: Double = 0.0,
    
    // 강타 통계
    val powerCount: Long = 0,
    val powerRate: Double = 0.0,
    
    // 연타 통계
    val fastCount: Long = 0,
    val fastRate: Double = 0.0,
    
    // DPS 범위
    val minDps: Double = 0.0,
    val maxDps: Double = 0.0,
    
    val skills: List<SkillStats> = emptyList()
)
