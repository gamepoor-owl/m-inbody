package com.gamboo.minbody.service.damage

import com.gamboo.minbody.service.buff.UserBuffStatisticsService
import mu.KotlinLogging
import org.springframework.stereotype.Component

/**
 * 레이드 모드 관리를 담당하는 컴포넌트
 *
 * 주요 기능:
 * 1. 레이드 세션별 보스 ID 관리
 * 2. 레이드 종료 감지 및 이벤트 처리
 * 3. 세션별 첫 Attack 패킷 정보 추적
 */
@Component
class RaidModeManager(
    private val userBuffStatisticsService: UserBuffStatisticsService
) {

    private val logger = KotlinLogging.logger {}

    /** 레이드 종료 이벤트 리스너 */
    private var onRaidEndListener: (() -> Unit)? = null
    private var onRaidEndByCenterListener: (() -> Unit)? = null
    
    /** 보스 스킬 이벤트 리스너 */
    private var onBossSkillListener: ((skillName: String) -> Unit)? = null

    /** 레이드 모드에서의 보스 ID (로컬 실행이므로 단일 변수) */
    private var bossId: Long? = null
    var bossSkillCount: MutableMap<String, Long> = mutableMapOf()

    /**
     * 레이드 모드 초기화 (레이드 시작 시)
     */
    fun initializeRaidMode() {
        // BOSS ID 초기화
        bossId = null
        bossSkillCount = mutableMapOf()
        logger.info { "Raid mode initialized - waiting for first Attack packet" }
    }

    fun processBossSkillCount(bossId: Long, skillId: String) {

        if(this.bossId != bossId) return

        bossSkillCount[skillId] = bossSkillCount.getOrDefault(skillId, 0) + 1

        logger.info { "Boss skill count for $skillId has been processed" }
        
        // 보스 스킬 이벤트 리스너 호출
        onBossSkillListener?.invoke(skillId)
    }
    /**
     * DieBlowPacket을 통한 레이드 종료 감지
     */
    fun processDieBlowPacket(dieBlowUserId: Long): Boolean {

        // Die_Blow userId가 첫 Attack의 userId 또는 targetId와 일치하는지 확인
        if (dieBlowUserId == bossId) {
            logger.info { "==== RAID END TRIGGER - Die_Blow from Boss (userId: $dieBlowUserId) ====" }
            handleRaidEnd()
            return true
        }

        return false
    }

    /**
     * 레이드 종료 이벤트 처리
     */
    private fun handleRaidEnd() {
        // 레이드 종료 시간 기록
        val raidEndTime = System.currentTimeMillis()
        logger.info { "Raid ended at: $raidEndTime" }
        
        // 모든 활성 버프 세션 종료
        try {
            userBuffStatisticsService.finalizeAllBuffSessions()
            logger.info { "All buff sessions finalized at raid end" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to finalize buff sessions" }
        }

        // 이벤트 리스너 호출
        onRaidEndListener?.invoke()
        
        // Center 전용 리스너 호출 (PlayerRecordSaveService의 save 메서드 실행)
        onRaidEndByCenterListener?.invoke()
    }


    /**
     * 레이드 종료 이벤트 리스너 설정
     */
    fun setOnRaidEndListener(listener: () -> Unit) {
        onRaidEndListener = listener
    }

    fun setOnRaidEndByCenterListener(listener: () -> Unit) {
        onRaidEndByCenterListener = listener
    }
    
    /**
     * 보스 스킬 이벤트 리스너 설정
     */
    fun setOnBossSkillListener(listener: (skillName: String) -> Unit) {
        onBossSkillListener = listener
    }


    fun getBossId(): Long? = bossId

    fun setBossId(targetId: Long) {
        bossId = targetId
        bossSkillCount = mutableMapOf()
    }

    /**
     * 데이터 정리
     */
    fun cleanup() {
        bossId = null
        bossSkillCount = mutableMapOf()
        logger.debug { "Cleaned up raid data" }
    }

    /**
     * 모든 레이드 데이터 초기화
     */
    fun clearAll() {
        bossId = null
        bossSkillCount = mutableMapOf()
    }
}