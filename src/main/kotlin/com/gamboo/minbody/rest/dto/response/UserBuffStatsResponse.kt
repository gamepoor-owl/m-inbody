package com.gamboo.minbody.rest.dto.response

/**
 * 유저별 버프 통계 응답
 */
data class UserBuffStatsResponse(
    val userId: Long,
    val userName: String?,
    val castBuffs: List<BuffStatsDetail>,
    val receivedBuffs: List<BuffStatsDetail>,
    val summary: UserBuffSummary,
    val lastUpdateTime: Long
)

/**
 * 버프 통계 상세 정보
 */
data class BuffStatsDetail(
    val buffName: String,
    val buffCode: Long,
    val totalCount: Int,
    val totalDuration: Long,
    val avgDuration: Double,
    val minDuration: Long,
    val maxDuration: Long,
    val currentStack: Int,
    val maxStack: Int,
    val avgStack: Double,
    val uptimePercent: Double,
    val targets: List<BuffTargetStats>? = null,  // 시전한 경우
    val casters: List<BuffCasterStats>? = null,  // 받은 경우
    val effectSummary: BuffEffectResponse
)

/**
 * 대상별 버프 통계
 */
data class BuffTargetStats(
    val targetId: Long,
    val targetName: String?,
    val count: Int,
    val totalDuration: Long,
    val avgStack: Double,
    val maxStack: Int
)

/**
 * 시전자별 버프 통계
 */
data class BuffCasterStats(
    val casterId: Long,
    val casterName: String?,
    val count: Int,
    val totalDuration: Long,
    val avgStack: Double,
    val maxStack: Int
)

/**
 * 버프 효과 응답
 */
data class BuffEffectResponse(
    val avgAtkBonus: Double,
    val avgDmgBonus: Double,
    val avgDefBonus: Double,
    val avgSpdBonus: Double,
    val totalAtkBonus: Double,
    val totalDmgBonus: Double,
    val totalDefBonus: Double,
    val totalSpdBonus: Double
)

/**
 * 유저 버프 요약
 */
data class UserBuffSummary(
    val totalCastBuffs: Int,
    val totalReceivedBuffs: Int,
    val totalCastCount: Int,
    val totalReceivedCount: Int,
    val avgCastUptime: Double,
    val avgReceivedUptime: Double,
    val mostCastBuff: String?,
    val mostReceivedBuff: String?
)

/**
 * 버프 업타임 응답
 */
data class BuffUptimeResponse(
    val userId: Long,
    val buffName: String,
    val startTime: Long,
    val endTime: Long,
    val uptimePercent: Double,
    val activeWindows: List<UptimeWindowResponse>
)

/**
 * 업타임 윈도우 응답
 */
data class UptimeWindowResponse(
    val startTime: Long,
    val endTime: Long,
    val duration: Long,
    val avgStack: Double,
    val maxStack: Int
)

/**
 * 버프 랭킹 응답
 */
data class BuffRankingResponse(
    val rankings: List<BuffRankingEntry>,
    val analysisPeriod: AnalysisPeriod
)

/**
 * 버프 랭킹 항목
 */
data class BuffRankingEntry(
    val rank: Int,
    val userId: Long,
    val userName: String?,
    val buffName: String,
    val metricType: String,  // uptime, count, duration, effectiveness
    val metricValue: Double
)

/**
 * 분석 기간
 */
data class AnalysisPeriod(
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Long
)