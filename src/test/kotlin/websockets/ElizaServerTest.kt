@file:Suppress("NoWildcardImports")

package websockets

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.websocket.ClientEndpoint
import jakarta.websocket.ContainerProvider
import jakarta.websocket.OnMessage
import jakarta.websocket.Session
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import java.net.URI
import java.util.concurrent.CountDownLatch

private val logger = KotlinLogging.logger {}

@SpringBootTest(webEnvironment = RANDOM_PORT)
class ElizaServerTest {
    @LocalServerPort
    private var port: Int = 0

    @Test
    fun onOpen() {
        logger.info { "This is the test worker" }
        val latch = CountDownLatch(3)
        val list = mutableListOf<String>()

        val client = SimpleClient(list, latch)
        client.connect("ws://localhost:$port/eliza")
        latch.await()
        assertEquals(3, list.size)
        assertEquals("The doctor is in.", list[0])
    }

    @Test
    fun onChat() {
        logger.info { "Test thread" }
        val latch = CountDownLatch(4)
        val list = mutableListOf<String>()

        val client = ComplexClient(list, latch)
        client.connect("ws://localhost:$port/eliza")
        latch.await()
        val size = list.size
        // 1. EXPLAIN WHY size = list.size IS NECESSARY
        // Because the WebSocket communication is asynchronous, capturing list.size at this point
        // ensures we work with a stable snapshot of received messages after latch.await() completes.

        // 2. REPLACE BY assertXXX expression that checks an interval; assertEquals must not be used;
        // 3. EXPLAIN WHY assertEquals CANNOT BE USED AND WHY WE SHOULD CHECK THE INTERVAL
        // The number of messages can vary depending on timing or server behavior, so we check
        // that the message count falls within an acceptable range instead of asserting exact equality.
        assertTrue(size in 3..6, "Expected between 3 and 6 messages but got $size")

        // 4. COMPLETE assertEquals(XXX, list[XXX])
        // The first message should always be the greeting.
        assertEquals("The doctor is in.", list[0])
    }
}

@ClientEndpoint
class SimpleClient(
    private val list: MutableList<String>,
    private val latch: CountDownLatch,
) {
    @OnMessage
    fun onMessage(message: String) {
        logger.info { "Client received: $message" }
        list.add(message)
        latch.countDown()
    }
}

@ClientEndpoint
class ComplexClient(
    private val list: MutableList<String>,
    private val latch: CountDownLatch,
) {
    @OnMessage
    fun onMessage(
        message: String,
        session: Session,
    ) {
        logger.info { "Client received: $message" }
        list.add(message)
        latch.countDown()
        // 5. COMPLETE if (expression) {
        // 6. COMPLETE   sentence
        // }
        // When the greeting arrives, send the user's message to the server.
        if (message.contains("doctor", ignoreCase = true)) {
            session.asyncRemote.sendText("I am feeling sad")
        }
    }
}

fun Any.connect(uri: String) {
    ContainerProvider.getWebSocketContainer().connectToServer(this, URI(uri))
}
