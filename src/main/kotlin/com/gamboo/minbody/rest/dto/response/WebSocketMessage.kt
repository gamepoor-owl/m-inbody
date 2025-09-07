package com.gamboo.minbody.rest.dto.response

data class WebSocketMessage(
    val type: String,
    val data: Any
)