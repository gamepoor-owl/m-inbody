package com.gamboo.minbody.service.network

import com.gamboo.minbody.rest.dto.response.NetworkInterfaceInfo
import com.gamboo.minbody.rest.dto.response.InterfaceStatus
import mu.KotlinLogging
import org.pcap4j.core.*
import org.springframework.stereotype.Service
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicLong
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy

/**
 * Network Interface Management Service
 * 
 * Features:
 * - Automatic interface detection and scoring
 * - Manual interface selection with persistence
 * - Real-time interface monitoring
 * - Performance statistics tracking
 */
@Service
class NetworkInterfaceService {
    
    private val logger = KotlinLogging.logger {}
    private val executor = Executors.newScheduledThreadPool(4)
    
    // Current active interface
    @Volatile
    private var activeInterface: NetworkInterfaceInfo? = null
    
    // Interface statistics
    private val interfaceStats = ConcurrentHashMap<String, InterfaceStatistics>()
    
    // Manual override
    @Volatile
    private var manualInterfaceOverride: String? = null
    
    
    
    @PostConstruct
    fun init() {
        logger.debug { "NetworkInterfaceService initialized" }

        if (activeInterface == null) {
            // Manual selection이 없고 활성 인터페이스도 없을 때만 자동 감지 (최초 1회)
            logger.info { "No manual selection found, running auto-detection for first time" }
            autoDetectInterface()
        }
    }
    
    @PreDestroy
    fun cleanup() {
        executor.shutdown()
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
        }
    }
    
    /**
     * Get all available network interfaces with their status
     */
    fun getAllInterfaces(): List<NetworkInterfaceInfo> {
        return try {
            val interfaces = try {
                val pcapInterfaces = Pcaps.findAllDevs()
                pcapInterfaces.map { nif ->
                    val stats = interfaceStats[nif.name]
                    val score = calculateInterfaceScore(nif, stats)
                    val isActive = activeInterface?.name == nif.name
                    
                    NetworkInterfaceInfo(
                        name = nif.name,
                        description = nif.description ?: "Unknown",
                        addresses = nif.addresses.map { it.address.hostAddress },
                        isActive = isActive,
                        isLoopback = nif.isLoopBack,
                        status = if (isActive) InterfaceStatus.ACTIVE else getInterfaceStatus(nif, stats),
                        score = score,
                        statistics = stats?.toDto()
                    )
                }
            } catch (e: UnsatisfiedLinkError) {
                logger.error { "Native pcap library not found: ${e.message}" }
                logger.info { "Using fallback interface detection" }
                getFallbackInterfaces()
            } catch (e: NoClassDefFoundError) {
                logger.error { "Pcap4j classes not found: ${e.message}" }
                logger.info { "Using fallback interface detection" }
                getFallbackInterfaces()
            } catch (e: Exception) {
                logger.error { "Error calling Pcaps.findAllDevs(): ${e.message}" }
                logger.info { "Using fallback interface detection" }
                getFallbackInterfaces()
            }
            
            interfaces.sortedByDescending { it.score }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to enumerate network interfaces" }
            // Try fallback as last resort
            getFallbackInterfaces()
        }
    }
    
    /**
     * Get current active interface
     */
    fun getActiveInterface(): NetworkInterfaceInfo? = activeInterface
    
    /**
     * Manually select an interface
     */
    fun selectInterface(interfaceName: String): Boolean {
        logger.info { "Manual interface selection: $interfaceName" }
        
        return try {
            // First try pcap method
            val nif = try {
                Pcaps.getDevByName(interfaceName)
            } catch (e: Exception) {
                logger.warn { "Pcap not available, using fallback for interface: $interfaceName" }
                null
            }
            
            if (nif != null) {
                manualInterfaceOverride = interfaceName
                val info = createInterfaceInfo(nif)
                setActiveInterface(info)
                logger.info { "Interface selected successfully: ${info.name} with status: ${info.status}" }
                true
            } else {
                // Try fallback method
                val fallbackInterfaces = getFallbackInterfaces()
                val fallbackInterface = fallbackInterfaces.find { it.name == interfaceName }
                
                if (fallbackInterface != null) {
                    manualInterfaceOverride = interfaceName
                    setActiveInterface(fallbackInterface.copy(isActive = true, status = InterfaceStatus.ACTIVE))
                    logger.info { "Interface selected via fallback: $interfaceName" }
                    true
                } else {
                    logger.error { "Interface not found: $interfaceName" }
                    false
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to select interface: $interfaceName" }
            false
        }
    }
    
    /**
     * Clear manual selection and re-enable auto-detection
     */
    fun clearManualSelection() {
        logger.info { "Clearing manual interface selection" }
        manualInterfaceOverride = null
        activeInterface = null // 활성 인터페이스도 초기화
        // 자동 감지 실행하지 않음 - 사용자가 명시적으로 선택하게 함
        logger.info { "Interface cleared, waiting for user selection or auto-detection trigger" }
    }

    
    /**
     * 자동으로 네트워크 인터페이스 감지
     * 1순위: MainNet/Ethernet 
     * 2순위: WiFi
     * 3순위: 첫 번째 사용 가능한 인터페이스
     */
    private fun autoDetectInterface(): NetworkInterfaceInfo? {
        logger.info { "Auto-detecting network interface" }
        
        val interfaces = try {
            Pcaps.findAllDevs().filter { !it.isLoopBack }
        } catch (e: Exception) {
            logger.error { "Failed to get network interfaces: ${e.message}" }
            return null
        }
        
        if (interfaces.isEmpty()) {
            logger.warn { "No non-loopback interfaces found" }
            return null
        }
        
        // 1순위: Ethernet/MainNet 인터페이스 찾기
        val ethernet = interfaces.find { nif ->
            val name = nif.name.lowercase()
            val desc = (nif.description ?: "").lowercase()
            desc.contains("realtek") ||
            desc.contains("intel")  ||
            name.contains("eth") || name.contains("en0")
        }
        
        if (ethernet != null) {
            logger.debug { "Selected Ethernet interface: ${ethernet.name}" }
            val info = createInterfaceInfo(ethernet)
            setActiveInterface(info)
            return info
        }
        
        // 2순위: WiFi 인터페이스 찾기
        val wifi = interfaces.find { nif ->
            val name = nif.name.lowercase()
            val desc = (nif.description ?: "").lowercase()
            desc.contains("wi-fi") || desc.contains("wifi") || 
            desc.contains("wireless") || desc.contains("802.11") ||
            name.contains("wlan") || name.contains("wlp")
        }
        
        if (wifi != null) {
            logger.debug { "Selected WiFi interface: ${wifi.name}" }
            val info = createInterfaceInfo(wifi)
            setActiveInterface(info)
            return info
        }
        
        // 3순위: 첫 번째 사용 가능한 인터페이스
        val first = interfaces.firstOrNull()
        if (first != null) {
            logger.debug { "Selected first available interface: ${first.name}" }
            val info = createInterfaceInfo(first)
            setActiveInterface(info)
            return info
        }
        
        logger.warn { "No suitable network interface found" }
        return null
    }
    
    
    
    /**
     * Calculate interface score for selection
     * Priority: MainNet(Ethernet) > WiFi > Others
     */
    private fun calculateInterfaceScore(
        nif: PcapNetworkInterface, 
        stats: InterfaceStatistics?
    ): Double {
        var score = 0.0
        
        // Base score for interface type
        val name = nif.name.lowercase()
        val desc = (nif.description ?: "").lowercase()
        
        // 1순위: MainNet/Ethernet (최우선)
        if ( desc.contains("realtek") ||
            desc.contains("intel") ||
            name.startsWith("eth") || name.startsWith("en") && !name.contains("wireless")) {
            score += 100.0  // 이더넷에 최고 점수
            logger.debug { "MainNet/Ethernet interface detected: ${nif.name} (+100 score)" }
        }
        // 2순위: WiFi/Wireless
        else if (desc.contains("wi-fi") || desc.contains("wifi") || 
                 desc.contains("wireless") || desc.contains("802.11") ||
                 name.startsWith("wlan") || name.startsWith("wl")) {
            score += 70.0  // WiFi는 두 번째 우선순위
            logger.debug { "WiFi interface detected: ${nif.name} (+70 score)" }
        }
        // 3순위: 기타 네트워크 인터페이스
        else {
            score += 30.0
        }
        
        // Penalize virtual interfaces (가상 인터페이스는 낮은 우선순위)
        if (desc.contains("vmware") || desc.contains("virtualbox") || 
            desc.contains("docker") || desc.contains("vpn") || desc.contains("virtual")) {
            score -= 50.0
            logger.debug { "Virtual interface detected: ${nif.name} (-50 score)" }
        }
        
        // Stats-based scoring
        stats?.let {
            // Heavy weight for game packets
            score += it.gamePackets.get() * 100.0
            
            // Moderate weight for total packets
            score += Math.min(it.totalPackets.get() * 0.1, 10.0)
            
            // Recent activity bonus
            val timeSinceLastPacket = System.currentTimeMillis() - it.lastGamePacketTime.get()
            if (timeSinceLastPacket < 10000) { // Within 10 seconds
                score += 20.0
            }
        }
        
        // Active interface bonus
        if (activeInterface?.name == nif.name) {
            score += 10.0
        }
        
        return score
    }
    
    /**
     * Set active interface and notify listeners
     */
    private fun setActiveInterface(info: NetworkInterfaceInfo?) {
        val previous = activeInterface
        activeInterface = info
        
        if (previous?.name != info?.name) {
            logger.info { "Active interface changed: ${previous?.name} -> ${info?.name}" }
        }
    }
    
    
    /**
     * Create interface info from PcapNetworkInterface
     */
    private fun createInterfaceInfo(nif: PcapNetworkInterface): NetworkInterfaceInfo {
        val stats = interfaceStats[nif.name]
        return NetworkInterfaceInfo(
            name = nif.name,
            description = nif.description ?: "Unknown",
            addresses = nif.addresses.map { it.address.hostAddress },
            isActive = true,
            isLoopback = nif.isLoopBack,
            status = InterfaceStatus.ACTIVE,  // Always ACTIVE when selected
            score = calculateInterfaceScore(nif, stats),
            statistics = stats?.toDto()
        )
    }
    
    /**
     * Get interface status
     */
    private fun getInterfaceStatus(
        nif: PcapNetworkInterface, 
        stats: InterfaceStatistics?
    ): InterfaceStatus {
        return when {
            activeInterface?.name == nif.name -> InterfaceStatus.ACTIVE
            stats?.gamePackets?.get() ?: 0 > 0 -> InterfaceStatus.GAME_DETECTED
            stats?.totalPackets?.get() ?: 0 > 0 -> InterfaceStatus.AVAILABLE
            else -> InterfaceStatus.IDLE
        }
    }
    
    
    /**
     * Interface statistics tracking
     */
    class InterfaceStatistics {
        val totalPackets = AtomicLong(0)
        val gamePackets = AtomicLong(0)
        val totalBytes = AtomicLong(0)
        val lastGamePacketTime = AtomicLong(0)
        val errors = AtomicLong(0)
        
        fun toDto() = com.gamboo.minbody.rest.dto.response.InterfaceStatistics(
            totalPackets = totalPackets.get(),
            gamePackets = gamePackets.get(),
            totalBytes = totalBytes.get(),
            lastActivityTime = if (lastGamePacketTime.get() > 0) lastGamePacketTime.get() else null,
            errors = errors.get()
        )
    }
    
    /**
     * Fallback method to get network interfaces when pcap fails
     */
    private fun getFallbackInterfaces(): List<NetworkInterfaceInfo> {
        val fallbackList = mutableListOf<NetworkInterfaceInfo>()
        
        try {
            // Try using Java NetworkInterface as fallback
            val javaInterfaces = java.net.NetworkInterface.getNetworkInterfaces()
            
            while (javaInterfaces.hasMoreElements()) {
                val javaInterface = javaInterfaces.nextElement()
                
                // Skip loopback and down interfaces
                if (javaInterface.isLoopback || !javaInterface.isUp) {
                    continue
                }
                
                val addresses = javaInterface.inetAddresses.toList().map { it.hostAddress }
                
                fallbackList.add(
                    NetworkInterfaceInfo(
                        name = javaInterface.name,
                        description = javaInterface.displayName ?: javaInterface.name,
                        addresses = addresses,
                        isActive = false,
                        isLoopback = javaInterface.isLoopback,
                        status = InterfaceStatus.AVAILABLE,
                        score = if (javaInterface.name.contains("eth") || javaInterface.name.contains("en")) 50.0 else 10.0,
                        statistics = null
                    )
                )
                logger.info { "Added fallback interface: ${javaInterface.name} (${javaInterface.displayName})" }
            }
        } catch (e: Exception) {
            logger.error { "Failed to get fallback interfaces: ${e.message}" }
        }
        
        // If no interfaces found, add common default names
        if (fallbackList.isEmpty()) {
            logger.info { "Adding default interface names as last resort" }
            
            // Common interface names
            val commonNames = when {
                System.getProperty("os.name").contains("Windows") -> 
                    listOf("Ethernet", "Wi-Fi", "Local Area Connection")
                System.getProperty("os.name").contains("Mac") -> 
                    listOf("en0", "en1", "en2")
                else -> 
                    listOf("eth0", "eth1", "ens33", "enp0s3", "wlan0")
            }
            
            commonNames.forEach { name ->
                fallbackList.add(
                    NetworkInterfaceInfo(
                        name = name,
                        description = "Default interface: $name",
                        addresses = emptyList(),
                        isActive = false,
                        isLoopback = false,
                        status = InterfaceStatus.IDLE,
                        score = 1.0,
                        statistics = null
                    )
                )
            }
        }
        
        return fallbackList
    }
}