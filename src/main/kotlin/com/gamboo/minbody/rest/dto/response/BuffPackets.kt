package com.gamboo.minbody.rest.dto.response

/**
 * 버프 시작 패킷 (타입: 100046)
 * 버프가 생성되거나 적용될 때 발생
 *     BUFF_START(100048),      // 버프 생성/적용
 *     BUFF_UPDATE(100051),     // 버프 갱신
 *     BUFF_END(100049);
 */
data class BuffStartPacket(
    override val type: Int = 100048,
    override val hide: Boolean = false,
    val instKey: String,        // 버프 인스턴스 고유 키 (8바이트 hex)
    val buffKey: Long,          // 버프 종류 코드
    val userId: Long,           // 시전자 ID
    val targetId: Long,         // 대상 ID
    val stack: Int,             // 스택 수
    val flags: Long             // 추가 플래그 정보
) : PacketData()

/**
 * 버프 업데이트 패킷 (타입: 100049)
 * 버프 스택이 변경될 때 발생
 */
data class BuffUpdatePacket(
    override val type: Int = 100051,
    override val hide: Boolean = false,
    val instKey: String,        // 버프 인스턴스 고유 키 (8바이트 hex)
    val buffKey: Long,          // 버프 종류 코드
    val userId: Long,           // 시전자 ID
    val targetId: Long,         // 대상 ID
    val stack: Int,             // 변경된 스택 수
    val flags: Long             // 추가 플래그 정보
) : PacketData()

/**
 * 버프 종료 패킷 (타입: 100047)
 * 버프가 종료될 때 발생
 */
data class BuffEndPacket(
    override val type: Int = 100049,
    override val hide: Boolean = false,
    val instKey: String,        // 버프 인스턴스 고유 키 (8바이트 hex)
    val userId: Long,           // 시전자 ID
    val flags: Long             // 추가 플래그 정보
) : PacketData()