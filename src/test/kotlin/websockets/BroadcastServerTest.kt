@file:Suppress("NoWildcardImports")

package websockets

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.websocket.ClientEndpoint
import jakarta.websocket.ContainerProvider
import jakarta.websocket.OnMessage
import jakarta.websocket.OnOpen
import jakarta.websocket.Session
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.URI
import java.util.concurrent.CountDownLatch

private val logger = KotlinLogging.logger {}

@SpringBootTest(webEnvironment = RANDOM_PORT)
class BroadcastServerTest {
    @LocalServerPort
    private var port: Int = 0

    @Test
    fun onOpen() {
        logger.info { "Testing connection to /broadcast endpoint" }
        val latch = CountDownLatch(3)
        val list1 = mutableListOf<String>()
        val list2 = mutableListOf<String>()

        val client1 = BroadcastClient(list1, latch)
        val client2 = BroadcastClient(list2, latch)
        client1.connect("ws://localhost:$port/broadcast")
        client2.connect("ws://localhost:$port/broadcast")
        latch.await()
        assertTrue(
            list1.any { it.contains("connected") },
            "Client1 should receive a connection notification"
        )
        assertTrue(
            list2.any { it.contains("connected") },
            "Client2 should receive a connection notification"
        )
        assertTrue(
            list1.size == 2 || list2.size == 2,
            "Expected one of the clients to have exactly 2 messages"
        )
    }

    @Test
    fun onChat() {
        logger.info { "Testing message broadcast" }
        val latch = CountDownLatch(5)
        val list1 = mutableListOf<String>()
        val list2 = mutableListOf<String>()

        val client1 = BroadcastClient(list1, latch)
        val client2 = BroadcastClient(list2, latch)
        client1.connect("ws://localhost:$port/broadcast")
        client2.connect("ws://localhost:$port/broadcast")
        Thread.sleep(200)
        client1.send("prueba")
        latch.await()
        assertTrue(
            list1.any { it.contains("prueba") },
            "Client1 should receive their own message"
        )
        assertTrue(
            list2.any { it.contains("prueba") },
            "Client2 should receive Client1's message"
        )
    }

    @Test
    fun onClose() {
        logger.info { "Testing disconnection from /broadcast endpoint" }
        val latch = CountDownLatch(4)
        val list1 = mutableListOf<String>()
        val list2 = mutableListOf<String>()

        val client1 = BroadcastClient(list1, latch)
        val client2 = BroadcastClient(list2, latch)
        client1.connect("ws://localhost:$port/broadcast")
        client2.connect("ws://localhost:$port/broadcast")
        Thread.sleep(200)
        client1.close()
        latch.await()
        assertTrue(
            list2.any { it.contains("disconnected") },
            "Client2 should receive a connection notification"
        )
    }
}

@ClientEndpoint
class BroadcastClient(
    private val list: MutableList<String>,
    private val latch: CountDownLatch,
) {
    private lateinit var session: Session

    @OnOpen
    fun onOpen(session: Session) {
        logger.info { "Client connected: ${session.id}" }
        this.session = session
    }

    @OnMessage
    fun onMessage(message: String) {
        logger.info { "Client received: $message" }
        list.add(message)
        latch.countDown()
    }

    fun send(text: String) {
        session.asyncRemote.sendText(text)
    }

    fun close() {
        if (this::session.isInitialized && session.isOpen) {
            logger.info { "Client disconnected: ${session.id}" }
            session.close()
        }
    }
}
