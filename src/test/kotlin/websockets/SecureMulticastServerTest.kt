@file:Suppress("NoWildcardImports")

package websockets

import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import java.util.concurrent.CountDownLatch

private val logger = KotlinLogging.logger {}

@SpringBootTest(webEnvironment = RANDOM_PORT)
class SecureMulticastServerTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private val restTemplate = TestRestTemplate()

    private fun endpoint(): String = "ws://localhost:$port/secure/multicast"

    @Test
    fun `onOpen authorized users`() {
        logger.info { "Testing authorized connection to /secure/multicast endpoint" }
        val latch = CountDownLatch(3)
        val list1 = mutableListOf<String>()
        val list2 = mutableListOf<String>()
        val token1 = getToken(restTemplate, port, "alice", mapOf("room" to "room1"))
        val token2 = getToken(restTemplate, port, "bob", mapOf("room" to "room1"))

        val client1 = SecureClient(token1, list1, latch)
        val client2 = SecureClient(token2, list2, latch)
        client1.secureConnect(endpoint())
        client2.secureConnect(endpoint())
        latch.await()

        assertTrue(
            list1.any { it.contains("joined the room") },
            "Client1 should receive a connection notification",
        )
        assertTrue(
            list2.any { it.contains("joined the room") },
            "Client2 should receive a connection notification",
        )
        assertTrue(
            list1.size == 2 || list2.size == 2,
            "Expected one of the clients to have exactly 2 messages",
        )
    }

    @Test
    fun `onOpen unauthorized user`() {
        logger.info { "Testing unauthorized connection to /secure/multicast endpoint" }
        val latch = CountDownLatch(1)
        val list1 = mutableListOf<String>()
        val list2 = mutableListOf<String>()
        val token1 = "invalid-token"
        val token2 = getToken(restTemplate, port, "alice", mapOf("room" to "room1"))

        val client1 = SecureClient(token1, list1, latch)
        val client2 = SecureClient(token2, list2, latch)
        client1.secureConnect(endpoint())
        client2.secureConnect(endpoint())
        Thread.sleep(300)
        latch.await()

        assertTrue(
            list1.isEmpty(),
            "Client1 should not receive any messages",
        )
        assertTrue(
            list2.any { it.contains("joined the room") },
            "Client2 should receive a connection notification",
        )
        assertTrue(
            list2.size == 1,
            "Client2 should ONLY receive the connection notification",
        )
    }

    @Test
    fun `onChat same room`() {
        logger.info { "Testing same room" }
        val latch = CountDownLatch(5)
        val list1 = mutableListOf<String>()
        val list2 = mutableListOf<String>()
        val token1 = getToken(restTemplate, port, "alice", mapOf("room" to "room1"))
        val token2 = getToken(restTemplate, port, "bob", mapOf("room" to "room1"))

        val client1 = SecureClient(token1, list1, latch)
        val client2 = SecureClient(token2, list2, latch)
        client1.secureConnect(endpoint())
        client2.secureConnect(endpoint())
        Thread.sleep(300)
        client1.send("prueba")
        latch.await()
        assertTrue(
            list1.any { it.contains("prueba") },
            "Client1 should receive their own message",
        )
        assertTrue(
            list2.any { it.contains("prueba") },
            "Client2 should receive Client1's message",
        )
    }

    @Test
    fun `onChat different room`() {
        logger.info { "Testing different rooms" }
        val latch = CountDownLatch(3)
        val list1 = mutableListOf<String>()
        val list2 = mutableListOf<String>()
        val token1 = getToken(restTemplate, port, "alice", mapOf("room" to "room1"))
        val token2 = getToken(restTemplate, port, "bob", mapOf("room" to "room2"))

        val client1 = SecureClient(token1, list1, latch)
        val client2 = SecureClient(token2, list2, latch)
        client1.secureConnect(endpoint())
        client2.secureConnect(endpoint())
        Thread.sleep(300)
        client1.send("prueba")
        latch.await()

        assertTrue(
            list1.any { it.contains("prueba") },
            "Client1 should receive their own message",
        )
        assertTrue(
            !list2.any { it.contains("prueba") },
            "Client2 should NOT receive Client1's message",
        )
        assertTrue(
            list1.size == 2 || list2.size == 1,
            "Both clients only receive their own connection notification",
        )
    }

    @Test
    fun onClose() {
        logger.info { "Testing disconnection from /secure/multicast endpoint" }
        val latch = CountDownLatch(4)
        val list1 = mutableListOf<String>()
        val list2 = mutableListOf<String>()

        val token1 = getToken(restTemplate, port, "alice", mapOf("room" to "room1"))
        val token2 = getToken(restTemplate, port, "bob", mapOf("room" to "room1"))

        val client1 = SecureClient(token1, list1, latch)
        val client2 = SecureClient(token2, list2, latch)
        client1.secureConnect(endpoint())
        client2.secureConnect(endpoint())
        Thread.sleep(200)
        client1.close()
        latch.await()
        assertTrue(
            list2.any { it.contains("left the room") },
            "Client2 should receive a connection notification",
        )
    }
}
