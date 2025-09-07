package com.gamboo.minbody.config.exception

import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClientException
import feign.FeignException
import feign.RetryableException
import java.time.LocalDateTime

/**
 * 전역 예외 처리기
 * 
 * REST API와 WebSocket 간의 스레드 격리를 위해
 * 모든 예외를 캐치하여 적절히 처리합니다.
 */
@ControllerAdvice
@RestController
class GlobalExceptionHandler {
    
    private val logger = KotlinLogging.logger {}
    
    /**
     * 일반적인 Exception 처리
     */
    @ExceptionHandler(Exception::class)
    fun handleGeneralException(e: Exception): ResponseEntity<ErrorResponse> {
        logger.error(e) { "Unhandled exception occurred" }
        
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(
                error = "Internal Server Error",
                message = e.message ?: "An unexpected error occurred",
                timestamp = LocalDateTime.now(),
                status = HttpStatus.INTERNAL_SERVER_ERROR.value()
            ))
    }
    
    /**
     * RestClient 관련 예외 처리 (MCenterClient 호출 시 발생)
     */
    @ExceptionHandler(RestClientException::class)
    fun handleRestClientException(e: RestClientException): ResponseEntity<ErrorResponse> {
        logger.error(e) { "External API call failed" }
        
        return ResponseEntity
            .status(HttpStatus.BAD_GATEWAY)
            .body(ErrorResponse(
                error = "External Service Error",
                message = "Failed to communicate with external service: ${e.message}",
                timestamp = LocalDateTime.now(),
                status = HttpStatus.BAD_GATEWAY.value()
            ))
    }
    
    /**
     * Feign 예외 처리 (MCenterClient 호출 시 발생)
     */
    @ExceptionHandler(FeignException::class)
    fun handleFeignException(e: FeignException): ResponseEntity<ErrorResponse> {
        logger.error(e) { "Feign client error: status=${e.status()}, message=${e.message}" }
        
        val httpStatus = when (e.status()) {
            400 -> HttpStatus.BAD_REQUEST
            401 -> HttpStatus.UNAUTHORIZED
            403 -> HttpStatus.FORBIDDEN
            404 -> HttpStatus.NOT_FOUND
            500 -> HttpStatus.INTERNAL_SERVER_ERROR
            502 -> HttpStatus.BAD_GATEWAY
            503 -> HttpStatus.SERVICE_UNAVAILABLE
            else -> HttpStatus.BAD_GATEWAY
        }
        
        return ResponseEntity
            .status(httpStatus)
            .body(ErrorResponse(
                error = "External Service Error",
                message = "M-Center API call failed: ${e.message}",
                timestamp = LocalDateTime.now(),
                status = httpStatus.value()
            ))
    }
    
    /**
     * Feign Retryable 예외 처리
     */
    @ExceptionHandler(RetryableException::class)
    fun handleRetryableException(e: RetryableException): ResponseEntity<ErrorResponse> {
        logger.error(e) { "Feign retryable error occurred: ${e.message}" }
        
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ErrorResponse(
                error = "Service Temporarily Unavailable",
                message = "External service is temporarily unavailable: ${e.message}",
                timestamp = LocalDateTime.now(),
                status = HttpStatus.SERVICE_UNAVAILABLE.value()
            ))
    }

    /**
     * IllegalArgumentException 처리
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(e: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        logger.warn { "Invalid argument: ${e.message}" }
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(
                error = "Invalid Request",
                message = e.message ?: "Invalid request parameters",
                timestamp = LocalDateTime.now(),
                status = HttpStatus.BAD_REQUEST.value()
            ))
    }
}

/**
 * 에러 응답 DTO
 */
data class ErrorResponse(
    val error: String,
    val message: String,
    val timestamp: LocalDateTime,
    val status: Int
)