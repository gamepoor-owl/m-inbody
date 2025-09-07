package com.gamboo.minbody.client.dto

class PlayerRecordSaveRequest(
    val key: String = "2025-08-27",
    val version: String,
    val playerId: Long?,
    val targetId: Long?,
    val type: String?,
    val player: String,
    val partyPlayer: List<String>,
    val duration: Long,
    val dps: Long,
    val totalDamage: Long,
    val skills: List<SkillRecordSaveRequest>,
    val buffs: BuffRecordSave,
    val hitCount: Long,
    val critCount: Long,
    val addhitCount: Long,
    val powerCount: Long,
    val fastCount: Long,
    val bossSkills: Map<String, Long>
)

class BuffRecordSave(
    val castBuffs: List<BuffRecordSaveRequest>,
    val receivedBuffs: List<BuffRecordSaveRequest>
)

class SkillRecordSaveRequest(
    val skillName: String,

    val totalDamage: Long,

    val hitCount: Long,

    val critCount: Long,

    val addhitCount: Long,

    val powerCount: Long,

    val fastCount: Long,

    val dotDamage: Long,

    val dotCount: Long
)

class BuffRecordSaveRequest(
    val buffId: Long,

    val totalCount: Int,

    val totalDuration: Long,

    val maxStack: Int,

    val avgStack: Double,

    val startCount: Int = 0,

    val updateCount: Int = 0,

    val refreshCount: Int = 0,
)