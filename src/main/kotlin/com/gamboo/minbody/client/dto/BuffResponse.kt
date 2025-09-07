package com.gamboo.minbody.client.dto

data class BuffResponse(
    val id: Long,
    val buffId: Long,
    val buffName: String,
    val type: String,
    val category: String?,
    val isExclude: Boolean
)