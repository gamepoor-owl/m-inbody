package com.gamboo.minbody.constants

//enum class PacketType(val code: Int) {
//    ATTACK(10308),
//    ACTION(100041),
//    HP_CHANGED(100178),
//    SELF_DAMAGE(10719),
//    ITEM_321(100321),
//    DIE_BLOW(100040),
//
//    // 버프 관련 패킷
//    BUFF_START(100046),      // 버프 생성/적용
//    BUFF_UPDATE(100049),     // 버프 갱신
//    BUFF_END(100047);        // 버프 종료
//
//    companion object {
//        private val map = entries.associateBy(PacketType::code)
//        fun fromCode(code: Int) = map[code]
//    }
//}


enum class PacketType(val code: Int) {
    ATTACK(20318),           // C# 코드와 일치 (20318이 맞음)
    ACTION(100043),
    HP_CHANGED(100172),
    SELF_DAMAGE(20741),
    ITEM_321(100321),
    DIE_BLOW(100042),
    CURRENT_HP(100180),

    BOSS1(100181),
    BOSS2(100182),
    BOSS3(100183),
    BOSS4(100184),
    BOSS5(100185),

    // 버프 관련 패킷
    BUFF_START(100048),      // 버프 생성/적용
    BUFF_UPDATE(100051),     // 버프 갱신
    BUFF_END(100049);        // 버프 종료

    companion object {
        private val map = entries.associateBy(PacketType::code)
        fun fromCode(code: Int) = map[code]
    }
}