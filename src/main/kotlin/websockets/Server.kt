@file:Suppress("NoWildcardImports", "WildcardImport", "SpreadOperator")

package websockets

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import jakarta.websocket.RemoteEndpoint
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.server.standard.ServerEndpointExporter

@SpringBootApplication
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}

@Configuration(proxyBeanMethods = false)
class WebSocketConfig(
    private val tokenService: TokenService,
) {
    @Bean
    fun serverEndpoint() = ServerEndpointExporter()

    @PostConstruct
    fun init() {
        WebSocketConfigurator.tokenService = tokenService
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
