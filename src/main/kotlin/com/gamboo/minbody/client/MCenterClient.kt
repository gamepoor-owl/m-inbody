package com.gamboo.minbody.client

import com.gamboo.minbody.client.dto.*
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.*

const val HEADER = "m-center-token"

@FeignClient(
    name = "m-center-client",
    url = "\${client.m-center.url}"
)
interface MCenterClient {
    
    // Skill APIs
    @GetMapping("/api/v2/skills")
    fun getAllSkills(): APIEnvelop<List<SkillResponse>>

    // Notice APIs
    @GetMapping("/api/v2/banners/{type}")
    fun getActiveNoticesByType(@PathVariable type: String): APIEnvelop<List<NoticeResponse>>


    @GetMapping("/api/v1/buffs")
    fun getBuffs(): APIEnvelop<List<BuffResponse>>

    @PostMapping("/api/v2/player_records")
    fun savePlayerRecord(
        @RequestHeader(HEADER) header: String?,
        @RequestBody request: PlayerRecordSaveRequest
    ): APIEnvelop<PlayerRecordSaveResponse>

    @PostMapping("/api/v1/users/sign-in/third-party")
    fun signIn(
        @RequestBody request: UserSignRequest
    ): APIEnvelop<UserTokenResponse>

    @GetMapping("/api/v1/users/me")
    fun validToken(
        @RequestHeader(HEADER) header: String
    ): APIEnvelop<UserTokenResponse>

}