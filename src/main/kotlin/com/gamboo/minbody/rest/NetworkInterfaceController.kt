package com.gamboo.minbody.rest

import com.gamboo.minbody.rest.dto.request.SelectInterfaceRequest
import com.gamboo.minbody.rest.dto.response.NetworkInterfaceInfo
import com.gamboo.minbody.rest.dto.response.NetworkInterfaceResponse
import com.gamboo.minbody.service.network.NetworkInterfaceService
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * REST API Controller for Network Interface Management
 * 
 * Endpoints:
 * - GET /api/network/interfaces - List all interfaces
 * - GET /api/network/interfaces/active - Get active interface
 * - POST /api/network/interfaces/select - Select interface manually
 * - POST /api/network/interfaces/detect - Trigger detection
 * - DELETE /api/network/interfaces/selection - Clear manual selection
 */
@RestController
@RequestMapping("/api/network")
class NetworkInterfaceController(
    private val networkInterfaceService: NetworkInterfaceService,
    private val packetCaptureService: com.gamboo.minbody.service.packet.PacketCaptureService
) {
    
    private val logger = KotlinLogging.logger {}
    
    /**
     * Get all available network interfaces
     */
    @GetMapping("/interfaces")
    fun getAllInterfaces(): ResponseEntity<NetworkInterfaceResponse> {
        logger.debug { "Getting all network interfaces" }
        
        return try {
            val interfaces = networkInterfaceService.getAllInterfaces()
            val active = networkInterfaceService.getActiveInterface()
            
            ResponseEntity.ok(
                NetworkInterfaceResponse(
                    success = true,
                    interfaces = interfaces,
                    activeInterface = active?.name,
                    autoDetectEnabled = true
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to get network interfaces" }
            ResponseEntity.ok(
                NetworkInterfaceResponse(
                    success = false,
                    error = e.message
                )
            )
        }
    }
    
    /**
     * Get current active interface
     */
    @GetMapping("/interfaces/active")
    fun getActiveInterface(): ResponseEntity<NetworkInterfaceInfo?> {
        logger.debug { "Getting active network interface" }
        
        return try {
            val active = networkInterfaceService.getActiveInterface()
            ResponseEntity.ok(active)
        } catch (e: Exception) {
            logger.error(e) { "Failed to get active interface" }
            ResponseEntity.ok(null)
        }
    }
    
    /**
     * Manually select an interface
     */
    @PostMapping("/interfaces/select")
    fun selectInterface(
        @RequestBody request: SelectInterfaceRequest
    ): ResponseEntity<NetworkInterfaceResponse> {
        logger.info { "Selecting interface: ${request.interfaceName}" }
        
        return try {
            val success = networkInterfaceService.selectInterface(request.interfaceName)
            
            if (success) {
                // 인터페이스 변경 성공 시 캡처 재시작
                logger.info { "Restarting packet capture with new interface: ${request.interfaceName}" }
                packetCaptureService.restartWithInterface(request.interfaceName)
                
                val interfaces = networkInterfaceService.getAllInterfaces()
                val active = networkInterfaceService.getActiveInterface()
                
                ResponseEntity.ok(
                    NetworkInterfaceResponse(
                        success = true,
                        interfaces = interfaces,
                        activeInterface = active?.name,
                        message = "Interface selected successfully"
                    )
                )
            } else {
                ResponseEntity.ok(
                    NetworkInterfaceResponse(
                        success = false,
                        error = "Failed to select interface: ${request.interfaceName}"
                    )
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "Error selecting interface" }
            ResponseEntity.ok(
                NetworkInterfaceResponse(
                    success = false,
                    error = e.message
                )
            )
        }
    }
    
    /**
     * Get current active interface info
     */
    @GetMapping("/interfaces/active-info")
    fun getActiveInterfaceInfo(): ResponseEntity<NetworkInterfaceResponse> {
        logger.info { "Getting active interface" }
        
        return try {
            val activeInterface = networkInterfaceService.getActiveInterface()
            val interfaces = networkInterfaceService.getAllInterfaces()
            
            ResponseEntity.ok(
                NetworkInterfaceResponse(
                    success = activeInterface != null,
                    interfaces = interfaces,
                    activeInterface = activeInterface?.name,
                    message = if (activeInterface != null) 
                        "Active interface: ${activeInterface.name}" 
                    else 
                        "No active interface"
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Error getting active interface" }
            ResponseEntity.ok(
                NetworkInterfaceResponse(
                    success = false,
                    error = e.message
                )
            )
        }
    }
    
    /**
     * Clear manual selection and re-enable auto-detection
     */
    @DeleteMapping("/interfaces/selection")
    fun clearSelection(): ResponseEntity<NetworkInterfaceResponse> {
        logger.info { "Clearing manual interface selection" }
        
        return try {
            networkInterfaceService.clearManualSelection()
            val interfaces = networkInterfaceService.getAllInterfaces()
            val active = networkInterfaceService.getActiveInterface()
            
            ResponseEntity.ok(
                NetworkInterfaceResponse(
                    success = true,
                    interfaces = interfaces,
                    activeInterface = active?.name,
                    message = "Manual selection cleared, auto-detection enabled"
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Error clearing selection" }
            ResponseEntity.ok(
                NetworkInterfaceResponse(
                    success = false,
                    error = e.message
                )
            )
        }
    }
}