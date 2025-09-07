package com.gamboo.minbody.rest.dto.response

/**
 * Network Interface Information DTO
 */
data class NetworkInterfaceInfo(
    val name: String,
    val description: String,
    val addresses: List<String>,
    val isActive: Boolean,
    val isLoopback: Boolean,
    val status: InterfaceStatus,
    val score: Double,
    val statistics: InterfaceStatistics? = null
)

/**
 * Interface Status Enum
 */
enum class InterfaceStatus {
    IDLE,           // No activity
    AVAILABLE,      // Has traffic but no game packets
    GAME_DETECTED,  // Game packets detected
    ACTIVE         // Currently selected and active
}

/**
 * Interface Statistics DTO
 */
data class InterfaceStatistics(
    val totalPackets: Long,
    val gamePackets: Long,
    val totalBytes: Long,
    val lastActivityTime: Long? = null,
    val errors: Long = 0
)

/**
 * Network Interface API Response
 */
data class NetworkInterfaceResponse(
    val success: Boolean,
    val interfaces: List<NetworkInterfaceInfo> = emptyList(),
    val activeInterface: String? = null,
    val autoDetectEnabled: Boolean = true,
    val message: String? = null,
    val error: String? = null
)