package com.gamboo.minbody.config

import org.apache.catalina.connector.Connector
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory
import org.springframework.boot.web.server.WebServerFactoryCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class HttpHttpsConfig {
    
    @Value("\${server.http.port:8080}")
    private val httpPort: Int = 8080
    
    @Bean
    fun servletContainer(): WebServerFactoryCustomizer<TomcatServletWebServerFactory> {
        return WebServerFactoryCustomizer { tomcat ->
            tomcat.addAdditionalTomcatConnectors(createHttpConnector())
        }
    }
    
    private fun createHttpConnector(): Connector {
        val connector = Connector("org.apache.coyote.http11.Http11NioProtocol")
        connector.scheme = "http"
        connector.port = httpPort
        connector.secure = false
        connector.redirectPort = 5000 // HTTPS 포트로 리다이렉트 (선택사항)
        return connector
    }
}