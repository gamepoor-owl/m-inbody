package com.gamboo.minbody.service.buff

import com.gamboo.minbody.model.*
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * 유저별 버프 통계 서비스
 * 각 유저가 시전한 버프와 받은 버프를 구분하여 통계 관리
 */
@Service
class UserBuffStatisticsService(
    private val buffMappingService: BuffMappingService
) {
    
    private val logger = KotlinLogging.logger {}
    
    // 유저별 버프 통계 (userId -> UserBuffStats)
    private val userBuffStats = ConcurrentHashMap<Long, UserBuffStats>()
    
    // 활성 버프 인스턴스 추적 (instKey -> BuffInstance)
    private val activeBuffInstances = ConcurrentHashMap<String, BuffInstance>()
    
    /**
     * 버프 시작 기록
     */
    fun recordBuffStart(buff: BuffInstance) {
        // 시전자 통계 업데이트
        updateCasterStats(buff.userId, buff)
        
        // 대상 통계 업데이트
        updateTargetStats(buff.targetId, buff)
        
        // 활성 버프 인스턴스 저장
        activeBuffInstances[buff.instKey] = buff
    }
    
    /**
     * 버프 업데이트 기록
     */
    fun recordBuffUpdate(buff: BuffInstance, oldStack: Int) {
        val currentTime = System.currentTimeMillis()
        
        val stats = getOrCreateUserStats(buff.userId)
        val castStats = stats.castBuffStats[buff.buffKey] ?: return
        
        // Duration 계산 단순화: Update 시에는 duration을 건드리지 않음
        // Duration은 버프 시작과 종료 시점에만 계산
        
        // 업데이트도 발동 횟수로 계산
        castStats.totalCount++
        castStats.updateCount++  // 업데이트 횟수 별도 추적
        
        // 스택 합계 추가 및 평균 즉시 계산
        castStats.totalStackSum += buff.stack
        castStats.avgStack = castStats.totalStackSum.toDouble() / castStats.totalCount
        
        // 최대 스택 업데이트
        castStats.maxStack = max(castStats.maxStack, buff.stack)
        castStats.currentStack = buff.stack
        
        // 마지막 적용 시간 업데이트
        castStats.lastAppliedTime = currentTime

        // 받은 버프 통계도 업데이트
        updateReceivedBuffStats(buff, oldStack)
    }
    
    /**
     * 버프 종료 기록
     */
    fun recordBuffEnd(buff: BuffInstance, duration: Long) {

        // 현재 세션 종료 (지속시간 누적)
        endCurrentSession(buff, duration)

        // 활성 버프 인스턴스 제거
        activeBuffInstances.remove(buff.instKey)
    }
    
    /**
     * 시전자 통계 업데이트
     */
    private fun updateCasterStats(userId: Long, buff: BuffInstance) {
        val stats = getOrCreateUserStats(userId)
        val castStats = stats.castBuffStats.computeIfAbsent(buff.buffKey) {
            DetailedBuffStats(id = buff.buffKey, name = buff.buffInfo?.name ?: buffMappingService.getBuffName(buff.buffKey))
        }
        
        castStats.totalCount++
        castStats.startCount++  // 시작 횟수 추적
        castStats.currentStack = buff.stack
        castStats.maxStack = max(castStats.maxStack, buff.stack)
        
        // 스택 합계 추가 및 평균 계산
        castStats.totalStackSum += buff.stack
        castStats.avgStack = castStats.totalStackSum.toDouble() / castStats.totalCount
        
        val currentTime = System.currentTimeMillis()
        if (castStats.firstAppliedTime <= 0L) {
            castStats.firstAppliedTime = currentTime
        }
        castStats.lastAppliedTime = currentTime
        
        // 현재 세션 시작 시간 설정
        castStats.currentSessionStart = buff.startTime
        
        stats.lastUpdateTime = currentTime
    }
    
    /**
     * 대상 통계 업데이트
     */
    private fun updateTargetStats(targetId: Long, buff: BuffInstance) {
        val stats = getOrCreateUserStats(targetId)
        val receivedStats = stats.receivedBuffStats.computeIfAbsent(buff.buffKey) {
            DetailedBuffStats(id = buff.buffKey, name = buff.buffInfo?.name ?: buffMappingService.getBuffName(buff.buffKey))
        }
        
        receivedStats.totalCount++
        receivedStats.startCount++  // 시작 횟수 추적
        receivedStats.currentStack = buff.stack
        receivedStats.maxStack = max(receivedStats.maxStack, buff.stack)
        
        // 스택 합계 추가 및 평균 계산
        receivedStats.totalStackSum += buff.stack
        receivedStats.avgStack = receivedStats.totalStackSum.toDouble() / receivedStats.totalCount
        
        val currentTime = System.currentTimeMillis()
        if (receivedStats.firstAppliedTime == 0L) {
            receivedStats.firstAppliedTime = currentTime
        }
        receivedStats.lastAppliedTime = currentTime
        
        // 현재 세션 시작 시간 설정
        receivedStats.currentSessionStart = buff.startTime
        
        stats.lastUpdateTime = currentTime
    }
    
    /**
     * 유저 통계 가져오기 또는 생성
     */
    private fun getOrCreateUserStats(userId: Long): UserBuffStats {
        return userBuffStats.computeIfAbsent(userId) { 
            UserBuffStats(userId = userId)
        }
    }
    
    /**
     * 현재 세션 종료 (지속시간 누적)
     */
    private fun endCurrentSession(buff: BuffInstance, duration: Long) {
        
        // 시전자 통계 업데이트
        val casterStats = getOrCreateUserStats(buff.userId)
        casterStats.castBuffStats[buff.buffKey]?.let { castStats ->
            if (castStats.currentSessionStart > 0) {
                // Duration 누적
                castStats.totalDuration += duration
                castStats.currentSessionStart = 0  // 세션 종료 표시
                castStats.currentStack = 0
                
                logger.debug { 
                    "Buff ${buff.buffKey} ended for caster. Duration: ${duration}ms" 
                }
            }
        }
        
        // 대상 통계 업데이트
        val targetStats = getOrCreateUserStats(buff.targetId)
        targetStats.receivedBuffStats[buff.buffKey]?.let { receivedStats ->
            if (receivedStats.currentSessionStart > 0) {
                // Duration 누적
                receivedStats.totalDuration += duration
                receivedStats.currentSessionStart = 0  // 세션 종료 표시
                receivedStats.currentStack = 0
                
                logger.debug { 
                    "Buff ${buff.buffKey} ended for target. Duration: ${duration}ms" 
                }
            }
        }
    }

    
    /**
     * 유저별 버프 통계 조회 (가동률 계산 포함)
     * 
     * @param userId 유저 ID
     * @param combatStartTime 전투 시작 시간
     * @param combatEndTime 전투 종료 시간
     * @return 가동률이 계산된 버프 통계
     */
    fun getUserBuffStatsWithUptime(
        userId: Long,
        combatStartTime: Long,
        combatEndTime: Long
    ): UserBuffStats? {
        val stats = userBuffStats[userId] ?: return null
        val currentTime = System.currentTimeMillis()
        
        // 복사본 생성하여 원본 데이터를 보호
        val resultStats = UserBuffStats(
            userId = stats.userId,
            lastUpdateTime = stats.lastUpdateTime
        )
        
        // 시전한 버프들의 가동률 계산 및 실시간 duration 반영
        stats.castBuffStats.forEach { (buffKey, buffStats) ->
            val copiedStats = buffStats.copy()
            
            // 활성 세션이 있으면 현재까지의 duration을 누적된 duration에 추가
            if (copiedStats.currentSessionStart > 0) {
                val currentSessionDuration = currentTime - copiedStats.currentSessionStart
                copiedStats.totalDuration += currentSessionDuration
            }
            
            copiedStats.uptimePercent = calculateBuffUptime(copiedStats, combatStartTime, combatEndTime)
            resultStats.castBuffStats[buffKey] = copiedStats
        }
        
        // 받은 버프들의 가동률 계산 및 실시간 duration 반영
        stats.receivedBuffStats.forEach { (buffKey, buffStats) ->
            val copiedStats = buffStats.copy()
            
            if (copiedStats.currentSessionStart > 0) {
                val currentSessionDuration = currentTime - copiedStats.currentSessionStart
                copiedStats.totalDuration += currentSessionDuration
            }
            
            copiedStats.uptimePercent = calculateBuffUptime(copiedStats, combatStartTime, combatEndTime)
            resultStats.receivedBuffStats[buffKey] = copiedStats
        }
        
        return resultStats
    }
    
    /**
     * 유저별 버프 통계 조회 (buffMappingService에 등록된 버프만 반환)
     */
    fun getUserBuffStatsFiltered(userId: Long): UserBuffStats? {
        val stats = userBuffStats[userId] ?: return null
        
        // Create a filtered copy
        val filteredStats = UserBuffStats(
            userId = stats.userId,
            lastUpdateTime = stats.lastUpdateTime
        )
        
        // Filter cast buffs - only include buffs that exist in buffMappingService
        // Create a copy of the map to avoid ConcurrentModificationException
        val castBuffsCopy = stats.castBuffStats.toMap()
        castBuffsCopy.forEach { (buffKey, buffStats) ->
            if (buffMappingService.hasBuffInfo(buffKey)) {
                filteredStats.castBuffStats[buffKey] = buffStats
            } else {
                logger.debug { "Filtering out unknown cast buff: $buffKey" }
            }
        }
        
        // Filter received buffs - only include buffs that exist in buffMappingService
        // Create a copy of the map to avoid ConcurrentModificationException
        val receivedBuffsCopy = stats.receivedBuffStats.toMap()
        receivedBuffsCopy.forEach { (buffKey, buffStats) ->
            if (buffMappingService.hasBuffInfo(buffKey)) {
                filteredStats.receivedBuffStats[buffKey] = buffStats
            } else {
                logger.debug { "Filtering out unknown received buff: $buffKey" }
            }
        }

        return filteredStats
    }
    
    /**
     * 모든 유저의 버프 통계 조회
     */
    fun getAllUserBuffStats(): Map<Long, UserBuffStats> {
        return userBuffStats.toMap()
    }
    
    /**
     * 유저 버프 요약 정보 조회
     */
    fun getUserBuffSummary(userId: Long): UserBuffSummaryResponse {
        val stats = userBuffStats[userId] 
            ?: return UserBuffSummaryResponse.empty(userId)
        
        // 시전한 버프 요약
        val castBuffsSummary = stats.castBuffStats.map { (buffKey, buffStats) ->
            BuffSummaryInfo(
                buffKey = buffKey,
                totalCount = buffStats.totalCount,
                startCount = buffStats.startCount,
                updateCount = buffStats.updateCount,
                refreshCount = buffStats.refreshCount,
                totalDuration = buffStats.totalDuration,
                avgStack = buffStats.avgStack,
                maxStack = buffStats.maxStack,
                firstAppliedTime = buffStats.firstAppliedTime,
                lastAppliedTime = buffStats.lastAppliedTime,
                currentSessionStart = buffStats.currentSessionStart
            )
        }
        
        // 받은 버프 요약
        val receivedBuffsSummary = stats.receivedBuffStats.map { (buffKey, buffStats) ->
            BuffSummaryInfo(
                buffKey = buffKey,
                totalCount = buffStats.totalCount,
                startCount = buffStats.startCount,
                updateCount = buffStats.updateCount,
                refreshCount = buffStats.refreshCount,
                totalDuration = buffStats.totalDuration,
                avgStack = buffStats.avgStack,
                maxStack = buffStats.maxStack,
                firstAppliedTime = buffStats.firstAppliedTime,
                lastAppliedTime = buffStats.lastAppliedTime,
                currentSessionStart = buffStats.currentSessionStart
            )
        }
        
        return UserBuffSummaryResponse(
            userId = userId,
            lastUpdateTime = stats.lastUpdateTime,
            castBuffsSummary = castBuffsSummary,
            receivedBuffsSummary = receivedBuffsSummary
        )
    }
    
    /**
     * 버프 가동률 계산
     * 전투 시간 대비 버프 활성 시간의 비율을 계산
     * 
     * @param buffStats 버프 통계
     * @param combatStartTime 전투 시작 시간
     * @param combatEndTime 전투 종료 시간
     * @return 가동률 (0.0 ~ 100.0)
     */
    fun calculateBuffUptime(
        buffStats: DetailedBuffStats,
        combatStartTime: Long,
        combatEndTime: Long
    ): Double {
        // 전투가 시작되지 않았거나 유효하지 않은 경우
        if (combatStartTime <= 0 || combatEndTime <= combatStartTime) {
            return 0.0
        }
        
        val combatDuration = combatEndTime - combatStartTime
        
        // 실제 버프 활성 시간 계산 (이미 getUserBuffStatsWithUptime에서 현재 세션 포함해서 계산됨)
        val totalActiveTime = buffStats.totalDuration
        
        // 전투 시간과 겹치는 버프 활성 시간만 계산하는 간단한 로직
        // 복잡한 세션별 계산 대신 전체 duration을 사용
        // (실제로는 각 세션의 시작/종료 시간을 추적해야 정확하지만, 현재 구조에서는 불가능)
        
        // 가동률 계산 (최대 100%)
        return minOf(100.0, (totalActiveTime.toDouble() / combatDuration) * 100)
    }
    
    /**
     * 모든 통계 초기화
     */
    fun clearAllStats() {
        userBuffStats.clear()
        activeBuffInstances.clear()
        logger.info { "Cleared all user buff statistics" }
    }

    /**
     * 받은 버프 통계 업데이트
     */
    private fun updateReceivedBuffStats(buff: BuffInstance, oldStack: Int) {
        val currentTime = System.currentTimeMillis()
        val targetStats = getOrCreateUserStats(buff.targetId)
        targetStats.receivedBuffStats[buff.buffKey]?.let { receivedStats ->
            // Update 시에는 duration을 건드리지 않음
            // Duration은 버프 시작과 종료 시점에만 계산
            
            receivedStats.totalCount++
            receivedStats.updateCount++  // 업데이트 횟수 추적
            receivedStats.currentStack = buff.stack
            receivedStats.maxStack = max(receivedStats.maxStack, buff.stack)
            
            // 스택 합계 추가 및 평균 즉시 계산
            receivedStats.totalStackSum += buff.stack
            receivedStats.avgStack = receivedStats.totalStackSum.toDouble() / receivedStats.totalCount
            
            // 마지막 적용 시간 업데이트
            receivedStats.lastAppliedTime = currentTime
        }
    }
    
    /**
     * 레이드 종료 시 모든 활성 버프 세션 종료
     * 레이드가 끝났을 때 호출되어 모든 진행 중인 버프 세션을 정리합니다.
     */
    fun finalizeAllBuffSessions() {
        val currentTime = System.currentTimeMillis()
        logger.info { "Finalizing all active buff sessions at raid end" }
        
        userBuffStats.forEach { (userId, stats) ->
            // 시전한 버프 세션 종료
            stats.castBuffStats.forEach { (buffKey, buffStats) ->
                if (buffStats.currentSessionStart > 0) {
                    val sessionDuration = currentTime - buffStats.currentSessionStart
                    buffStats.totalDuration += sessionDuration
                    buffStats.currentSessionStart = 0  // 세션 종료 표시
                    buffStats.currentStack = 0  // 현재 스택 0으로 설정
                    
                    logger.debug { 
                        "Finalized cast buff $buffKey for user $userId. Session duration: ${sessionDuration}ms" 
                    }
                }
            }
            
            // 받은 버프 세션 종료
            stats.receivedBuffStats.forEach { (buffKey, buffStats) ->
                if (buffStats.currentSessionStart > 0) {
                    val sessionDuration = currentTime - buffStats.currentSessionStart
                    buffStats.totalDuration += sessionDuration
                    buffStats.currentSessionStart = 0  // 세션 종료 표시
                    buffStats.currentStack = 0  // 현재 스택 0으로 설정
                    
                    logger.debug { 
                        "Finalized received buff $buffKey for user $userId. Session duration: ${sessionDuration}ms" 
                    }
                }
            }
            
            stats.lastUpdateTime = currentTime
        }
        
        logger.info { "All active buff sessions finalized" }
    }
}