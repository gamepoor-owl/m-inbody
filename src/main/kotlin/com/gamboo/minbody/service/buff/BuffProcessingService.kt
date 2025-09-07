package com.gamboo.minbody.service.buff

import com.gamboo.minbody.model.BuffEffect
import com.gamboo.minbody.model.BuffInstance
import com.gamboo.minbody.rest.dto.response.BuffEndPacket
import com.gamboo.minbody.rest.dto.response.BuffStartPacket
import com.gamboo.minbody.rest.dto.response.BuffUpdatePacket
import com.gamboo.minbody.rest.dto.response.PacketData
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * 버프 처리 서비스
 * 버프 패킷을 처리하여 버프 상태를 관리하고 통계를 수집
 */
@Service
class BuffProcessingService(
    private val buffMappingService: BuffMappingService,
    private val userBuffStatisticsService: UserBuffStatisticsService
) {
    
    private val logger = KotlinLogging.logger {}
    
    // 활성 버프 인스턴스 관리 (인스턴스 키 -> 버프 인스턴스)
    private val activeBuffs = ConcurrentHashMap<String, BuffInstance>()
    
    // 유저별 버프 추적 (유저 ID -> 인스턴스 키 세트)
    private val buffsByUser = ConcurrentHashMap<Long, MutableSet<String>>()
    
    // 타겟별 버프 추적 (타겟 ID -> 인스턴스 키 세트)
    private val buffsByTarget = ConcurrentHashMap<Long, MutableSet<String>>()

    /**
     * 버프 패킷 처리
     * 
     * @param packet 처리할 버프 패킷
     */
    fun processBuffPacket(packet: PacketData) {
        when (packet) {
            is BuffStartPacket -> processBuffStart(packet)
            is BuffUpdatePacket -> processBuffUpdate(packet)
            is BuffEndPacket -> processBuffEnd(packet)
            else -> logger.warn { "Unknown buff packet type: ${packet.type}" }
        }
    }
    
    /**
     * 버프 생성/적용 처리
     * 
     * @param packet 버프 시작 패킷
     */
    private fun processBuffStart(packet: BuffStartPacket) {
        val buffInfo = buffMappingService.getBuffByKey(packet.buffKey)

        if(buffInfo?.isExclude == true) {
            logger.debug { "Ignoring buff ${packet.buffKey}" }
            return
        }
        
        // 버프 인스턴스 생성
        val buffInstance = BuffInstance(
            instKey = packet.instKey,
            buffKey = packet.buffKey,
            buffInfo = buffInfo,
            userId = packet.userId,
            targetId = packet.targetId,
            stack = packet.stack,
            startTime = System.currentTimeMillis(),
            lastUpdateTime = System.currentTimeMillis()
        )

        // 기존 인스턴스가 있으면 종료 처리
        activeBuffs[packet.instKey]?.let { existingBuff ->
            val duration = System.currentTimeMillis() - existingBuff.startTime
            userBuffStatisticsService.recordBuffEnd(existingBuff, duration)
        }
        
        // 새 인스턴스 저장
        activeBuffs[packet.instKey] = buffInstance
        
        // 버프 추적 정보 업데이트
        trackBuffByUser(packet.userId, packet.instKey)
        trackBuffByTarget(packet.targetId, packet.instKey)

        
        // 유저별 통계 업데이트
        userBuffStatisticsService.recordBuffStart(buffInstance)
        
        logger.debug { 
            "Buff started: $buffInfo (key: ${packet.buffKey}, stack: ${packet.stack}) " +
            "from user ${packet.userId} on target ${packet.targetId}" 
        }
    }
    
    /**
     * 버프 갱신 처리
     * 
     * @param packet 버프 업데이트 패킷
     */
    private fun processBuffUpdate(packet: BuffUpdatePacket) {
        activeBuffs[packet.instKey]?.let { buff ->
            val oldStack = buff.stack
            
            // 스택 업데이트
            buff.stack = packet.stack
            buff.lastUpdateTime = System.currentTimeMillis()
            
            // 타겟이 변경된 경우 추적 정보 업데이트
            if (buff.targetId != packet.targetId) {
                updateTargetTracking(buff.targetId, packet.targetId, packet.instKey)
                buff.targetId = packet.targetId
            }

            // 유저별 통계 업데이트
            userBuffStatisticsService.recordBuffUpdate(buff, oldStack)
            
            logger.debug { 
                "Buff updated: ${buff.buffInfo?.name ?: "Unknown"} (stack: $oldStack -> ${packet.stack}) " +
                "on target ${packet.targetId}" 
            }
        }
    }
    
    /**
     * 버프 종료 처리
     * 
     * @param packet 버프 종료 패킷
     */
    private fun processBuffEnd(packet: BuffEndPacket) {
        activeBuffs.remove(packet.instKey)?.let { buff ->
            // 버프 지속시간 계산
            val duration = System.currentTimeMillis() - buff.startTime

            // 유저별 통계 업데이트
            userBuffStatisticsService.recordBuffEnd(buff, duration)
            
            // 추적 정보 제거 및 정리
            cleanupBuffTracking(buff.userId, buff.targetId, packet.instKey)
            
            logger.debug { 
                "Buff ended: ${buff.buffInfo?.name ?: "Unknown"} (duration: ${duration}ms, final stack: ${buff.stack})" 
            }
        }
    }
    
    /**
     * 특정 타겟의 현재 버프 효과 계산
     * 
     * @param targetId 대상 ID
     * @return 누적된 버프 효과
     */
    fun calculateCurrentBuffEffects(targetId: Long): BuffEffect {
        val targetBuffs = buffsByTarget[targetId] ?: return BuffEffect()
        
        var totalAtkBonus = 0.0
        var totalDmgBonus = 0.0
        var totalDefBonus = 0.0
        var totalSpdBonus = 0.0
        
        targetBuffs.forEach { instKey ->
            activeBuffs[instKey]?.let { buff ->
                val effect = buffMappingService.getBuffEffect(buff.buffKey)
                // 스택만큼 효과 증가
                totalAtkBonus += effect.atkBonus * buff.stack
                totalDmgBonus += effect.dmgBonus * buff.stack
                totalDefBonus += effect.defBonus * buff.stack
                totalSpdBonus += effect.spdBonus * buff.stack
            }
        }
        
        return BuffEffect(totalAtkBonus, totalDmgBonus, totalDefBonus, totalSpdBonus)
    }

    
    /**
     * 전체 초기화
     */
    fun clearAll() {
        activeBuffs.clear()
        buffsByUser.clear()
        buffsByTarget.clear()
        userBuffStatisticsService.clearAllStats()
        logger.info { "Cleared all buff data" }
    }
    
    /**
     * 버프 통계만 초기화 (버프 자체는 유지)
     * Boss ID 변경 시 사용 - 이전 전투의 통계를 리셋하고 새로 시작
     */
    fun clearBuffStats() {
        val currentTime = System.currentTimeMillis()
        
        // 1. 모든 통계 초기화
        userBuffStatisticsService.clearAllStats()
        
        // 2. 남아있는 활성 버프들의 시작 시간을 현재로 리셋하고 새로운 통계 생성
        activeBuffs.forEach { (instKey, buff) ->
            // 버프의 시작 시간을 현재 시간으로 리셋 (새 전투 시작)
            buff.startTime = currentTime
            buff.lastUpdateTime = currentTime
            
            // 새로운 통계 시작 (recordBuffStart 호출)
            userBuffStatisticsService.recordBuffStart(buff)
            logger.debug { "Reset and recreated statistics for active buff: $instKey" }
        }
        
        logger.info { "Cleared buff statistics and reset ${activeBuffs.size} active buffs to current time" }
    }
    
    /**
     * 선택적 버프 초기화 - 지정된 시간 이상 된 버프만 초기화
     * 
     * @deprecated clearBuffStats()를 사용하세요. 버프 시작 시간을 리셋하는 것으로 충분합니다.
     * @param olderThanMs 이 시간(밀리초) 이상 된 버프만 초기화 (기본값: 3000ms = 3초)
     */
    @Deprecated("Use clearBuffStats() instead", ReplaceWith("clearBuffStats()"))
    fun clearOldBuffs(olderThanMs: Long = 3000L) {
        val now = System.currentTimeMillis()
        val cutoffTime = now - olderThanMs
        
        val buffsToRemove = mutableListOf<String>()
        
        // 3초 이상 된 버프만 찾아서 제거
        activeBuffs.forEach { (instKey, buff) ->
            if (buff.startTime < cutoffTime) {
                buffsToRemove.add(instKey)
                
                // 버프 지속시간 계산
                val duration = now - buff.startTime
                
                // 유저별 통계 업데이트
                userBuffStatisticsService.recordBuffEnd(buff, duration)
                
                // 추적 정보 제거
                cleanupBuffTracking(buff.userId, buff.targetId, instKey)
            }
        }
        
        // 실제 제거
        buffsToRemove.forEach { instKey ->
            activeBuffs.remove(instKey)
        }
        
        if (buffsToRemove.isNotEmpty()) {
            logger.info { "Cleared ${buffsToRemove.size} old buffs (older than ${olderThanMs}ms)" }
        }
    }
    
    /**
     * 유저별 버프 추적
     */
    private fun trackBuffByUser(userId: Long, instKey: String) {
        buffsByUser.computeIfAbsent(userId) { ConcurrentHashMap.newKeySet() }
            .add(instKey)
    }
    
    /**
     * 타겟별 버프 추적
     */
    private fun trackBuffByTarget(targetId: Long, instKey: String) {
        buffsByTarget.computeIfAbsent(targetId) { ConcurrentHashMap.newKeySet() }
            .add(instKey)
    }
    
    /**
     * 타겟 변경 시 추적 정보 업데이트
     */
    private fun updateTargetTracking(oldTargetId: Long, newTargetId: Long, instKey: String) {
        buffsByTarget[oldTargetId]?.remove(instKey)
        trackBuffByTarget(newTargetId, instKey)
    }
    
    /**
     * 버프 추적 정보 정리
     */
    private fun cleanupBuffTracking(userId: Long, targetId: Long, instKey: String) {
        buffsByUser[userId]?.remove(instKey)
        buffsByTarget[targetId]?.remove(instKey)
        
        // 빈 세트 정리
        if (buffsByUser[userId]?.isEmpty() == true) {
            buffsByUser.remove(userId)
        }
        if (buffsByTarget[targetId]?.isEmpty() == true) {
            buffsByTarget.remove(targetId)
        }
    }
}