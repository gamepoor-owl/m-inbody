package com.gamboo.minbody.config

import mu.KotlinLogging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.annotation.AsyncConfigurer
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

/**
 * 비동기 처리 설정
 * 
 * WebSocket과 REST API의 스레드를 분리하여
 * 예외 발생 시 상호 영향을 최소화합니다.
 */
@Configuration
@EnableAsync
class AsyncConfig : AsyncConfigurer {
    
    private val logger = KotlinLogging.logger {}
    
    /**
     * REST API용 스레드 풀
     */
    @Bean(name = ["restTaskExecutor"])
    fun restTaskExecutor(): TaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 10
        executor.maxPoolSize = 20
        executor.queueCapacity = 100
        executor.setThreadNamePrefix("rest-api-")
        executor.setRejectedExecutionHandler { r, _ ->
            logger.warn { "REST API task rejected: $r" }
        }
        executor.initialize()
        
        logger.info { "REST API thread pool initialized: core=${executor.corePoolSize}, max=${executor.maxPoolSize}" }
        return executor
    }
    
    /**
     * WebSocket용 스레드 풀
     */
    @Bean(name = ["webSocketTaskExecutor"])
    fun webSocketTaskExecutor(): TaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 20
        executor.maxPoolSize = 40
        executor.queueCapacity = 200
        executor.setThreadNamePrefix("websocket-")
        executor.setRejectedExecutionHandler { r, _ ->
            logger.warn { "WebSocket task rejected: $r" }
        }
        executor.initialize()
        
        logger.info { "WebSocket thread pool initialized: core=${executor.corePoolSize}, max=${executor.maxPoolSize}" }
        return executor
    }
    
    /**
     * 기본 비동기 실행자
     */
    override fun getAsyncExecutor(): Executor? {
        return restTaskExecutor()
    }
    
    /**
     * 비동기 실행 중 발생한 예외 처리
     */
    override fun getAsyncUncaughtExceptionHandler(): org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler? {
        return org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler { ex, method, params ->
            logger.error(ex) { 
                "Async execution failed - Method: ${method.name}, Params: ${params.contentToString()}" 
            }
        }
    }
}