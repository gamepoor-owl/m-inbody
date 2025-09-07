package com.gamboo.minbody.constants

/**
 * 버프 관련 상수
 */
object BuffConstants {
    
    /**
     * 버프 패킷 타입
     */
    object PacketTypes {
        const val BUFF_START = 100046
        const val BUFF_END = 100047
        const val BUFF_UPDATE = 100049
    }
    
    /**
     * 버프 타입별 카테고리
     */
    object BuffCategories {
        const val LEGENDARY = "(전설)"
        const val EPIC = "(에픽)"
        const val CLASS_GREAT_SWORD = "(대검)"
        const val CLASS_SWORD = "(검술)"
        const val CLASS_MAGE = "(법사)"
        const val CLASS_ELECTRIC = "(전격)"
        const val CLASS_HEALER = "(힐러)"
        const val CLASS_SYNERGY = "(시너지)"
        const val CLASS_MONK = "(수도)"
        const val CLASS_CROSSBOW = "(석궁)"
        const val CLASS_LONGBOW = "(장궁)"
        const val CLASS_BARD = "(악사)"
        const val CLASS_ROGUE = "(도적)"
        const val CLASS_DUAL_BLADE = "(듀블)"
    }
    
    /**
     * 버프 효과 타입
     */
    object EffectTypes {
        const val ATTACK_BONUS = "atkBonus"
        const val DAMAGE_BONUS = "dmgBonus"
        const val DEFENSE_BONUS = "defBonus"
        const val SPEED_BONUS = "spdBonus"
    }
    
    /**
     * 기본값
     */
    object Defaults {
        const val DEFAULT_STACK = 0
        const val DEFAULT_BONUS = 0.0
        const val CLEANUP_TIMEOUT_MS = 300_000L // 5분
    }
    
    /**
     * 계산 관련 상수
     */
    object Calculations {
        const val MILLIS_TO_SECONDS = 1000.0
        const val PERCENTAGE_MULTIPLIER = 100.0
    }
}