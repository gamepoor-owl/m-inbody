package com.gamboo.minbody.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.WebSocketHandlerDecorator
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory

@Configuration
class WebSocketSessionConfig : WebSocketHandlerDecoratorFactory {
    
    override fun decorate(handler: WebSocketHandler): WebSocketHandler {
        return SessionIdWebSocketHandlerDecorator(handler)
    }
}

class SessionIdWebSocketHandlerDecorator(delegate: WebSocketHandler) : WebSocketHandlerDecorator(delegate) {
    
    override fun afterConnectionEstablished(session: WebSocketSession) {
        // Send session ID as part of the initial handshake
        // This will be handled by the actual PacketWebSocketHandler
        super.afterConnectionEstablished(session)
    }
}