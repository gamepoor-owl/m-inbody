package com.gamboo.minbody.model

/**
 * 유저별 버프 통계 정보
 * 유저가 시전한 버프와 받은 버프를 구분하여 관리
 */
data class UserBuffStats(
    val userId: Long,
    val castBuffStats: MutableMap<Long, DetailedBuffStats> = mutableMapOf(),  // 시전한 버프
    val receivedBuffStats: MutableMap<Long, DetailedBuffStats> = mutableMapOf(),  // 받은 버프
    var lastUpdateTime: Long = System.currentTimeMillis()
) {
    companion object {
        fun empty(userId: Long): UserBuffStats = UserBuffStats(userId)
    }
}

/**
 * 상세 버프 통계 정보
 * 기존 BuffStats를 확장하여 더 많은 정보 제공
 */
data class DetailedBuffStats(
    // 기본 통계
    val id: Long,
    val name: String,
    var totalCount: Int = 0,              // 총 발동 횟수 (시작 + 업데이트 + 갱신)
    var startCount: Int = 0,              // 시작 횟수
    var updateCount: Int = 0,             // 업데이트 횟수
    var refreshCount: Int = 0,            // 갱신 횟수
    var totalDuration: Long = 0,          // 총 지속시간 (ms)
    var maxStack: Int = 0,                // 최대 스택
    var avgStack: Double = 0.0,           // 평균 스택
    var currentStack: Int = 0,            // 현재 스택
    var totalStackSum: Long = 0,          // 모든 스택의 합계 (평균 계산용)
    
    // 시간별 통계
    var firstAppliedTime: Long = 0,       // 첫 적용 시간
    var lastAppliedTime: Long = 0,        // 마지막 적용 시간
    var currentSessionStart: Long = 0,    // 현재 세션 시작 시간 (0 = 비활성)
    
    // 가동률 (전투 시간 대비 버프 활성 시간 비율)
    var uptimePercent: Double = 0.0       // 가동률 (0.0 ~ 100.0)
)


/**
 * 업타임 윈도우
 */
data class UptimeWindow(
    val startTime: Long,
    val endTime: Long,
    val avgStack: Double,
    val maxStack: Int
)

/**
 * 버프 통계 요약
 */
data class BuffStatsSummary(
    val buffName: String,
    val buffCode: Long,
    val totalCount: Int,
    val totalDuration: Long,
    val uptime: Double,        // 업타임 백분율
    val avgStack: Double,
    val maxStack: Int,
    val effectiveBonus: BuffEffectSummary  // 실제 효과 요약
)

/**
 * 버프 효과 요약
 */
data class BuffEffectSummary(
    val avgAtkBonus: Double,
    val avgDmgBonus: Double,
    val avgDefBonus: Double,
    val avgSpdBonus: Double,
    val totalAtkBonus: Double,  // 누적 효과
    val totalDmgBonus: Double,
    val totalDefBonus: Double,
    val totalSpdBonus: Double
)

/**
 * 유저 버프 요약 응답
 */
data class UserBuffSummaryResponse(
    val userId: Long,
    val lastUpdateTime: Long,
    val castBuffsSummary: List<BuffSummaryInfo>,
    val receivedBuffsSummary: List<BuffSummaryInfo>
) {
    companion object {
        fun empty(userId: Long) = UserBuffSummaryResponse(
            userId = userId,
            lastUpdateTime = 0L,
            castBuffsSummary = emptyList(),
            receivedBuffsSummary = emptyList()
        )
    }
}

/**
 * 버프 요약 정보
 */
data class BuffSummaryInfo(
    val buffKey: Long,
    val totalCount: Int,           // 총 발동 횟수
    val startCount: Int,           // 시작 횟수
    val updateCount: Int,          // 업데이트 횟수
    val refreshCount: Int,         // 갱신 횟수
    val totalDuration: Long,       // 총 지속시간
    val avgStack: Double,          // 평균 스택
    val maxStack: Int,             // 최대 스택
    val firstAppliedTime: Long,    // 첫 적용 시간
    val lastAppliedTime: Long,     // 마지막 적용 시간
    val currentSessionStart: Long  // 현재 세션 시작 시간 (0 = 비활성)
)