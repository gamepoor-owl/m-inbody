package com.gamboo.minbody.rest.dto.response

data class BuffInfoResponse(
    val buffCode: Long,
    val buffName: String,
    val atkBonus: Double,
    val dmgBonus: Double,
    val defBonus: Double,
    val spdBonus: Double
)

data class AllBuffInfoResponse(
    val totalCount: Int,
    val buffs: List<BuffInfoResponse>
)