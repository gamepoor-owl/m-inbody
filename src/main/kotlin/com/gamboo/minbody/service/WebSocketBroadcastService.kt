package com.gamboo.minbody.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.gamboo.minbody.rest.dto.response.WebSocketMessage
import com.gamboo.minbody.service.damage.DamageStatsService
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy

/**
 * WebSocket 브로드캐스트 서비스
 * 
 * 1초마다 통계 변경을 체크하여 모든 활성 세션에 브로드캐스트
 */
@Service
class WebSocketBroadcastService(
    private val damageStatsService: DamageStatsService,
    private val objectMapper: ObjectMapper
) {
    
    private val logger = KotlinLogging.logger {}
    
    // 활성 WebSocket 세션 관리
    private val activeSessions = ConcurrentHashMap<String, WebSocketSession>()
    
    // 브로드캐스트 스케줄러 (단일 스레드)
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    
    // 각 세션별 마지막 전송한 데미지 (개별 관리)
    private val sessionLastDamage = ConcurrentHashMap<String, Long>()
    
    @PostConstruct
    fun init() {
        logger.debug { "Starting WebSocket broadcast service" }
        
        // 500ms마다 통계 브로드캐스트 (더 빠른 업데이트)
        scheduler.scheduleAtFixedRate({
            try {
                broadcastStats()
            } catch (e: Exception) {
                logger.error(e) { "Error in broadcast scheduler" }
            }
        }, 500, 500, TimeUnit.MILLISECONDS)
    }
    
    @PreDestroy
    fun cleanup() {
        logger.debug { "Stopping WebSocket broadcast service" }
        scheduler.shutdown()
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow()
            }
        } catch (e: InterruptedException) {
            scheduler.shutdownNow()
        }
    }
    
    /**
     * WebSocket 세션 등록
     */
    fun registerSession(sessionId: String, session: WebSocketSession) {
        activeSessions[sessionId] = session
        logger.debug { "[BROADCAST] Session registered: $sessionId, total active: ${activeSessions.size}" }
        
        // 새 세션에 현재 통계 즉시 전송
        sendInitialStats(session)
    }
    
    /**
     * WebSocket 세션 해제
     */
    fun unregisterSession(sessionId: String) {
        activeSessions.remove(sessionId)
        sessionLastDamage.remove(sessionId)
        logger.debug { "[BROADCAST] Session unregistered: $sessionId, remaining: ${activeSessions.size}" }
    }
    
    /**
     * 새 세션에 초기 통계 전송
     */
    private fun sendInitialStats(session: WebSocketSession) {
        try {
            val personalStats = damageStatsService.getPersonalStats()
            
            if (personalStats != null && session.isOpen) {
                val message = WebSocketMessage("personal_stats", personalStats)
                val json = objectMapper.writeValueAsString(message)
                synchronized(session) {
                    session.sendMessage(TextMessage(json))
                }
                
                // 초기 전송 후 마지막 데미지 기록
                session.id?.let { sessionId ->
                    sessionLastDamage[sessionId] = personalStats.totalDamage
                }
                
                logger.debug { "[BROADCAST] Sent initial stats to session ${session.id} (totalDamage: ${personalStats.totalDamage})" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to send initial stats to session ${session.id}" }
        }
    }
    
    /**
     * 모든 활성 세션에 통계 브로드캐스트
     */
    private fun broadcastStats() {
        // 활성 세션이 없으면 스킵
        if (activeSessions.isEmpty()) {
            return
        }

        val personalStats = damageStatsService.getPersonalStats() ?: return
        val currentTotalDamage = personalStats.totalDamage

        // 모든 활성 세션에 세션별 모드에 따라 전송
        val deadSessions = mutableListOf<String>()
        
        activeSessions.forEach { (sessionId, session) ->
            try {
                if (session.isOpen) {
                    // 세션별 마지막 전송한 데미지 확인
                    val lastDamage = sessionLastDamage[sessionId] ?: -1L
                    
                    // totalDamage가 변경된 경우에만 전송
                    if (currentTotalDamage != lastDamage) {
                        val message = WebSocketMessage("personal_stats", personalStats)
                        val json = objectMapper.writeValueAsString(message)
                        
                        synchronized(session) {
                            session.sendMessage(TextMessage(json))
                        }
                        
                        // 마지막 전송 데미지 업데이트
                        sessionLastDamage[sessionId] = currentTotalDamage
                        
//                        logger.debug { "[BROADCAST] Sent stats to session $sessionId (damage: $lastDamage -> $currentTotalDamage)" }
                    }
                } else {
                    deadSessions.add(sessionId)
                }
            } catch (e: Exception) {
                logger.error { "[BROADCAST] Failed to send to session $sessionId: ${e.message}" }
                deadSessions.add(sessionId)
            }
        }
        
        // 죽은 세션 정리
        deadSessions.forEach { unregisterSession(it) }
    }
    
    /**
     * 레이드 종료 이벤트 브로드캐스트
     */
    fun broadcastRaidEnd() {
        
        val stats = damageStatsService.getPersonalStats()
        val message = WebSocketMessage("raid_end", stats)
        val json = objectMapper.writeValueAsString(message)
        
        activeSessions.forEach { (sessionId, session) ->
            try {
                if (session.isOpen) {
                    session.sendMessage(TextMessage(json))
                }
            } catch (e: Exception) {
                logger.error { "[BROADCAST] Failed to send raid_end to session $sessionId: ${e.message}" }
            }
        }
    }

    /**
     * 보스 스킬 사용 브로드캐스트
     */
    fun broadcastBossSkill(skillId: String) {
        val message = WebSocketMessage("boss_skill", skillId)
        val json = objectMapper.writeValueAsString(message)

        activeSessions.forEach { (_, session) ->
            try {
                if (session.isOpen) {
                    session.sendMessage(TextMessage(json))
                }
            } catch (e: Exception) {
                logger.error { "[BROADCAST] Failed to send item_use: ${e.message}" }
            }
        }
    }
    
    /**
     * 아이템 사용 이벤트 브로드캐스트
     */
    fun broadcastItemUse(itemPacket: Any) {
        val message = WebSocketMessage("item_use", itemPacket)
        val json = objectMapper.writeValueAsString(message)
        
        activeSessions.forEach { (_, session) ->
            try {
                if (session.isOpen) {
                    session.sendMessage(TextMessage(json))
                }
            } catch (e: Exception) {
                logger.error { "[BROADCAST] Failed to send item_use: ${e.message}" }
            }
        }
    }
    
    /**
     * 레코드 저장 완료 이벤트 브로드캐스트
     */
    fun broadcastRecordSaved(recordId: Long) {
        val recordData = mapOf(
            "recordId" to recordId,
            "url" to "https://m-inbody.info/record_detail?record_id=$recordId"
        )
        val message = WebSocketMessage("record_saved", recordData)
        val json = objectMapper.writeValueAsString(message)
        
        logger.info { "[BROADCAST] Broadcasting record saved event with ID: $recordId" }
        
        activeSessions.forEach { (sessionId, session) ->
            try {
                if (session.isOpen) {
                    synchronized(session) {
                        session.sendMessage(TextMessage(json))
                    }
                    logger.debug { "[BROADCAST] Sent record_saved to session $sessionId" }
                }
            } catch (e: Exception) {
                logger.error { "[BROADCAST] Failed to send record_saved to session $sessionId: ${e.message}" }
            }
        }
    }
}