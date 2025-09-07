package com.gamboo.minbody.service.packet

import com.gamboo.minbody.rest.dto.response.PacketData
import com.gamboo.minbody.service.damage.DamageProcessingService
import com.gamboo.minbody.service.network.NetworkInterfaceService
import jakarta.annotation.PostConstruct
import mu.KotlinLogging
import org.pcap4j.core.BpfProgram
import org.pcap4j.core.PacketListener
import org.pcap4j.core.PcapNetworkInterface
import org.pcap4j.core.Pcaps
import org.pcap4j.packet.TcpPacket
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicLong

/**
 * 네트워크 패킷을 캡처하고 게임 데이터를 파싱하는 서비스
 *
 * 주요 기능:
 * 1. TCP 포트 16000의 게임 서버 트래픽 캡처
 * 2. TCP 스트림 재조립 (out-of-order 패킷 처리)
 * 3. 게임 프로토콜 파싱 (Attack, Action, HP Changed, Self Damage)
 * 4. 실시간 데미지 통계 처리
 */
@Service
class PacketCaptureService(
    private val packetParserService: PacketParserService,
    private val damageProcessingService: DamageProcessingService,
    private val networkInterfaceService: NetworkInterfaceService,
    @Value("\${packet.capture.filter:tcp and src port 16000}") private val captureFilter: String,
    @Value("\${packet.capture.interface:}") private val networkInterface: String
) {

    private val logger = KotlinLogging.logger {}

    // 패킷 캡처 스레드 관리
    @Volatile
    private var captureThread: Thread? = null
    private val globalBuffer = TcpStreamReassembler() // 단일 버퍼

    // 글로벌 캡처 상태
    @Volatile
    private var isGlobalCaptureRunning = false

    private var currentInterface: PcapNetworkInterface? = null
    private val globalPacketCount = AtomicLong(0)

    @PostConstruct
    fun init() {
        logger.info { "Running in MOCK mode - no real packet capture" }

        // 서버 시작 시 글로벌 캡처 시작
        startGlobalCapture()
    }

    @jakarta.annotation.PreDestroy
    fun cleanup() {
        logger.info { "Stopping PacketCaptureService" }
        stopGlobalCapture()
    }

    /**
     * 글로벌 패킷 캡처 시작 (서버 시작 시 자동 호출)
     */
    fun startGlobalCapture() {
        if (isGlobalCaptureRunning) {
            logger.info { "Global packet capture already running" }
            return
        }

        logger.info { "Starting global packet capture" }
        isGlobalCaptureRunning = true
        startCaptureThread()
    }

    /**
     * 글로벌 패킷 캡처 중지
     */
    fun stopGlobalCapture() {
        if (!isGlobalCaptureRunning) {
            logger.info { "Global packet capture not running" }
            return
        }

        logger.info { "Stopping global packet capture" }
        isGlobalCaptureRunning = false
        captureThread?.interrupt()
        captureThread = null
        currentInterface = null
        globalBuffer.clear()
    }

    /**
     * 네트워크 인터페이스 변경 시 캡처 재시작
     */
    fun restartWithInterface(interfaceName: String) {
        logger.debug { "Restarting packet capture with interface: $interfaceName" }

        // 기존 캡처 중지
        stopGlobalCapture()

        // 잠시 대기 후 새 인터페이스로 재시작
        Thread.sleep(2000)

        // 새 인터페이스로 캡처 재시작
        startGlobalCapture()
    }


    /**
     * Start the capture thread (extracted for reuse)
     */
    private fun startCaptureThread() {
        // 이미 캡처 스레드가 실행 중이면 새로 시작하지 않음
        if (captureThread?.isAlive == true) {
            logger.info { "Capture thread already running, not starting a new one" }
            return
        }

        val thread = Thread {
            try {
                logger.info { "Starting global packet capture thread" }

                // Get network interface from NetworkInterfaceService
                val activeInterface = networkInterfaceService.getActiveInterface()
                val nif = if (activeInterface != null) {
                    logger.info { "Using active interface from NetworkInterfaceService: ${activeInterface.name}" }
                    try {
                        Pcaps.getDevByName(activeInterface.name)
                    } catch (e: Exception) {
                        logger.error { "Failed to get interface ${activeInterface.name}: ${e.message}" }
                        null
                    }
                } else if (networkInterface.isNotBlank()) {
                    logger.info { "Using configured interface: $networkInterface" }
                    try {
                        Pcaps.getDevByName(networkInterface)
                    } catch (e: Exception) {
                        logger.error { "Failed to get interface $networkInterface: ${e.message}" }
                        null
                    }
                } else {
                    // Fallback: Get active interface from NetworkInterfaceService
                    val activeInterface = networkInterfaceService.getActiveInterface()
                    if (activeInterface != null) {
                        logger.info { "Using active interface: ${activeInterface.name}" }
                        try {
                            Pcaps.getDevByName(activeInterface.name)
                        } catch (e: Exception) {
                            logger.error { "Failed to get active interface ${activeInterface.name}: ${e.message}" }
                            null
                        }
                    } else {
                        logger.warn { "No active interface, using first available interface" }
                        try {
                            val allDevs = Pcaps.findAllDevs()
                            allDevs.find { !it.isLoopBack } ?: allDevs.firstOrNull()
                        } catch (e: Exception) {
                            logger.error { "Failed to get any interface: ${e.message}" }
                            null
                        }
                    }
                }

                if (nif == null) {
                    logger.error { "No network interface found" }
                    return@Thread
                }

                logger.debug { "Using interface: ${nif.name} (${nif.description})" }

                // 현재 인터페이스 저장 (전역)
                currentInterface = nif
                globalPacketCount.set(0)

                // Open interface
                val handle = nif.openLive(65536, PcapNetworkInterface.PromiscuousMode.PROMISCUOUS, 1000)
                logger.info { "Interface opened successfully in PROMISCUOUS mode" }

                // Set filter
                try {
                    handle.setFilter(captureFilter, BpfProgram.BpfCompileMode.OPTIMIZE)
                    logger.debug { "Filter set: $captureFilter" }
                } catch (e: Exception) {
                    logger.warn { "Failed to set filter: ${e.message}" }
                }

                logger.info { "Started global packet capture" }

                val listener = PacketListener { packet ->
                    try {
                        packet?.let {
                            if (it.contains(TcpPacket::class.java)) {
                                val tcpPacket = it.get(TcpPacket::class.java)

                                tcpPacket.payload?.rawData?.let { data ->
                                    // 모든 활성 세션에 대해 패킷 처리
                                    processTcpSegmentForAllSessions(tcpPacket.header.sequenceNumber.toLong(), data)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        logger.error(e) { "Error processing packet" }
                    }
                }

                // Main capture loop
                while (!Thread.currentThread().isInterrupted) {
                    try {
                        handle.loop(10, listener)
                    } catch (e: Exception) {
                        if (!Thread.currentThread().isInterrupted) {
                            logger.error(e) { "Error in packet loop" }
                            Thread.sleep(1000)
                        }
                    }
                }

                handle.close()
                logger.debug { "Global packet capture stopped" }
            } catch (e: Exception) {
                logger.error(e) { "Error in global packet capture" }
            }
        }

        thread.name = "GlobalPacketCapture"
        thread.start()
        captureThread = thread
    }

    /**
     * TCP 세그먼트를 처리하는 메서드
     * 글로벌 버퍼를 사용하여 패킷을 처리하고 통계를 업데이트
     */
    private fun processTcpSegmentForAllSessions(
        sequenceNumber: Long,
        data: ByteArray
    ) {
        // 글로벌 캡처가 실행 중이 아니면 처리하지 않음
        if (!isGlobalCaptureRunning) {
            return
        }

        // TCP 세그먼트를 전역 버퍼에 추가
        globalBuffer.addTcpSegment(sequenceNumber, data)

        // 재조립된 버퍼에서 패킷 파싱 시도
        val bufferData = globalBuffer.getData()
        logger.debug { "Buffer size before parsing: ${bufferData.size} bytes" }

        val (packets, consumed) = parsePackets(bufferData)

        if (packets.isNotEmpty()) {
            logger.debug { "Parsed ${packets.size} game packets from buffer, consumed $consumed bytes" }

            val uniquePackets = packets

            if (uniquePackets.isNotEmpty()) {
                logger.debug { "Processing ${uniquePackets.size} packets" }

                // 모드 설정 가져오기 - 단일 연결 정책으로 간소화
                try {
                    // 패킷 처리 - 통계가 자동으로 업데이트됨
                    // DamageProcessingService가 내부적으로 DamageStatsService를 업데이트
                    damageProcessingService.processPackets(
                        uniquePackets
                    )

                    // WebSocketBroadcastService가 1초마다 자동으로 변경된 통계를 브로드캐스트
                    // 여기서는 별도 작업 필요 없음

                } catch (e: Exception) {
                    logger.error(e) { "Error processing packets" }
                }
            }
        }

        if (consumed > 0) {
            globalBuffer.consume(consumed)
            logger.debug { "Buffer size after consuming: ${globalBuffer.getData().size} bytes" }
        }
    }

    /**
     * 바이트 배열에서 게임 패킷을 파싱
     *
     * 패킷 구조:
     * - 시작 마커: 0x68 0x27 0x00... (9 bytes)
     * - 데이터 타입: 4 bytes (little endian)
     * - 길이: 4 bytes (little endian)
     * - 인코딩 타입: 1 byte (0=plain, 1=brotli compressed)
     * - 컨텐츠: variable length
     * - 종료 마커: 0xe3 0x27 0x00... (9 bytes)
     *
     * @return 파싱된 패킷 리스트와 소비된 바이트 수
     */
    private fun parsePackets(data: ByteArray): Pair<List<PacketData>, Int> {
        val packets = mutableListOf<PacketData>()
        var consumed = 0
        var pivot = 0

        while (pivot < data.size) {
            // Find packet start marker (0x7c 0x4e... matching C# code)
            val startPivot = findSequence(data, pivot, byteArrayOf(0x7c, 0x4e, 0, 0, 0, 0, 0, 0, 0))
            if (startPivot == -1) break

            // Check for end marker (0xf7 0x4e... matching C# code)
            val endPivot = findSequence(data, startPivot + 9, byteArrayOf(0xf7.toByte(), 0x4e, 0, 0, 0, 0, 0, 0, 0))
            if (endPivot == -1) break

            pivot = startPivot + 9

            while (pivot + 9 <= data.size && pivot < endPivot) {
                val dataType = ByteBuffer.wrap(data, pivot, 4).order(ByteOrder.LITTLE_ENDIAN).int
                val length = ByteBuffer.wrap(data, pivot + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                val encodeType = data[pivot + 8].toInt()


                if (dataType == 0) {
                    logger.debug { "End of packet data (dataType=0)" }
                    break
                }
                if (pivot + 9 + length > data.size) {
                    logger.debug { "Incomplete packet data, waiting for more" }
                    return packets to consumed  // Return what we've consumed so far
                }

                var content = data.sliceArray((pivot + 9) until (pivot + 9 + length))

                // Skip compressed packets for now (matching current behavior)
                if (encodeType == 1) {
                    logger.debug { "Skipping compressed packet (type=$dataType, length=$length)" }
                    pivot += 9 + length
                    continue
                }

                // Parse packet based on type
                logger.debug { "[PACKET TYPE] Received packet - Type: $dataType, Length: $length, EncodeType: $encodeType" }

                packetParserService.parsePacket(dataType, content)?.let { packet ->
                    packets.add(packet)
                    logger.debug { "[PACKET PARSED] Successfully parsed packet type=$dataType" }
                } ?: run {
                    // 새로운 패킷 타입 중 주요 타입들 로깅
                    if (dataType in listOf(20318, 100043, 100172, 20741, 100048, 100051, 100049)) {
                        logger.info { "[PACKET KNOWN] Known packet type not parsed: $dataType (might be a parsing issue)" }
                    }
                }

                pivot += 9 + length
            }

            // Consume up to the end marker + 9 bytes
            consumed = endPivot + 9
            pivot = consumed
        }

        return packets to consumed
    }

    private fun findSequence(data: ByteArray, start: Int, sequence: ByteArray): Int {
        if (start + sequence.size > data.size) return -1

        for (i in start..(data.size - sequence.size)) {
            var found = true
            for (j in sequence.indices) {
                if (data[i + j] != sequence[j]) {
                    found = false
                    break
                }
            }
            if (found) return i
        }
        return -1
    }

    private inner class TcpStreamReassembler {
        private var buffer = ByteArray(0)
        private val tcpSegments = mutableMapOf<Long, ByteArray>()
        private var currentSeq: Long? = null
        private val seqMod = 1L shl 32

        private fun seqDistance(a: Long, b: Long): Long {
            return ((a - b + (1L shl 31)) % seqMod) - (1L shl 31)
        }

        @Synchronized
        fun addTcpSegment(seq: Long, payload: ByteArray) {
            if (payload.isEmpty()) return  // Skip empty payloads

            if (currentSeq == null) {
                currentSeq = seq
            }

            // Check if sequence is too far from current
            currentSeq?.let { current ->
                if (kotlin.math.abs(seqDistance(seq, current)) > 10000) {
                    logger.debug { "Sequence number too far from current: $seq vs $current, resetting buffer" }
                    tcpSegments.clear()
                    currentSeq = seq
                    buffer = ByteArray(0)
                }
            }

            // Add segment if not already present OR if payload is different (handling retransmissions)
            val existingPayload = tcpSegments[seq]
            if (existingPayload == null || !existingPayload.contentEquals(payload)) {
                if (existingPayload != null) {
                    logger.debug { "TCP retransmission detected: seq=$seq, old size=${existingPayload.size}, new size=${payload.size}" }
                }
                tcpSegments[seq] = payload
                logger.debug { "Added TCP segment: seq=$seq, size=${payload.size}" }
                // Reassemble consecutive segments
                reassembleSegments()
            } else {
                logger.debug { "Duplicate TCP segment ignored: seq=$seq, size=${payload.size}" }
            }
        }

        private fun reassembleSegments() {
            currentSeq?.let { seq ->
                var currentSequence = seq
                while (tcpSegments.containsKey(currentSequence)) {
                    val segment = tcpSegments.remove(currentSequence)!!
                    buffer += segment
                    currentSequence = (currentSequence + segment.size) % seqMod
                }
                currentSeq = currentSequence
            }

            // Clean up buffer if too large (increased to 64KB to reduce packet loss)
            if (buffer.size > 1024 * 64) {  // 64KB limit (increased from 16KB)
                val halfSize = buffer.size / 2
                buffer = buffer.sliceArray(halfSize until buffer.size)
            }
        }

        @Synchronized
        fun getData(): ByteArray = buffer

        @Synchronized
        fun consume(bytes: Int) {
            if (bytes >= buffer.size) {
                buffer = ByteArray(0)
            } else {
                buffer = buffer.sliceArray(bytes until buffer.size)
            }
        }

        @Synchronized
        fun clear() {
            buffer = ByteArray(0)
            tcpSegments.clear()
            currentSeq = null
        }
    }
}