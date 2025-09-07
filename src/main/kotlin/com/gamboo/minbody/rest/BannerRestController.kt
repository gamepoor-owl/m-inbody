package com.gamboo.minbody.rest

import com.gamboo.minbody.service.NoticeApiService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v2/banners")
class BannerRestController(
    private val noticeApiService: NoticeApiService
) {

    @GetMapping("/{type}")
    fun getBanner(
        @PathVariable type: String
    ) = noticeApiService.getActiveNoticesByType(type)
}