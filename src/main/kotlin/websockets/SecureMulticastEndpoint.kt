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
import java.util.concurrent.CopyOnWriteArraySet

@ServerEndpoint("/secure/multicast", configurator = WebSocketConfigurator::class)
@Component
class SecureMulticastEndpoint {
    companion object {
        // Shared among all instances of this endpoint
        private val sessions = ConcurrentHashMap<String, MutableSet<Session>>()
    }

    private fun multicast(
        room: String,
        message: String,
    ) {
        sessions[room]?.forEach { s ->
            logger.info { "Sending message ... User ${s.userProperties["username"]}" }
            runCatching {
                if (s.isOpen) {
                    with(s.basicRemote) {
                        sendTextSafe(message)
                    }
                }
            }.onFailure {
                logger.error(it) { "Error while sending message to ${s.userProperties["username"]}" }
            }
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
        val room = claims["room"] as? String

        if (room.isNullOrEmpty()) {
            logger.info { "Server Refused connection for user without a room" }

            // Close with 1008 policy violation (policy violation), include reason
            session.close(
                CloseReason(
                    CloseReason.CloseCodes.VIOLATED_POLICY,
                    "No room specified",
                ),
            )
            return
        }

        // Store user info
        session.userProperties["username"] = username
        session.userProperties["room"] = room

        logger.info { "Server Connected ... Session ${session.id} ... User $username ... Room $room" }

        sessions.compute(room) { _, existing ->
            val updated = existing ?: CopyOnWriteArraySet()
            updated.add(session)
            updated
        }

        multicast(room, "New user $username joined the room")
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
            val room = session.userProperties["room"] as String
            logger.info { "User $username's session ${session.id} closed because of $closeReason" }

            sessions[room]?.let { sessionSet ->
                sessionSet.remove(session)
                if (sessionSet.isEmpty()) {
                    sessions.remove(room)
                    logger.info { "Room $room is now empty and was removed from active sessions." }
                }
            }

            multicast(room, "User $username left the room")
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
        val room = session.userProperties["room"] as String
        logger.info { "New Message ... User $username ... Room $room ... $message" }

        multicast(room, "[$username]: $message")
    }

    @OnError
    fun onError(
        session: Session,
        errorReason: Throwable,
    ) {
        logger.error(errorReason) { "Session ${session.id} closed because of ${errorReason.javaClass.name}" }
    }
}
