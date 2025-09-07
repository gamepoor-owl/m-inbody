package com.gamboo.minbody.config.websocket

import com.gamboo.minbody.service.PacketWebSocketHandler
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean
import org.springframework.web.socket.server.support.DefaultHandshakeHandler

@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val packetWebSocketHandler: PacketWebSocketHandler,
    @Qualifier("webSocketTaskExecutor") private val webSocketTaskExecutor: TaskExecutor
) : WebSocketConfigurer {
    
    private val logger = KotlinLogging.logger {}
    
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        logger.info { "Registering WebSocket handler at /ws" }
        
        // WebSocket 전용 스레드 풀을 사용하는 핸드셰이크 핸들러 생성
        val handshakeHandler = DefaultHandshakeHandler()
        
        registry.addHandler(packetWebSocketHandler, "/ws")
            .setAllowedOrigins("*")
            .setHandshakeHandler(handshakeHandler)
        logger.info { "WebSocket handler registered successfully with dedicated thread pool" }
    }
    
    @Bean
    fun createWebSocketContainer(): ServletServerContainerFactoryBean {
        val container = ServletServerContainerFactoryBean()
        // 메시지 버퍼 크기를 5MB로 증가 (기본값은 보통 8KB)
        container.setMaxTextMessageBufferSize(5 * 1024 * 1024) // 5MB
        container.setMaxBinaryMessageBufferSize(5 * 1024 * 1024) // 5MB
        container.setMaxSessionIdleTimeout(300000) // 5분 (300초)로 증가
        logger.info { "WebSocket container configured with 5MB message buffer size" }
        return container
    }
}