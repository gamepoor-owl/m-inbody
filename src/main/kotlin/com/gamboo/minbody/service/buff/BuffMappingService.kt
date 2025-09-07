package com.gamboo.minbody.service.buff

import com.gamboo.minbody.client.MCenterClient
import com.gamboo.minbody.model.BuffEffect
import com.gamboo.minbody.model.BuffInfo
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

/**
 * 버프 정보 매핑 서비스
 * JSON 파일에서 버프 정보를 로드하고 버프 코드와 효과를 매핑
 */
@Service
class BuffMappingService(
    private val mCenterClient: MCenterClient
) {
    
    private val logger = KotlinLogging.logger {}
    
    // 버프 코드 -> 버프 정보 매핑
    private val buffInfoMap = ConcurrentHashMap<Long, BuffInfo>()
    
    /**
     * 서비스 초기화 시 버프 정보 로드
     * MCenter에서 버프 정보를 가져옴
     */
    init {
        loadBuffDataFromMCenter()
    }
    
    /**
     * MCenter에서 버프 정보 로드
     */
    private fun loadBuffDataFromMCenter() {
        try {
            val response = mCenterClient.getBuffs()
            response.data.forEach { buff ->
                buffInfoMap[buff.buffId] = BuffInfo(
                    name = buff.buffName,
                    type = buff.type,
                    category = buff.category,
                    isExclude = buff.isExclude,
                    effect = BuffEffect() // 효과는 빈 객체로 초기화
                )
            }
            logger.info { "Loaded ${buffInfoMap.size} buff definitions from MCenter" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to load buffs from MCenter, service will continue with empty buff data" }
        }
    }
    
    /**
     * 버프 이름 조회
     * 
     * @param buffKey 버프 코드
     * @return 버프 이름 (없으면 "Unknown Buff (코드)")
     */
    fun getBuffName(buffKey: Long): String {
        return buffInfoMap[buffKey]?.name ?: "$buffKey"
    }

    fun getBuffByKey(buffKey: Long): BuffInfo? {
        return buffInfoMap[buffKey]
    }
    
    /**
     * 버프 효과 조회
     * 
     * @param buffKey 버프 코드
     * @return 버프 효과 (없으면 빈 효과)
     */
    fun getBuffEffect(buffKey: Long): BuffEffect {
        return buffInfoMap[buffKey]?.effect ?: BuffEffect()
    }
    
    /**
     * 버프 정보 존재 여부 확인
     * 
     * @param buffKey 버프 코드
     * @return 버프 정보 존재 여부
     */
    fun hasBuffInfo(buffKey: Long): Boolean {
        return buffInfoMap.containsKey(buffKey)
    }
    
    /**
     * 모든 버프 정보 조회
     * 
     * @return 전체 버프 정보 맵
     */
    fun getAllBuffInfo(): Map<Long, BuffInfo> {
        return buffInfoMap.toMap()
    }
    
    /**
     * 버프 정보 갱신 (런타임 중 동적 로드용)
     * 
     * @param buffKey 버프 코드
     * @param buffInfo 버프 정보
     */
    fun updateBuffInfo(buffKey: Long, buffInfo: BuffInfo) {
        buffInfoMap[buffKey] = buffInfo
        logger.debug { "Updated buff info for key $buffKey: ${buffInfo.name}" }
    }
}