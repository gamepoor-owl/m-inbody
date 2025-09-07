package com.gamboo.minbody.model

/**
 * 버프 인스턴스 정보
 * 현재 활성화된 버프의 상태를 추적
 */
data class BuffInstance(
    val instKey: String,        // 버프 고유 인스턴스 키
    val buffKey: Long,          // 버프 종류 코드
    val buffInfo: BuffInfo?,
    val userId: Long,           // 시전자 ID
    var targetId: Long,         // 대상 ID
    var stack: Int = 0,         // 현재 스택
    var startTime: Long = System.currentTimeMillis(),        // 시작 시간 (리셋 가능)
    var lastUpdateTime: Long = System.currentTimeMillis()    // 마지막 갱신 시간
)

/**
 * 버프 통계 정보 (레거시 호환용)
 * 기본적인 버프 통계 정보
 */
data class BuffStats(
    var totalCount: Int = 0,    // 적용 횟수
    var totalDuration: Long = 0, // 총 지속시간 (ms)
    var maxStack: Int = 0,      // 최대 스택
    var avgStack: Double = 0.0  // 평균 스택
)

/**
 * 버프 효과 정보
 * 버프가 캐릭터 스탯에 미치는 영향
 */
data class BuffEffect(
    val atkBonus: Double = 0.0,  // 공격력 증가율
    val dmgBonus: Double = 0.0,  // 데미지 증가율
    val defBonus: Double = 0.0,  // 방어력 증가율
    val spdBonus: Double = 0.0   // 속도 증가율
)

/**
 * 버프 정보 (버프 코드에 대한 정보)
 */
data class BuffInfo(
    val name: String,
    val type: String? = null,
    val category: String? = null,
    val isExclude: Boolean = false,
    val effect: BuffEffect
) {
    companion object {
        val EMPTY = BuffInfo("", null, null, false, BuffEffect())
    }
}

/**
 * 버프 데이터 파일 구조 (JSON 매핑용)
 */
data class BuffData(
    val info: Map<String, BuffDataInfo>,
    val unhandledCode: Map<String, String>
)

/**
 * 버프 상세 정보 (JSON 매핑용)
 */
data class BuffDataInfo(
    val code: Long,
    val atk: Double?,
    val dmg: Double?,
    val def: Double?,
    val spd: Double?
)