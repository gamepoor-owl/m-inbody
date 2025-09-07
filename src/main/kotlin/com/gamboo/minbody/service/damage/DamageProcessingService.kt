package com.gamboo.minbody.service.damage

import com.gamboo.minbody.rest.dto.response.*
import com.gamboo.minbody.service.buff.BuffProcessingService
import jakarta.annotation.PostConstruct
import mu.KotlinLogging
import org.springframework.stereotype.Service

/**
 * 게임 패킷을 처리하여 데미지 통계를 계산하는 서비스
 *
 * 리팩토링되어 단일 책임 원칙을 적용하고 사용성을 향상시켰습니다.
 *
 * 주요 기능:
 * 1. 패킷 처리 오케스트레이션
 * 2. 각 컴포넌트간 데이터 전달 및 조정
 * 3. 전체 시스템 직접 액세스 제공
 */
@Service
class DamageProcessingService(
    private val skillNameResolver: SkillNameResolver,
    private val statisticsCalculator: DamageStatisticsCalculator,
    private val raidModeManager: RaidModeManager,
    private val buffProcessingService: BuffProcessingService,
) {

    private val logger = KotlinLogging.logger {}

    /**
     * Python의 p_hpdff와 동일한 역할
     * 마지막으로 발생한 HP 감소 패킷을 저장하고, 다음 Attack 패킷과 매칭
     */
    private var pendingHpChange: HpChangedPacket? = null

    /**
     * CurrentHpPacket을 사용한 HP 추적
     * targetId별로 이전 HP를 저장하여 변화량 계산
     */
    private val targetHpMap = mutableMapOf<Long, Long>()

    @PostConstruct
    fun init() {
        // 60초 타임아웃 시 jobMapping과 버프 데이터도 초기화하도록 콜백 등록
        statisticsCalculator.onCombatTimeout = {
            logger.info { "Combat timeout: Clearing job mapping and resetting buff stats" }
            skillNameResolver.jobMapping.clear()
            raidModeManager.clearAll()
            // 버프 통계만 초기화 (활성 버프는 유지하되 시작 시간 리셋)
            buffProcessingService.clearBuffStats()
        }
    }

    // ========== 컴포넌트 데이터에 대한 직접 액세스 제공 ==========

    /** 유저별 전체 데미지 통계 */
    val damageStats get() = statisticsCalculator.damageStats

    /** 타겟별 데미지 통계 */
    val damageByTarget get() = statisticsCalculator.damageByTarget

    /** 유저별 스킬별 데미지 통계 */
    val damageByUserBySkill get() = statisticsCalculator.damageByUserBySkill

    /** 유저별 자가 데미지 통계 */
    val selfDamageByUser get() = statisticsCalculator.selfDamageByUser

    /** DPS 계산용 데이터 */
    val damageForDPS get() = statisticsCalculator.damageForDPS

    /** 자가 데이터 */
    val selfData get() = statisticsCalculator.selfData

    /** DPS 시간 추적 */
    val timeForDPS get() = statisticsCalculator.timeForDPS

    /** 직업 매핑 */
    val jobMapping get() = skillNameResolver.jobMapping

    /**
     * 패킷 리스트를 처리하여 데미지 통계를 업데이트
     *
     * Python 프로젝트와 동일한 로직으로 구현:
     * - 단순한 HP Change 매칭 (마지막 HP 변화만 추적)
     * - target_id 기반 Attack 패킷 매칭
     * - 허수아비 모드에서 SelfDamage -> HP Change 변환
     *
     * @param packets 처리할 패킷 리스트
     * @param singleMode 허수아비 모드 여부 (true시 HP Change 무시하고 SelfDamage 사용)
     * @param raidMode 레이드 모드 여부
     * @return 처리된 패킷 리스트
     */
    fun processPackets(packets: List<PacketData>): List<PacketData> {
        val processedPackets = mutableListOf<PacketData>()

        packets.forEach { packet ->
            when (packet) {
                is HpChangedPacket -> {
                    /**
                     * HP 변화 패킷 처리 (Python 로직과 동일)
                     *
                     * 처리 규칙:
                     * 1. HP가 감소한 경우만 처리 (prevHp > currentHp)
                     * 2. 허수아비 모드가 아닐 때만 저장 (!singleMode)
                     * 3. 마지막 HP 변화만 유지 (다음 Attack 패킷과 매칭용)
                     */
//                    if (packet.prevHp > packet.currentHp && !singleMode) {
//                        val damage = packet.prevHp - packet.currentHp
//                        logger.info { "[HP_CHANGED] Target: ${packet.targetId}, PrevHP: ${packet.prevHp}, CurrentHP: ${packet.currentHp}, Damage: $damage, Time: ${System.currentTimeMillis()}" }
//                        pendingHpChange = packet
//                    } else {
//                        if (singleMode) {
//                            logger.debug { "[HP_CHANGED] Ignored (singleMode=true)" }
//                        } else if (packet.prevHp <= packet.currentHp) {
//                            logger.debug { "[HP_CHANGED] Ignored (no damage: ${packet.prevHp} -> ${packet.currentHp})" }
//                        }
//                        pendingHpChange = null
//                    }
                    processedPackets.add(packet)
                }

                is CurrentHpPacket -> {
                    /**
                     * CurrentHpPacket을 사용한 HP 변화 추적
                     *
                     * 처리 규칙:
                     * 1. targetId별로 이전 HP를 저장
                     * 2. 이전 HP가 있으면 변화량 계산
                     * 3. 첫 번째 패킷은 초기값 설정만 (데미지 계산 X)
                     * 4. HP가 감소한 경우만 pendingHpChange로 설정
                     */
//                    val targetId = packet.targetId
//                    val currentHp = packet.currentHp
//                    val previousHp = targetHpMap[targetId]
//
//                    if (previousHp != null) {
//                        // 이전 HP가 있는 경우 - 변화량 계산
//                        if (previousHp > currentHp) {
//                            val damage = previousHp - currentHp
////                            logger.info { "[CURRENT_HP] Target: $targetId, PrevHP: $previousHp, CurrentHP: $currentHp, Damage: $damage" }
//
//                            // HpChangedPacket처럼 동작하도록 설정
//                            pendingHpChange = HpChangedPacket(
//                                targetId = targetId,
//                                prevHp = previousHp,
//                                currentHp = currentHp
//                            )
//                        } else if (previousHp < currentHp) {
//                            logger.debug { "[CURRENT_HP] Target: $targetId healed from $previousHp to $currentHp" }
//                            pendingHpChange = null
//                        }
//                    } else if (previousHp == null) {
//                        // 첫 번째 패킷 - 초기값 설정
//                        logger.debug { "[CURRENT_HP] Initial HP for target $targetId: $currentHp" }
//                    }
//
//                    // 현재 HP를 저장 (다음 패킷 처리를 위해)
//                    targetHpMap[targetId] = currentHp
                    processedPackets.add(packet)
                }

                is SelfDamagePacket -> {
                    /**
                     * 자가 데미지 패킷 처리
                     *
                     * 처리 규칙:
                     * 1. 이상값 필터링: 2095071572 초과 시 무시 (Python과 동일)
                     * 2. 일반 모드: selfDamageByUser에 집계
                     * 3. 허수아비 모드: SelfDamage를 HP Change로 변환하여 Attack 패킷과 매칭 가능하도록 함
                     */
                    logger.debug { "SelfDamage packet received: userId=${packet.userId}, targetId=${packet.targetId}, damage=${packet.damage}" }
                    
                    if (packet.damage > 2095071572) {
                        logger.warn { "SelfDamage packet filtered due to abnormal damage: ${packet.damage}" }
                        processedPackets.add(packet)
                        return@forEach
                    }

                    statisticsCalculator.processSelfDamage(packet.userId, packet.targetId, packet.damage)
                    // 허수아비 모드: SelfDamage를 HP Change로 변환
                    pendingHpChange = HpChangedPacket(
                        targetId = packet.targetId,
                        prevHp = packet.damage,
                        currentHp = 0
                    )

                    processedPackets.add(packet)
                    logger.debug { "SelfDamage packet processed successfully" }
                }

                is AttackPacket -> {
                    /**
                     * 공격 패킷 처리 (Python 로직과 동일)
                     *
                     * 처리 규칙:
                     * 1. pendingHpChange가 존재해야 함
                     * 2. target_id가 일치해야 함
                     * 3. 데미지가 0보다 커야 함
                     * 4. 조건 만족 시 데미지 통계 업데이트
                     */

                    if (pendingHpChange != null && packet.targetId == pendingHpChange!!.targetId) {
                        val damage = pendingHpChange!!.prevHp - pendingHpChange!!.currentHp
                        if (damage > 0) {
                            val skillName = skillNameResolver.resolveSkillName(packet.key1, packet.flags)

                            // 버프 효과 적용
                            val buffEffects = buffProcessingService.calculateCurrentBuffEffects(packet.userId)
                            val finalDamage = (damage * (1 + buffEffects.dmgBonus)).toLong()

//                            logger.info { "[ATTACK_MATCHED] User: ${packet.userId} -> Target: ${packet.targetId}, HP_Damage: $damage, BuffedDamage: $finalDamage, Skill: $skillName" }

                            statisticsCalculator.processDamage(
                                finalDamage,
                                packet.userId,
                                packet.targetId,
                                packet.flags,
                                skillName
                            )

                            // Clear pendingHpChange after use to prevent reuse
                            pendingHpChange = null
                        }
                    }
                    processedPackets.add(packet)
                }

                is ActionPacket -> {
                    /**
                     * 액션 패킷 처리
                     *
                     * 스킬 사용 시 발생하는 패킷으로 다음 정보를 추출:
                     * 1. 스킬 ID (key1) -> 스킬명 매핑
                     * 2. 유저 ID -> 직업명 매핑
                     *
                     * 이 정보는 나중에 Attack 패킷 처리 시 스킬명 결정에 사용됨
                     */
                    skillNameResolver.processActionPacket(packet.userId, packet.skillName, packet.key1)

                    // 보스 스킬 카운트 처리 (리스너가 WebSocket 브로드캐스트 담당)
                    raidModeManager.processBossSkillCount(packet.userId, packet.skillName)

                    processedPackets.add(packet)
                }

                is ItemUsePacket -> {
                    /**
                     * 아이템 사용 패킷 처리
                     *
                     * 유저가 아이템을 사용했을 때 발생하는 패킷
                     * 웹소켓으로 클라이언트에 전송됨
                     */
                    processedPackets.add(packet)
                }

                is DieBlowPacket -> {
                    /**
                     * Die_Blow 패킷 처리
                     *
                     * 레이드 모드에서 Die_Blow의 userId가:
                     * 1. 플레이어(mappedUserIds)가 아니고
                     * 2. 첫 번째 Attack 패킷의 userId 또는 targetId와 일치하는 경우 레이드 종료
                     */
                    raidModeManager.processDieBlowPacket(packet.userId)
                    processedPackets.add(packet)
                }

                // 버프 패킷 처리
                is BuffStartPacket, is BuffUpdatePacket, is BuffEndPacket -> {
                    /**
                     * 버프 패킷 처리
                     *
                     * 버프 생성, 갱신, 종료 시 버프 상태를 관리하고 통계를 수집
                     */
                    buffProcessingService.processBuffPacket(packet)
                    processedPackets.add(packet)
                }

                is BossPacket -> {
                    val currentBossId = raidModeManager.getBossId()
                    if (currentBossId != packet.bossId) {

                        if (currentBossId != null) {
                            // 보스 ID가 변경되면 = 실제 전투 시작
                            logger.info { "Boss ID changed from $currentBossId to ${packet.bossId}, resetting buff stats while preserving active buffs" }
                            // 버프 통계 초기화 (활성 버프는 유지하되 시작 시간을 현재로 리셋)
                            buffProcessingService.clearBuffStats()
                        }
                        raidModeManager.setBossId(packet.bossId)
                        logger.info { "New boss ID set: ${packet.bossId}" }
                    }
                }
            }
        }

        return processedPackets
    }

    /**
     * 레이드 종료 이벤트 리스너 설정
     */
    fun setOnRaidEndListener(listener: () -> Unit) {
        raidModeManager.setOnRaidEndListener(listener)
    }

    /**
     * 보스 스킬 이벤트 리스너 설정
     */
    fun setOnBossSkillListener(listener: (skillName: String) -> Unit) {
        raidModeManager.setOnBossSkillListener(listener)
    }

    /**
     * 모든 데이터 초기화
     *
     * @param preserveRecentBuffs true일 경우 3초 이내의 버프는 보존 (기본값: false)
     */
    fun clearAll(preserveRecentBuffs: Boolean = false) {
        pendingHpChange = null
        targetHpMap.clear()  // CurrentHpPacket 추적 맵 초기화
        skillNameResolver.clearAll()
        statisticsCalculator.clearAll()
        raidModeManager.clearAll()

        if (preserveRecentBuffs) {
            // 버프 통계만 초기화 (활성 버프는 유지하되 시작 시간 리셋)
            buffProcessingService.clearBuffStats()
        } else {
            // 모든 버프 초기화
            buffProcessingService.clearAll()
        }
    }
}