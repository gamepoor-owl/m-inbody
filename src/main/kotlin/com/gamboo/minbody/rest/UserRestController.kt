package com.gamboo.minbody.rest

import com.gamboo.minbody.client.HEADER
import com.gamboo.minbody.client.dto.UserSignRequest
import com.gamboo.minbody.service.LoginService
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserRestController(
    private val loginService: LoginService
) {

    @PostMapping("/sign-in/third-party")
    fun signIn(
        @RequestBody request: UserSignRequest
    ) = loginService.login(request)


    @GetMapping("/me")
    fun me(
        @RequestHeader(HEADER) header: String
    ) = loginService.me(header)

    @DeleteMapping("/logout")
    fun logout()
    = loginService.logout()
}