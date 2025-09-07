package com.gamboo.minbody.model

data class DamageStats(
    var hitCount: Int = 0,  // 총 타격 횟수 (DOT 포함)
    var total: Long = 0,
    var crit: Int = 0,
    var addhit: Int = 0,
    var power: Int = 0,     // 강타 횟수
    var fast: Int = 0       // 연타 횟수
)

data class SkillDetail(
    var total: Long = 0,
    var dot: Long = 0,
    var hitCount: Int = 0,  // 총 타격 횟수 (DOT 포함)
    var cntCrit: Int = 0,
    var cntAddhit: Int = 0,
    var cntDot: Int = 0,
    var cntPower: Int = 0,   // 강타 횟수
    var cntFast: Int = 0,    // 연타 횟수
    var maxDmg: Long = 0,
    var minDmg: Long = Long.MAX_VALUE,
    var maxDotDmg: Long = 0,
    var minDotDmg: Long = Long.MAX_VALUE,
    val byTarget: MutableMap<Long, SkillDetail> = mutableMapOf()
)