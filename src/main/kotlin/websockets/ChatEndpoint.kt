package websockets

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Lazy
import org.springframework.context.event.EventListener
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.simp.user.SimpSession
import org.springframework.messaging.simp.user.SimpSubscription
import org.springframework.messaging.simp.user.SimpUser
import org.springframework.messaging.simp.user.SimpUserRegistry
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.ErrorMessage
import org.springframework.messaging.support.MessageBuilder
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller
import org.springframework.stereotype.Service
import org.springframework.web.socket.messaging.SessionConnectedEvent
import org.springframework.web.socket.messaging.SessionDisconnectEvent
import org.springframework.web.socket.messaging.SessionSubscribeEvent
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent
import java.security.Principal

@Service
class StompErrorSender(
    @param:Lazy private val clientOutboundChannel: MessageChannel,
) {
    fun sendErrorAndDisconnect(
        sessionId: String,
        message: String,
    ) {
        val accessor = StompHeaderAccessor.create(StompCommand.ERROR)
        accessor.sessionId = sessionId
        accessor.message = message

        val errorMsg =
            MessageBuilder<ErrorMessage>.createMessage(ByteArray(0), accessor.messageHeaders)

        clientOutboundChannel.send(errorMsg)
    }
}

class JwtChannelInterceptor(
    private val tokenService: TokenService,
    private val stompErrorSender: StompErrorSender,
) : ChannelInterceptor {
    override fun preSend(
        message: Message<*>,
        channel: MessageChannel,
    ): Message<*>? {
        val accessor =
            MessageHeaderAccessor.getAccessor(
                message,
                StompHeaderAccessor::class.java,
            )!!
        if (accessor.command == StompCommand.CONNECT) {
            val authHeader = accessor.getFirstNativeHeader("Authorization")
            val token = authHeader?.removePrefix("Bearer ")
            if (token.isNullOrBlank() || !tokenService.isValid(token)) {
                stompErrorSender.sendErrorAndDisconnect(
                    sessionId = accessor.sessionId!!,
                    message = "Unauthorized: Missing or invalid JWT",
                )

                return null
            }
            val username = tokenService.extractUsername(token)
            val roles = tokenService.extractRoles(token)
            accessor.user = StompPrincipal(username, roles)
        }

        return message
    }
}

class StompPrincipal(
    private val name: String,
    private val roles: List<String> = emptyList(), // empty = normal user, "ADMIN" = admin
) : Principal {
    override fun getName() = name

    fun isAdmin() = roles.contains("ADMIN")
}

@Component
class WebSocketEventListener(
    private val messaging: SimpMessagingTemplate,
    private val simpUserRegistry: SimpUserRegistry,
) {
    private val logger = KotlinLogging.logger {}

    @EventListener
    fun handleConnect(event: SessionConnectedEvent) {
        val accessor = StompHeaderAccessor.wrap(event.message)
        val username = accessor.user?.name ?: "guest"
        logger.info { "User connected: $username" }
    }

    @EventListener
    fun handleSubscribe(event: SessionSubscribeEvent) {
        val accessor = StompHeaderAccessor.wrap(event.message)
        val username = accessor.user?.name ?: "guest"
        val destination = accessor.destination ?: return

        logger.info { "User: $username subscribed: $destination" }

        // Broadcast join message to the room
        messaging.convertAndSend(destination, ChatMessage("System", "$username joined"))
    }

    @EventListener
    fun handleUnsubscribe(event: SessionUnsubscribeEvent) {
        val accessor = StompHeaderAccessor.wrap(event.message)
        val username = accessor.user?.name ?: "guest"
        val subscriptionId = accessor.subscriptionId ?: return

        // Find the destination for this subscription using SimpUserRegistry
        val sessionId = accessor.sessionId
        val subscriptions = getSubscriptions(sessionId!!)
        println(subscriptions)
        val subscription = subscriptions?.firstOrNull { it.id == subscriptionId } ?: return
        val destination = subscription.destination

        messaging.convertAndSend(destination, ChatMessage("System", "$username left"))
    }

    @EventListener
    fun handleDisconnect(event: SessionDisconnectEvent) {
        val accessor = StompHeaderAccessor.wrap(event.message)
        val username = accessor.user?.name ?: "guest"
        println(username)
        val sessionId = event.sessionId

        // Iterate all subscriptions for this session
        val subscriptions = getSubscriptions(sessionId)
        println(subscriptions)
        subscriptions?.forEach { sub ->
            messaging.convertAndSend(sub.destination, ChatMessage("System", "$username disconnected"))
        }

        logger.info { "User disconnected: $username" }
    }

    private fun getSubscriptions(sessionId: String): Set<SimpSubscription>? {
        val sessions: List<SimpSession> =
            simpUserRegistry.users
                .flatMap { user: SimpUser -> user.sessions }

        val session = sessions.firstOrNull { it.id == sessionId }
        return session?.subscriptions
    }
}

// IMPORTANTE: constructor vacio para client frameHandler()
data class ChatMessage(
    val from: String = "",
    val text: String = "",
)

@Controller
class ChatController(
    private val messaging: SimpMessagingTemplate,
    private val simpUserRegistry: SimpUserRegistry,
) {
    private val logger = KotlinLogging.logger {}

    @MessageMapping("{room}/send") // client sends to /app/secure/{room}/send
    fun secure(
        @DestinationVariable room: String,
        message: ChatMessage,
        principal: Principal,
    ) {
        logger.info { "Message: $message to: /topic/$room" }
        if (message.text.startsWith("/")) {
            handleCommand(message.text, principal as StompPrincipal, room)
        } else {
            val msg = ChatMessage(principal.name, message.text)
            // broadcast to /topic/secure/{room}
            messaging.convertAndSend("/topic/$room", msg)
        }
    }

    private fun handleCommand(
        command: String,
        principal: StompPrincipal,
        room: String? = null,
    ) {
        if (!principal.isAdmin()) {
            messaging.convertAndSendToUser(
                principal.name,
                "/queue/errors",
                ChatMessage(command, "You are not authorized to run commands"),
            )
            return
        }
        when {
            command.startsWith("/subscribers") -> {
                val subscribers =
                    simpUserRegistry.users
                        .flatMap { user ->
                            user.sessions.flatMap { session ->
                                session.subscriptions.mapNotNull { sub ->
                                    if (sub.destination == "/topic/$room") user.name else null
                                }
                            }
                        }.distinct()

                messaging.convertAndSendToUser(
                    principal.name,
                    "/queue/command",
                    ChatMessage("/subscribers", "Subscribers in $room: ${subscribers.joinToString(", ")}"),
                )
            }

            else -> {
                messaging.convertAndSendToUser(
                    principal.name,
                    "/queue/errors",
                    ChatMessage(command, "Unknown command: $command"),
                )
            }
        }
    }
}
