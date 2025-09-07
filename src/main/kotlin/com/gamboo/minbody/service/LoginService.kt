package com.gamboo.minbody.service

import com.gamboo.minbody.client.MCenterClient
import com.gamboo.minbody.client.dto.UserSignRequest
import mu.KotlinLogging
import org.springframework.stereotype.Service

@Service
class LoginService(
    private val mCenterClient: MCenterClient,
) {

    private val logger = KotlinLogging.logger {}

    var token: String? = null

    fun login(request: UserSignRequest) = run {
        val res = mCenterClient.signIn(request)
        token = res.data.accessToken

        logger.info { "Successfully logged in. ${token}" }
        res
    }

    fun me(header: String) = run {
        val res = mCenterClient.validToken(header)
        token = res.data.accessToken

        logger.info { "Successfully me . ${token}" }
        res
    }

    fun logout() {
        token = null
    }
}