package websockets

import jakarta.websocket.CloseReason
import jakarta.websocket.CloseReason.CloseCodes
import jakarta.websocket.OnClose
import jakarta.websocket.OnError
import jakarta.websocket.OnMessage
import jakarta.websocket.OnOpen
import jakarta.websocket.Session
import jakarta.websocket.server.ServerEndpoint
import org.springframework.stereotype.Component
import java.util.Locale
import java.util.Scanner

@ServerEndpoint("/eliza")
@Component
class ElizaEndpoint {
    private val eliza = Eliza()

    /**
     * Successful connection
     *
     * @param session
     */
    @OnOpen
    fun onOpen(session: Session) {
        logger.info { "Server Connected ... Session ${session.id}" }
        with(session.basicRemote) {
            sendTextSafe("The doctor is in.")
            sendTextSafe("What's on your mind?")
            sendTextSafe("---")
        }
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
        logger.info { "Server Message ... Session ${session.id}" }
        val currentLine = Scanner(message.lowercase(Locale.getDefault()))
        if (currentLine.findInLine("bye") == null) {
            logger.info { "Server received \"${message}\"" }
            runCatching {
                if (session.isOpen) {
                    with(session.basicRemote) {
                        sendTextSafe(eliza.respond(currentLine))
                        sendTextSafe("---")
                    }
                }
            }.onFailure {
                logger.error(it) { "Error while sending message" }
                session.close(CloseReason(CloseCodes.CLOSED_ABNORMALLY, "I'm sorry, I didn't understand that."))
            }
        } else {
            session.close(CloseReason(CloseCodes.NORMAL_CLOSURE, "Alright then, goodbye!"))
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
