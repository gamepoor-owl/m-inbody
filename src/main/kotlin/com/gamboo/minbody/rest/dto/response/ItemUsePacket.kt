package com.gamboo.minbody.rest.dto.response

data class ItemUsePacket(
    override val type: Int,
    override val hide: Boolean = false,
    val userId: Long,
    val itemName: String,
    val timestamp: Long = System.currentTimeMillis()
) : PacketData()