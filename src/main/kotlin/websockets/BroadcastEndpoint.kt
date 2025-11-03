package websockets

import jakarta.websocket.CloseReason
import jakarta.websocket.OnClose
import jakarta.websocket.OnError
import jakarta.websocket.OnMessage
import jakarta.websocket.OnOpen
import jakarta.websocket.Session
import jakarta.websocket.server.ServerEndpoint
import org.springframework.stereotype.Component
import java.util.concurrent.CopyOnWriteArraySet

@ServerEndpoint("/broadcast")
@Component
class BroadcastEndpoint {
    companion object {
        // Shared among all instances of this endpoint
        private val sessions: MutableSet<Session> = CopyOnWriteArraySet()
    }

    private fun broadcast(message: String) {
        sessions.forEach { s ->
            logger.info { "Sending message ... Session ${s.id}" }
            runCatching {
                if (s.isOpen) {
                    with(s.basicRemote) {
                        sendTextSafe(message)
                    }
                }
            }.onFailure {
                logger.error(it) { "Error while sending message to ${s.id}" }
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
        logger.info { "Server Connected ... Session ${session.id}" }
        sessions.add(session)
        broadcast("New user ${session.id} connected")
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
        logger.info { "Session ${session.id} closed because of $closeReason" }
        sessions.remove(session)
        broadcast("User ${session.id} disconnected")
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
        logger.info { "New Message ... Session ${session.id} ... $message" }
        broadcast("[${session.id}]: $message")
    }

    @OnError
    fun onError(
        session: Session,
        errorReason: Throwable,
    ) {
        logger.error(errorReason) { "Session ${session.id} closed because of ${errorReason.javaClass.name}" }
    }
}
