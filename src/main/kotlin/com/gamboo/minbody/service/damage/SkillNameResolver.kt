package com.gamboo.minbody.service.damage

import com.gamboo.minbody.service.skill.SkillMappingService
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * 스킬명 해석 및 매핑을 담당하는 컴포넌트
 * 
 * 주요 기능:
 * 1. Action 패킷으로부터 동적 스킬명 매핑 수집
 * 2. 스킬 ID를 스킬명으로 변환
 * 3. DOT 및 특수 공격의 스킬명 생성
 */
@Component
class SkillNameResolver(
    private val skillMappingService: SkillMappingService
) {
    
    /** Action 패킷으로부터 수집한 스킬 ID -> 스킬명 매핑 (key1 -> skillName) */
    private val skillKey2Name = ConcurrentHashMap<Long, String>()
    
    /** Action 패킷으로부터 수집한 유저 ID -> 직업명 매핑 (userId -> jobName) */
    val jobMapping = ConcurrentHashMap<Long, String>()
    
    // 성능 최적화: 미리 정의된 상수로 변경
    companion object {
        private val DOT_TYPES = arrayOf(
            "bleed_flag" to "출혈",
            "dark_flag" to "암흑", 
            "fire_flag" to "화상",
            "holy_flag" to "신성",
            "ice_flag" to "빙결",
            "electric_flag" to "감전",
            "poison_flag" to "중독",
            "mind_flag" to "정신"
        )
        
        // 직업명 매핑을 위한 최적화된 맵 (O(1) 검색)
        private val JOB_MAPPING = mapOf(
            "expertwarrior" to "전사",
            "greatsword" to "대검",
            "swordmaster" to "검술",
            "healer" to "힐러",
            "monk" to "수도",
            "priest" to "사제",
            "bard" to "음유",
            "battlemusician" to "악사",
            "dancer" to "댄서",
            "fighter" to "격가",
            "dualblades" to "듀블",
            "highthief" to "도적",
            "highmage" to "법사",
            "firemage" to "화법",
            "icemage" to "빙결",
            "lightningmage" to "전격",
            "higharcher" to "궁수",
            "arbalist" to "석궁",
            "longbowman" to "장궁",
            "novice" to "전사"
        )
        
        // 무시할 스킬명 패턴 (null 반환)
        private val IGNORED_SKILLS = setOf(
            "novicewarrior_shieldbash",
            "defaultattack"
        )
    }
    
    /**
     * Action 패킷을 처리하여 스킬명과 직업명 매핑 정보 수집
     */
    fun processActionPacket(userId: Long, skillName: String, key1: Long) {
        // Build dynamic skill mapping from action packets
        if (key1 != 0L && !skillKey2Name.containsKey(key1) && !skillMappingService.isSkillIgnored(skillName)) {
            val translatedName = skillMappingService.getSkillNameFromAction(skillName) ?: skillName
            skillKey2Name[key1] = translatedName
        }
        
        // Process job mapping based on skill name
        val jobName = extractJobFromSkillName(skillName.lowercase())
        jobName?.let {
            if (!jobMapping.containsKey(userId)) {
                jobMapping[userId] = it
            }
        }
    }
    
    /**
     * 스킬 ID를 기반으로 스킬명 해석
     */
    fun resolveSkillName(key1: Long, flags: Map<String, Boolean>): String {
        val isDot = (flags["dot_flag"] == true && flags["dot_flag2"] == true && 
                    flags["dot_flag3"] == true) || flags["dot_flag4"] == true
        
        return when {
            key1 != 0L -> {
                // key1이 0이 아닌 경우: 스킬 매핑 테이블에서 검색
                // 우선순위: skills2.json -> Action 패킷 매핑 -> skills.json -> key1 숫자
                skillMappingService.getSkillName(key1) ?: skillKey2Name[key1] ?: key1.toString()
            }
            isDot -> {
                // DOT 판정: "(도트) 속성명" 형태로 생성 (예: "(도트) 출혈", "(도트) 무속성")
                buildDotKey(flags)
            }
            else -> {
                // key1이 0이고 DOT가 아닌 경우: "(특수) 속성명" 형태로 생성 (예: "(특수) 암흑", "(특수) 무속성")
                buildSpecialKey(flags)
            }
        }
    }
    
    /**
     * 스킬명으로부터 직업명 추출 (최적화된 맵 기반 검색)
     */
    private fun extractJobFromSkillName(skillName: String): String? {
        // 무시할 스킬명 패턴 먼저 확인
        if (IGNORED_SKILLS.any { it in skillName }) {
            return null
        }
        
        // O(1) 맵 검색으로 직업명 찾기
        return JOB_MAPPING.entries.find { (key, _) -> key in skillName }?.value
    }
    
    /**
     * DOT 공격의 스킬명 생성 (최적화된 버전)
     */
    private fun buildDotKey(flags: Map<String, Boolean>): String {
        return buildAttributeKey("(도트)", flags)
    }
    
    /**
     * 특수 공격의 스킬명 생성 (최적화된 버전)
     */
    private fun buildSpecialKey(flags: Map<String, Boolean>): String {
        return buildAttributeKey("(특수)", flags)
    }
    
    /**
     * 속성 기반 스킬명 생성을 위한 공통 메소드
     */
    private fun buildAttributeKey(prefix: String, flags: Map<String, Boolean>): String {
        val builder = StringBuilder(prefix)
        var hasAttribute = false
        
        // 성능 최적화: 플래그가 true인 것만 처리
        for ((flag, name) in DOT_TYPES) {
            if (flags[flag] == true) {
                if (hasAttribute) builder.append(',') // 여러 속성이 있을 경우 구분자 추가
                builder.append(' ').append(name)
                hasAttribute = true
            }
        }
        
        if (!hasAttribute) builder.append(" 무속성")
        return builder.toString()
    }
    
    /**
     * 현재 활동 중인 모든 직업이 매핑된 유저 ID 목록
     */
    fun getMappedUserIds(): Set<Long> {
        return jobMapping.keys.toSet()
    }
    
    /**
     * 모든 매핑 정보 초기화
     */
    fun clearAll() {
        skillKey2Name.clear()
        jobMapping.clear()
    }
}