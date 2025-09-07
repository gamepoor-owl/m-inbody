package com.gamboo.minbody.client.dto

data class UserTokenResponse(
    val accessToken: String,
    val accessTokenExpirationTime: Long,
    val refreshToken: String?,
    val refreshTokenExpirationTime: Long?,
    val userInfo: UserInfo,
)

data class UserInfo(
    val id: Long,
    val profile: String?,
    val username: String,
    val email: String
)