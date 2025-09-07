package com.gamboo.minbody.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.gamboo.minbody.rest.dto.response.WebSocketMessage
import com.gamboo.minbody.service.damage.DamageProcessingService
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@Component
class PacketWebSocketHandler(
    private val webSocketBroadcastService: WebSocketBroadcastService,
    private val damageProcessingService: DamageProcessingService,
    private val objectMapper: ObjectMapper,
) : TextWebSocketHandler() {

    private val logger = KotlinLogging.logger {}

    // 세션별 헬스체크 스케줄러 관리
    private val healthCheckSchedulers = ConcurrentHashMap<String, ScheduledFuture<*>>()
    private val scheduler = Executors.newScheduledThreadPool(1) // health check용

    // 레이드 종료 리스너 등록 여부 확인 (중복 방지)
    private var raidListenerRegistered = false

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val currentThread = Thread.currentThread().name
        logger.info { "[SESSION] WebSocket connected: ${session.id} from ${session.remoteAddress} on thread: $currentThread" }

        // 이미 존재하는 헬스체크 스케줄러가 있으면 취소
        healthCheckSchedulers[session.id]?.cancel(false)

        // WebSocketBroadcastService에 세션 등록
        webSocketBroadcastService.registerSession(session.id, session)
        logger.info { "[SESSION] Session registered with broadcast service: ${session.id}" }

        // 레이드 종료 리스너는 한 번만 등록 (중복 방지)
        if (!raidListenerRegistered) {
            damageProcessingService.setOnRaidEndListener {
                logger.info { "Raid end detected, triggering raid_end event" }
                // WebSocketBroadcastService가 레이드 종료를 브로드캐스트
                webSocketBroadcastService.broadcastRaidEnd()
            }
            
            // 보스 스킬 리스너 등록
            damageProcessingService.setOnBossSkillListener { skillName ->
                // 비동기로 브로드캐스트하여 패킷 처리 블로킹 방지
                try {
                    logger.debug { "Boss skill detected: $skillName, broadcasting to all sessions" }
                    webSocketBroadcastService.broadcastBossSkill(skillName)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to broadcast boss skill: $skillName" }
                }
            }
            
            raidListenerRegistered = true
        }

        // Start health check for this session (every 30 seconds)
        val healthCheckTask = scheduler.scheduleAtFixedRate({
            if (session.isOpen) {
                sendHealthCheck(session)
            } else {
                // 세션이 닫혔으면 스케줄러 취소
                healthCheckSchedulers[session.id]?.cancel(false)
                healthCheckSchedulers.remove(session.id)
            }
        }, 30, 30, TimeUnit.SECONDS)

        healthCheckSchedulers[session.id] = healthCheckTask
        logger.info { "[SESSION] Health check started for session: ${session.id}" }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val currentThread = Thread.currentThread().name
        logger.info { "[SESSION] WebSocket disconnected: ${session.id} with status: ${status.code} (${status.reason}) on thread: $currentThread" }

        // 헬스체크 스케줄러 정리
        healthCheckSchedulers[session.id]?.cancel(false)
        healthCheckSchedulers.remove(session.id)
        logger.info { "[SESSION] Health check stopped for session: ${session.id}" }

        // WebSocketBroadcastService에서 세션 해제
        webSocketBroadcastService.unregisterSession(session.id)
        logger.info { "[SESSION] Session ${session.id} unregistered from broadcast service" }
    }


    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        try {
            val payload = message.payload
            logger.debug { "Received message from ${session.id}: $payload" }

            val jsonNode = objectMapper.readTree(payload)
            val messageType = jsonNode.get("type")?.asText()

            when (messageType) {
                "pong" -> {
                    // Health check response from client
                    logger.debug { "Received pong from session ${session.id}" }
                }

                "clear" -> {
                    // Clear all statistics including buff data
                    logger.info { "Clearing all statistics for session ${session.id}" }
                    damageProcessingService.clearAll()

                    // Send confirmation back to client
                    val clearConfirmation = WebSocketMessage(
                        "clear_confirmed", mapOf(
                            "status" to "success",
                            "timestamp" to System.currentTimeMillis()
                        )
                    )
                    synchronized(session) {
                        session.sendMessage(TextMessage(objectMapper.writeValueAsString(clearConfirmation)))
                    }
                }
                // Network interface commands removed - use REST API instead
                // This reduces WebSocket load for better game data performance
                else -> {
                    logger.debug { "Unknown message type: $messageType" }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error handling text message" }
        }
    }


    /**
     * WebSocket 전송 에러 처리
     * 세션을 즉시 닫지 않고 복구를 시도합니다.
     */
    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        logger.error(exception) { "WebSocket transport error for session ${session.id}" }

        // 세션이 여전히 열려있다면 복구 시도
        if (session.isOpen) {
            try {
                // 에러 메시지 전송 시도
                val errorMessage = WebSocketMessage(
                    "error", mapOf(
                        "message" to "Transport error occurred, but connection maintained",
                        "timestamp" to System.currentTimeMillis()
                    )
                )
                session.sendMessage(TextMessage(objectMapper.writeValueAsString(errorMessage)))

                logger.info { "Session ${session.id} recovered from transport error" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to recover session ${session.id}, closing connection" }
                try {
                    session.close(CloseStatus.SERVER_ERROR)
                } catch (closeError: Exception) {
                    logger.error(closeError) { "Error closing session ${session.id}" }
                }
            }
        }
    }

    /**
     * WebSocket 세션 보호를 위한 헬스체크
     * 주기적으로 ping을 보내 연결 상태를 확인합니다.
     */
    private fun sendHealthCheck(session: WebSocketSession) {
        if (!session.isOpen) return

        try {
            val pingMessage = WebSocketMessage(
                "ping", mapOf(
                    "timestamp" to System.currentTimeMillis()
                )
            )
            synchronized(session) {
                session.sendMessage(TextMessage(objectMapper.writeValueAsString(pingMessage)))
            }
            logger.debug { "[HEALTH] Sent ping to session ${session.id}" }
        } catch (e: Exception) {
            logger.debug { "[HEALTH] Failed to send ping to session ${session.id}: ${e.message}" }
        }
    }
}