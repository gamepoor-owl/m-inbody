package com.gamboo.minbody.service.packet

import com.gamboo.minbody.constants.DamageFlags
import com.gamboo.minbody.constants.PacketType
import com.gamboo.minbody.rest.dto.response.*
import com.gamboo.minbody.service.skill.SkillMappingService
import mu.KotlinLogging
import org.springframework.stereotype.Service
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 게임 패킷을 파싱하여 구조화된 데이터로 변환하는 서비스
 * 
 * 지원하는 패킷 타입 (Python 프로젝트와 동일):
 * 1. ATTACK (10308): 공격 패킷 - 공격자, 대상, 스킬ID, 플래그 정보
 * 2. ACTION (100041): 액션 패킷 - 스킬명, 직업, 스킬ID 매핑 정보
 * 3. HP_CHANGED (100178): HP 변화 패킷 - 대상의 HP 변화량
 * 4. SELF_DAMAGE (10719): 자가 데미지 패킷 - 자신에게 입힌 데미지
 * 
 * 패킷 구조는 리틀 엔디안 바이트 순서를 사용하며,
 * 각 패킷마다 정해진 바이트 오프셋에서 데이터를 추출합니다.
 */
@Service
class PacketParserService(
    private val skillMappingService: SkillMappingService
) {
    
    private val logger = KotlinLogging.logger {}
    
    /**
     * 패킷 타입에 따라 적절한 파서를 호출하여 패킷을 파싱
     * 
     * Python 프로젝트의 parse_dict와 동일한 역할:
     * parse_dict = {
     *     10308: parse_attack,
     *     100041: parse_action,
     *     100178: parse_hp_changed,
     *     10719: parse_self_damage,
     * }
     * 
     * @param type 패킷 타입 코드
     * @param data 패킷 데이터 (바이트 배열)
     * @return 파싱된 패킷 객체 또는 null (지원하지 않는 타입의 경우)
     */
    fun parsePacket(type: Int, data: ByteArray): PacketData? {
        return when (PacketType.fromCode(type)) {
            PacketType.ATTACK -> {
                parseAttack(data)
            }
            PacketType.ACTION -> {
                parseAction(data)
            }
            PacketType.CURRENT_HP -> {
                parseCurrentHp(data)
            }
            PacketType.HP_CHANGED -> {
                parseHpChanged(data)
            }
            PacketType.SELF_DAMAGE -> {
                parseSelfDamage(data)
            }
            PacketType.ITEM_321 -> {
                parseItem321(data)
            }
            PacketType.DIE_BLOW -> {
                parseDieBlow(data)
            }
            PacketType.BUFF_START -> {
                parseBuffStart(data)
            }
            PacketType.BUFF_UPDATE -> {
                parseBuffUpdate(data)
            }
            PacketType.BUFF_END -> {
                parseBuffEnd(data)
            }
            PacketType.BOSS1, PacketType.BOSS2, PacketType.BOSS3, PacketType.BOSS4, PacketType.BOSS5 -> {
                parseBossPacket(data)
            }
            else -> {
                null
            }
        }
    }
    
    /**
     * ATTACK 패킷 파싱 (패킷 타입: 20318)
     * 
     * 패킷 구조 (35바이트, 리틀 엔디안):
     * - 0-3: userId (공격자 ID)
     * - 4-7: p1 (미사용)
     * - 8-11: targetId (대상 ID) 
     * - 12-15: p2 (미사용)
     * - 16-19: key1 (스킬 ID, 0이면 평타/DOT/특수)
     * - 20-23: key2 (보조 스킬 ID)
     * - 24-30: flags (7바이트, 치명타/DOT/추가타 등 플래그)
     * - 31-34: c (미사용)
     * 
     * Python 코드 참조:
     * user_id = int.from_bytes(data[pivot:pivot+4], byteorder='little')
     * target_id = int.from_bytes(data[pivot+8:pivot+12], byteorder='little')
     * key1 = int.from_bytes(data[pivot+16:pivot+20], byteorder='little')
     * flags = data[pivot+24:pivot+31]  # 7 bytes
     * 
     * @param data 35바이트 패킷 데이터
     * @return 파싱된 AttackPacket 또는 null (파싱 실패 시)
     */
    private fun parseAttack(data: ByteArray): AttackPacket? {
        
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        
        // ========== 패킷 데이터 추출 (Python과 동일한 오프셋) ==========
        val userId = buffer.getInt(0).toLong() and 0xFFFFFFFFL    // 공격자 ID
        val p1 = buffer.getInt(4).toLong() and 0xFFFFFFFFL        // 미사용
        val targetId = buffer.getInt(8).toLong() and 0xFFFFFFFFL  // 대상 ID
        val p2 = buffer.getInt(12).toLong() and 0xFFFFFFFFL       // 미사용
        val key1 = buffer.getInt(16).toLong() and 0xFFFFFFFFL     // 주 스킬 ID
        val key2 = buffer.getInt(20).toLong() and 0xFFFFFFFFL     // 보조 스킬 ID
        val flags = data.sliceArray(24..30)                // 플래그 7바이트
        val c = buffer.getInt(31).toLong() and 0xFFFFFFFFL        // 미사용
        
        // ========== 플래그 추출 (치명타, DOT, 추가타 등) ==========
        val extractedFlags = DamageFlags.extractFlags(flags)

        
        return AttackPacket(
            userId = userId,
            targetId = targetId,
            key1 = key1,
            key2 = key2,
            flags = extractedFlags
        )
    }


    private fun parseBossPacket(data: ByteArray): BossPacket? {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

        // ========== 패킷 데이터 추출 (Python과 동일한 오프셋) ==========
        val bossId = buffer.getInt(0).toLong() and 0xFFFFFFFFL    // 공격자 ID

        return BossPacket(
            bossId = bossId
        )
    }
    
    /**
     * ACTION 패킷 파싱 (패킷 타입: 100041)
     * 
     * 패킷 구조 (가변 길이, 리틀 엔디안):
     * - 0-3: userId (유저 ID)
     * - 4-7: p1 (미사용)
     * - 8-11: skillNameLen (스킬명 길이)
     * - 12-(12+skillNameLen-1): skillName (UTF-8 스킬명, null 문자 포함)
     * - 이후: skillId (key1, 4바이트)
     * - 이후: 직업명 길이 및 직업명 (가변)
     * 
     * 이 패킷은 스킬 사용 시 발생하며, 스킬 ID와 스킬명을 매핑하는데 사용됩니다.
     * Python에서 skillKey2Name 객체에 저장되는 정보와 동일합니다.
     * 
     * Python 코드 참조:
     * skill_name_len = int.from_bytes(data[pivot:pivot+4], byteorder='little')
     * skill_name = data[pivot:pivot+skill_name_len].replace(b'\x00', b'').decode('utf-8')
     * key1 = int.from_bytes(data[pivot:pivot+4], byteorder='little')
     * 
     * @param data 가변 길이 패킷 데이터
     * @return 파싱된 ActionPacket 또는 null (파싱 실패 시)
     */
    private fun parseAction(data: ByteArray): ActionPacket? {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        var position = 0
        
        // ========== 기본 정보 추출 ==========
        val userId = buffer.getInt(position).toLong() and 0xFFFFFFFFL
        position += 4
        
        val p1 = buffer.getInt(position)  // 미사용
        position += 4
        
        // ========== 스킬명 추출 ==========
        val skillNameLen = buffer.getInt(position)
        position += 4
        
        val skillNameBytes = data.sliceArray(position until position + skillNameLen)
        position += skillNameLen
        
        val skillName = skillNameBytes
            .filter { it != 0.toByte() }
            .toByteArray()
            .toString(Charsets.UTF_8)
            .trim()
        
        val skillId = buffer.getInt(position)
        position += 4
        
        val p2 = buffer.getInt(position)
        position += 4
        
        // Skip what1 (17 bytes)
        position += 17
        
        val key1 = buffer.getInt(position).toLong() and 0xFFFFFFFFL
        
        
        return ActionPacket(
            userId = userId,
            skillName = skillName,
            key1 = key1
        )
    }

    private fun parseCurrentHp(data: ByteArray): CurrentHpPacket? {
        // HP 변경 패킷은 최소 20바이트 필요 (5개의 uint × 4바이트)

        try {
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

            // C# 코드와 동일하게 5개의 값만 읽음
            val targetId = buffer.getInt(0).toLong() and 0xFFFFFFFFL     // offset 0
            val unknown1 = buffer.getInt(4).toLong() and 0xFFFFFFFFL     // offset 4 (사용 안 함)
            val unknown2 = buffer.getInt(8).toLong() and 0xFFFFFFFFL       // offset 8
            val currentHp = buffer.getInt(12).toLong() and 0xFFFFFFFFL    // offset 12 (사용 안 함)
            val unknown3 = buffer.getInt(16).toLong() and 0xFFFFFFFFL   // offset 16

            return CurrentHpPacket(
                targetId = targetId,
                currentHp = currentHp
            )
        } catch (e: Exception) {
            logger.error(e) { "HP 변화 데이터 파싱 중 예외 발생" }
            return null
        }
    }
    
    private fun parseHpChanged(data: ByteArray): HpChangedPacket? {
        // HP 변경 패킷은 최소 20바이트 필요 (5개의 uint × 4바이트)
        
        try {
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            
            // C# 코드와 동일하게 5개의 값만 읽음
            val targetId = buffer.getInt(0).toLong() and 0xFFFFFFFFL     // offset 0
            val unknown1 = buffer.getInt(4).toLong() and 0xFFFFFFFFL     // offset 4 (사용 안 함)
            val prevHp = buffer.getInt(8).toLong() and 0xFFFFFFFFL       // offset 8
            val unknown2 = buffer.getInt(12).toLong() and 0xFFFFFFFFL    // offset 12 (사용 안 함)
            val currentHp = buffer.getInt(16).toLong() and 0xFFFFFFFFL   // offset 16

            return HpChangedPacket(
                targetId = targetId,
                prevHp = prevHp,
                currentHp = currentHp
            )
        } catch (e: Exception) {
            logger.error(e) { "HP 변화 데이터 파싱 중 예외 발생" }
            return null
        }
    }
    
    private fun parseSelfDamage(data: ByteArray): SelfDamagePacket? {
        
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        
        val userId = buffer.getInt(0).toLong() and 0xFFFFFFFFL
        val e1 = buffer.getInt(4).toLong() and 0xFFFFFFFFL
        val targetId = buffer.getInt(8).toLong() and 0xFFFFFFFFL
        val e2 = buffer.getInt(12).toLong() and 0xFFFFFFFFL
        val damage = buffer.getInt(16).toLong() and 0xFFFFFFFFL
        val e3 = buffer.getInt(20).toLong() and 0xFFFFFFFFL
        val siran = buffer.getInt(24).toLong() and 0xFFFFFFFFL
        val e4 = buffer.getInt(28).toLong() and 0xFFFFFFFFL
        val flags = data.sliceArray(32..38)  // 7 bytes starting at position 32
        
        val extractedFlags = DamageFlags.extractFlags(flags)
        
        
        return SelfDamagePacket(
            userId = userId,
            targetId = targetId,
            damage = damage,
            flags = extractedFlags
        )
    }
    
    /**
     * ITEM 패킷 파싱 (패킷 타입: 100045)
     * 
     * 패킷 구조:
     * - 0-3: userId (아이템 사용자 ID)
     * - 4-7: unknown (미사용)
     * - 8-11: stringLength (문자열 길이)
     * - 12-N: itemAction (UTF-16 LE 문자열, "UseXXX" 형태)
     * 
     * @param data 가변 길이 패킷 데이터
     * @return 파싱된 ItemUsePacket 또는 null (파싱 실패 시)
     */
    private fun parseItem(data: ByteArray): ItemUsePacket? {
        
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        
        val userId = buffer.getInt(0).toLong() and 0xFFFFFFFFL
        val unknown = buffer.getInt(4)  // 미사용
        val stringLength = buffer.getInt(8)
        
        if (data.size < 12 + stringLength) {
            logger.warn { "Invalid item packet: string length exceeds packet size" }
            return null
        }
        
        // UTF-16 LE 문자열 읽기
        val stringBytes = data.sliceArray(12 until 12 + stringLength)
        val itemAction = String(stringBytes, Charsets.UTF_16LE).trim('\u0000')
        
        // "UseXXX" 형태에서 아이템 이름 추출
        val itemName = if (itemAction.startsWith("Use")) {
            itemAction.substring(3)
        } else {
            itemAction
        }
        
        
        return ItemUsePacket(
            type = 100045,
            userId = userId,
            itemName = itemName
        )
    }
    
    /**
     * ITEM_321 패킷 파싱 (패킷 타입: 100321)
     * 
     * 본인이 아이템을 사용했을 때 발생하는 패킷
     * 구조: stringLength(4) + itemName(UTF-16) + ...
     */
    private fun parseItem321(data: ByteArray): ItemUsePacket? {
        if (data.size < 4) {
            logger.error { "ITEM_321 packet too small: ${data.size} bytes" }
            return null
        }
        
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        
        // 문자열 길이 읽기
        val stringLength = buffer.getInt(0)
        if (stringLength <= 0 || stringLength > 100 || data.size < 4 + stringLength) {
            logger.warn { "ITEM_321 invalid string length: $stringLength" }
            return null
        }
        
        // 아이템 이름 읽기
        val itemName = try {
            val stringBytes = data.sliceArray(4 until 4 + stringLength)
            String(stringBytes, Charsets.UTF_16LE).trim('\u0000')
        } catch (e: Exception) {
            logger.error(e) { "Failed to decode item name" }
            return null
        }
        
        // userId를 0으로 설정 (0은 본인을 나타냄)
        val userId = 0L
        
        
        return ItemUsePacket(
            type = 100321,
            userId = userId,  // 0 = self
            itemName = itemName
        )
    }
    
    /**
     * DIE_BLOW 패킷 파싱 (패킷 타입: 100042)
     * 
     * 패킷 구조 (53바이트, 리틀 엔디안):
     * - 0-3: userId
     * - 4-7: unknown field (0)
     * - 8-11: skillNameLen (UTF-16 길이 in bytes)
     * - 12-27: skillName (UTF-16LE "Die_Blow")
     * - 28-35: unknown data
     * - 36-43: unknown data
     * - 44-52: unknown data
     * 
     * @param data 80바이트 패킷 데이터
     * @return 파싱된 DieBlowPacket 또는 null (파싱 실패 시)
     */
    private fun parseDieBlow(data: ByteArray): DieBlowPacket? {
        
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        
        // 데이터 추출
        val userId = buffer.getInt(0).toLong() and 0xFFFFFFFFL
        val unknown1 = buffer.getInt(4).toLong() and 0xFFFFFFFFL
        val skillNameLen = buffer.getInt(8)
        
        // UTF-16LE 스킬명 추출 (16 bytes = "Die_Blow")
        val skillNameBytes = data.sliceArray(12 until 12 + skillNameLen)
        val skillName = String(skillNameBytes, Charsets.UTF_16LE)
        
        // skillName이 "Die_Blow"가 아니면 파싱하지 않음 (대소문자 무시)
        if (!skillName.equals("Die_Blow", ignoreCase = true)) {
            return null
        }
        
        // 나머지 unknown 데이터
        val unknown2 = data.sliceArray(28 until 36)
        val unknown3 = data.sliceArray(36 until 53)
        
        
        return DieBlowPacket(
            userId = userId,
            skillName = skillName,
            unknown1 = unknown1
        )
    }
    
    /**
     * BUFF_START 패킷 파싱 (패킷 타입: 100046)
     * 
     * 버프가 생성되거나 적용될 때 발생하는 패킷
     * 
     * @param data 패킷 데이터
     * @return 파싱된 BuffStartPacket 또는 null
     */
    private fun parseBuffStart(data: ByteArray): BuffStartPacket? {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        var position = 0
        
        try {
            // 유저 ID
            val userId = buffer.getInt(position).toLong() and 0xFFFFFFFFL
            position += 4
            
            // 미사용 필드
            val p1 = buffer.getInt(position)
            position += 4
            
            // 8바이트 인스턴스 키
            val instKey = data.sliceArray(position until position + 8).toHex()
            position += 8
            
            // 버프 키 (버프 종류)
            val buffKey = buffer.getInt(position).toLong() and 0xFFFFFFFFL
            position += 4
            
            // 플래그
            val flags = buffer.getInt(position).toLong() and 0xFFFFFFFFL
            position += 4
            
            // 스택 정보
            val stack1 = buffer.getInt(position)
            position += 4
            
            val stack2 = buffer.getInt(position)
            position += 4
            
            // 추가 데이터 스킵 (stack2 개수만큼)
            repeat(stack2) {
                position += 4
            }
            
            // 타겟 ID
            val targetId = buffer.getInt(position).toLong() and 0xFFFFFFFFL
            position += 4
            
            return BuffStartPacket(
                instKey = instKey,
                buffKey = buffKey,
                userId = userId,
                targetId = targetId,
                stack = stack1,
                flags = flags
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse BUFF_START packet" }
            return null
        }
    }
    
    /**
     * BUFF_UPDATE 패킷 파싱 (패킷 타입: 100049)
     * 
     * 버프 스택이 변경될 때 발생하는 패킷
     * 
     * @param data 패킷 데이터
     * @return 파싱된 BuffUpdatePacket 또는 null
     */
    private fun parseBuffUpdate(data: ByteArray): BuffUpdatePacket? {
        // BUFF_START와 동일한 구조
        val startPacket = parseBuffStart(data) ?: return null
        
        return BuffUpdatePacket(
            instKey = startPacket.instKey,
            buffKey = startPacket.buffKey,
            userId = startPacket.userId,
            targetId = startPacket.targetId,
            stack = startPacket.stack,
            flags = startPacket.flags
        )
    }
    
    /**
     * BUFF_END 패킷 파싱 (패킷 타입: 100047)
     * 
     * 버프가 종료될 때 발생하는 패킷
     * 
     * @param data 패킷 데이터
     * @return 파싱된 BuffEndPacket 또는 null
     */
    private fun parseBuffEnd(data: ByteArray): BuffEndPacket? {
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        var position = 0
        
        try {
            // 유저 ID
            val userId = buffer.getInt(position).toLong() and 0xFFFFFFFFL
            position += 4
            
            // 미사용 필드
            val p1 = buffer.getInt(position)
            position += 4
            
            // 8바이트 인스턴스 키
            val instKey = data.sliceArray(position until position + 8).toHex()
            position += 8
            
            // 플래그
            val flags = buffer.getInt(position).toLong() and 0xFFFFFFFFL
            
            return BuffEndPacket(
                instKey = instKey,
                userId = userId,
                flags = flags
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to parse BUFF_END packet" }
            return null
        }
    }
    
    /**
     * ByteArray를 Hex 문자열로 변환하는 확장 함수
     */
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}