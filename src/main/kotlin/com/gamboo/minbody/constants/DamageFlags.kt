package com.gamboo.minbody.constants

data class DamageFlag(
    val index: Int,
    val name: String,
    val mask: Int
)

object DamageFlags {
    val FLAGS = listOf(
        DamageFlag(0, "crit_flag", 0x01),
        DamageFlag(0, "what1", 0x02),
        DamageFlag(0, "unguarded_flag", 0x04),
        DamageFlag(0, "break_flag", 0x08),
        
        DamageFlag(0, "what05", 0x10),
        DamageFlag(0, "what06", 0x20),
        DamageFlag(0, "first_hit_flag", 0x40),
        DamageFlag(0, "default_attack_flag", 0x80),
        
        DamageFlag(1, "multi_attack_flag", 0x01),
        DamageFlag(1, "power_flag", 0x02),
        DamageFlag(1, "fast_flag", 0x04),
        DamageFlag(1, "dot_flag", 0x08),
        
        DamageFlag(1, "dot_flag2", 0x80),
//        DamageFlag(1, "pet_flag", 0x88),
//
        DamageFlag(2, "dot_flag3", 0x01),
        
        DamageFlag(3, "add_hit_flag", 0x08),
        
        DamageFlag(3, "bleed_flag", 0x10),
        DamageFlag(3, "dark_flag", 0x20),
        DamageFlag(3, "fire_flag", 0x40),
        DamageFlag(3, "holy_flag", 0x80),
        
        DamageFlag(4, "ice_flag", 0x01),
        DamageFlag(4, "electric_flag", 0x02),
        DamageFlag(4, "poison_flag", 0x04),
        DamageFlag(4, "mind_flag", 0x08),
        
        DamageFlag(4, "dot_flag4", 0x10)
    )
    
    fun extractFlags(flags: ByteArray): Map<String, Boolean> {
        return FLAGS.associate { flag ->
            val value = if (flag.index < flags.size) {
                (flags[flag.index].toInt() and flag.mask) != 0
            } else {
                false
            }
            flag.name to value
        }
    }
}