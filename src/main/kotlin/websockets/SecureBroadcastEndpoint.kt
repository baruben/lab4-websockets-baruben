package websockets

import jakarta.websocket.CloseReason
import jakarta.websocket.OnClose
import jakarta.websocket.OnError
import jakarta.websocket.OnMessage
import jakarta.websocket.OnOpen
import jakarta.websocket.Session
import jakarta.websocket.server.ServerEndpoint
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@ServerEndpoint("/secure/broadcast", configurator = WebSocketConfigurator::class)
@Component
class SecureBroadcastEndpoint {
    companion object {
        // Shared among all instances of this endpoint
        private val sessions = ConcurrentHashMap<String, Session>()
    }

    private fun broadcast(message: String) {
        sessions.values.forEach { s ->
            val username = s.userProperties["username"]
            logger.info { "Sending message ... User $username" }
            runCatching {
                if (s.isOpen) {
                    with(s.basicRemote) {
                        sendTextSafe(message)
                    }
                }
            }.onFailure {
                logger.error(it) { "Error while sending message to $username" }
            }
        }
    }

    private fun kick(
        target: String,
        admin: String,
    ) {
        val session = sessions[target]
        if (session == null || !session.isOpen) {
            // User not found or already disconnected
            val adminSession = sessions[admin]
            runCatching {
                if (adminSession != null && adminSession.isOpen) {
                    with(adminSession.basicRemote) {
                        sendTextSafe("User $target not found or not connected")
                    }
                }
            }.onFailure {
                logger.error(it) { "Error while sending message to $admin" }
            }
            return
        }

        runCatching {
            session.close(
                CloseReason(
                    CloseReason.CloseCodes.NORMAL_CLOSURE,
                    "You have been kicked by $admin",
                ),
            )
        }.onSuccess {
            broadcast("$target was kicked by $admin")
            sessions.remove(target)
            logger.info { "User $admin kicked User $target" }
        }.onFailure {
            logger.error(it) { "Failed to kick user $target" }
        }
    }

    /**
     * Successful connection
     *
     * @param session
     */
    @OnOpen
    fun onOpen(session: Session) {
        val userProperties = session.userProperties
        val authenticated = userProperties["ws.authenticated"] as? Boolean ?: false

        if (!authenticated) {
            logger.info { "Server Refused connection for unauthorized user" }

            // Close with 1008 policy violation (policy violation), include reason
            session.close(
                CloseReason(
                    CloseReason.CloseCodes.VIOLATED_POLICY,
                    "Unauthorized",
                ),
            )
            return
        }
        val username = userProperties["ws.username"] as String
        val claims = userProperties["ws.claims"] as Map<String, Any>
        val roles = claims["roles"] as? List<String>

        // Save the username and roles to the session userProperties
        session.userProperties["username"] = username
        session.userProperties["roles"] = roles ?: emptyList<String>()

        logger.info { "Server Connected ... Session ${session.id} ... User $username ... Roles $roles" }
        sessions[username] = session
        broadcast("New user $username ($roles) connected")
    }

    /**
     * Connection closure
     *
     * @param session
     */
    @OnClose
    fun onClose(
        session: Session,
        closeReason: CloseReason,
    ) {
        val username = session.userProperties["username"] as? String
        if (username != null) {
            logger.info { "User $username's session ${session.id} closed because of $closeReason" }
            sessions.remove(username)
            broadcast("User $username disconnected")
        }
    }

    /**
     * Message received
     *
     * @param message
     */
    @OnMessage
    fun onMsg(
        message: String,
        session: Session,
    ) {
        val username = session.userProperties["username"] as String
        val roles = session.userProperties["roles"] as List<String>
        logger.info { "New Message ... User $username ... Roles $roles ... $message" }

        // Enforce role-based control: only users with role can send messages
        if (roles.isEmpty()) {
            runCatching {
                session.basicRemote.sendText("You are not authorized to send messages")
            }
            return
        }

        val isAdmin = roles.map { it }.contains("ADMIN")
        val isKick = message.startsWith("/kick")

        // Enforce role-based control: only admins can kick users
        if (isKick && isAdmin) {
            val parts = message.split(" ")
            if (parts.size != 2) {
                session.basicRemote.sendText("Usage: /kick <username>")
                return
            }

            val target = parts[1]
            kick(target, username)
        } else if (isKick) {
            session.basicRemote.sendText("You are not authorized to kick users")
        } else {
            broadcast("[$username]: $message")
        }
    }

    @OnError
    fun onError(
        session: Session,
        errorReason: Throwable,
    ) {
        logger.error(errorReason) { "Session ${session.id} closed because of ${errorReason.javaClass.name}" }
    }
}
