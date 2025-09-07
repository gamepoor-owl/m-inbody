package com.gamboo.minbody.config.feign

import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.context.annotation.Configuration

@Configuration
@EnableFeignClients(basePackages = ["com.gamboo.minbody.client"])
class FeignConfig