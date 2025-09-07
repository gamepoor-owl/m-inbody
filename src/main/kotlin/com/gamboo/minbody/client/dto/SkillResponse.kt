package com.gamboo.minbody.client.dto

data class SkillResponse(
    val id: Long,
    val skillKey: String,
    val skillName: String,
    val isExclude: Boolean
)