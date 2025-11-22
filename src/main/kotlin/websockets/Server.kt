@file:Suppress("NoWildcardImports", "WildcardImport", "SpreadOperator")

package websockets

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.websocket.RemoteEndpoint
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import org.springframework.web.socket.server.standard.ServerEndpointExporter

@SpringBootApplication
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}

@Configuration(proxyBeanMethods = false)
@EnableWebSocketMessageBroker
class WebSocketConfig(
    private val tokenService: TokenService,
    private val stompErrorSender: StompErrorSender,
) : WebSocketMessageBrokerConfigurer {
    @Bean
    fun serverEndpoint() = ServerEndpointExporter()

    override fun configureMessageBroker(config: MessageBrokerRegistry) {
        // destination for broadcasts: /topic/...
        config.enableSimpleBroker("/topic", "/queue")

        // prefix for sending messages from clients: /app/...
        config.setApplicationDestinationPrefixes("/app")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry
            .addEndpoint("/ws") // WebSocket endpoint
            .setAllowedOriginPatterns("*") // allow CORS
            .withSockJS() // fallback
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(JwtChannelInterceptor(tokenService, stompErrorSender))
    }
}

val logger = KotlinLogging.logger {}

/**
 * If the websocket connection underlying this [RemoteEndpoint] is busy sending a message when a call is made to send
 * another one, for example if two threads attempt to call a send method concurrently, or if a developer attempts to
 * send a new message while in the middle of sending an existing one, the send method called while the connection
 * is already busy may throw an [IllegalStateException].
 *
 * This method wraps the call to [RemoteEndpoint.Basic.sendText] in a synchronized block to avoid this exception.
 */
fun RemoteEndpoint.Basic.sendTextSafe(message: String) {
    synchronized(this) {
        sendText(message)
    }
}
