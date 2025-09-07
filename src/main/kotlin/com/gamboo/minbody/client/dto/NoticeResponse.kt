package com.gamboo.minbody.client.dto

import java.time.ZonedDateTime

data class NoticeResponse(
    val id: Long,
    val title: String,
    val type: String,
    val description: String? = null,
    val imageUrl: String? = null,
    val url: String?,
    val startAt: ZonedDateTime,
    val endAt: ZonedDateTime
)