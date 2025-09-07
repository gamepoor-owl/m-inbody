package com.gamboo.minbody.rest.dto.response

sealed class PacketData {
    abstract val type: Int
    abstract val hide: Boolean
}

data class AttackPacket(
    override val type: Int = 10299,
    override val hide: Boolean = false,
    val userId: Long,
    val targetId: Long,
    val key1: Long,
    val key2: Long,
    val flags: Map<String, Boolean>
) : PacketData()

data class BossPacket(
    override val type: Int = 100181,
    override val hide: Boolean = false,
    val bossId: Long,
): PacketData()

data class ActionPacket(
    override val type: Int = 100041,
    override val hide: Boolean = false,
    val userId: Long,
    val skillName: String,
    val key1: Long
) : PacketData()

data class HpChangedPacket(
    override val type: Int = 100178,
    override val hide: Boolean = true,
    val targetId: Long,
    val prevHp: Long,
    val currentHp: Long
) : PacketData()

data class CurrentHpPacket(
    override val type: Int = 100180,
    override val hide: Boolean = true,
    val targetId: Long,
    val currentHp: Long
) : PacketData()

data class SelfDamagePacket(
    override val type: Int = 20741,
    override val hide: Boolean = false,
    val userId: Long,
    val targetId: Long,
    val damage: Long,
    val flags: Map<String, Boolean>
) : PacketData()

data class DieBlowPacket(
    override val type: Int = 100042,
    override val hide: Boolean = false,
    val userId: Long,
    val skillName: String,
    val unknown1: Long
) : PacketData()