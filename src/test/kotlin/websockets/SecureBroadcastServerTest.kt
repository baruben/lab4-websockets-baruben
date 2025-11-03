@file:Suppress("NoWildcardImports")

package websockets

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.websocket.ClientEndpoint
import jakarta.websocket.ClientEndpointConfig
import jakarta.websocket.ContainerProvider
import jakarta.websocket.OnMessage
import jakarta.websocket.OnOpen
import jakarta.websocket.Session
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import java.net.URI
import java.util.concurrent.CountDownLatch

private val logger = KotlinLogging.logger {}

@SpringBootTest(webEnvironment = RANDOM_PORT)
class SecureBroadcastServerTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private val restTemplate = TestRestTemplate()

    private fun endpoint(): String = "ws://localhost:$port/secure/broadcast"

    @Test
    fun `onOpen authorized users`() {
        logger.info { "Testing authorized connection to /secure/broadcast endpoint" }
        val latch = CountDownLatch(3)
        val list1 = mutableListOf<String>()
        val list2 = mutableListOf<String>()
        val token1 = getToken(restTemplate, port, "alice", mapOf("roles" to listOf("USER")))
        val token2 = getToken(restTemplate, port, "bob", mapOf("roles" to listOf("USER")))

        val client1 = SecureClient(token1, list1, latch)
        val client2 = SecureClient(token2, list2, latch)
        client1.secureConnect(endpoint())
        client2.secureConnect(endpoint())
        latch.await()

        assertTrue(
            list1.any { it.contains("connected") },
            "Client1 should receive a connection notification",
        )
        assertTrue(
            list2.any { it.contains("connected") },
            "Client2 should receive a connection notification",
        )
        assertTrue(
            list1.size == 2 || list2.size == 2,
            "Expected one of the clients to have exactly 2 messages",
        )
    }

    @Test
    fun `onOpen unauthorized user`() {
        logger.info { "Testing unauthorized connection to /secure/broadcast endpoint" }
        val latch = CountDownLatch(1)
        val list1 = mutableListOf<String>()
        val list2 = mutableListOf<String>()
        val token1 = "invalid-token"
        val token2 = getToken(restTemplate, port, "alice", mapOf("roles" to listOf("USER")))

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
            list2.any { it.contains("connected") },
            "Client2 should receive a connection notification",
        )
        assertTrue(
            list2.size == 1,
            "Client2 should ONLY receive the connection notification",
        )
    }

    @Test
    fun onChat() {
        logger.info { "Testing message secure broadcast" }
        val latch = CountDownLatch(5)
        val list1 = mutableListOf<String>()
        val list2 = mutableListOf<String>()

        val token1 = getToken(restTemplate, port, "alice", mapOf("roles" to listOf("USER")))
        val token2 = getToken(restTemplate, port, "bob", mapOf("roles" to listOf("USER")))
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
    fun `onChat admin kicks user`() {
        logger.info { "Testing admin kicks user" }
        val latch = CountDownLatch(5)
        val adminList = mutableListOf<String>()
        val list = mutableListOf<String>()
        val adminToken = getToken(restTemplate, port, "admin", mapOf("roles" to listOf("ADMIN")))
        val token = getToken(restTemplate, port, "bob", mapOf("roles" to listOf("USER")))

        val admin = SecureClient(adminToken, adminList, latch)
        val user = SecureClient(token, list, latch)
        admin.secureConnect(endpoint())
        user.secureConnect(endpoint())
        Thread.sleep(300)
        admin.send("/kick bob")
        latch.await()
        assertTrue(
            adminList.any { it.contains("bob was kicked by admin") },
            "Admin should receive kicking notification",
        )
        assertTrue(
            list.size == 1,
            "Client2 should ONLY receive the connection notification",
        )
    }

    @Test
    fun `onChat non-admin doesn't kick user`() {
        logger.info { "Testing non-admin doesn't kick user" }
        val latch = CountDownLatch(4)
        val list1 = mutableListOf<String>()
        val list2 = mutableListOf<String>()
        val token1 = getToken(restTemplate, port, "alice", mapOf("roles" to listOf("USER")))
        val token2 = getToken(restTemplate, port, "bob", mapOf("roles" to listOf("USER")))

        val client1 = SecureClient(token1, list1, latch)
        val client2 = SecureClient(token2, list2, latch)
        client1.secureConnect(endpoint())
        client2.secureConnect(endpoint())
        Thread.sleep(300)
        client1.send("/kick bob")
        latch.await()

        assertTrue(
            list1.any { it.contains("not authorized to kick users") },
            "Client1 should receive kicking notification",
        )
        assertTrue(
            list2.size == 1,
            "Client2 should ONLY receive the connection notification",
        )
    }

    @Test
    fun onClose() {
        logger.info { "Testing disconnection from /broadcast endpoint" }
        val latch = CountDownLatch(4)
        val list1 = mutableListOf<String>()
        val list2 = mutableListOf<String>()

        val token1 = getToken(restTemplate, port, "alice", mapOf("roles" to listOf("USER")))
        val token2 = getToken(restTemplate, port, "bob", mapOf("roles" to listOf("USER")))

        val client1 = SecureClient(token1, list1, latch)
        val client2 = SecureClient(token2, list2, latch)
        client1.secureConnect(endpoint())
        client2.secureConnect(endpoint())
        Thread.sleep(200)
        client1.close()
        latch.await()
        assertTrue(
            list2.any { it.contains("disconnected") },
            "Client2 should receive a connection notification",
        )
    }
}

fun getToken(
    restTemplate: TestRestTemplate,
    port: Int,
    username: String,
    claims: Map<String, Any>,
): String {
    val body = mapOf("username" to username, "claims" to claims)
    val response = restTemplate.postForEntity("http://localhost:$port/token", body, AuthController.AuthResponse::class.java)
    assertEquals(HttpStatus.OK, response.statusCode)
    return response.body!!.token
}

@ClientEndpoint(configurator = AuthHeaderConfigurator::class)
class SecureClient(
    private val token: String,
    private val list: MutableList<String>,
    private val latch: CountDownLatch,
) {
    private lateinit var session: Session

    @OnOpen
    fun onOpen(session: Session) {
        this.session = session
        logger.info { "Client connected: ${session.id}" }
    }

    @OnMessage
    fun onMessage(msg: String) {
        logger.info { "Client received: $msg" }
        list.add(msg)
        latch.countDown()
    }

    fun send(text: String) {
        if (this::session.isInitialized && session.isOpen) {
            session.asyncRemote.sendText(text)
        }
    }

    fun secureConnect(uri: String) {
        AuthContext.token = token
        ContainerProvider.getWebSocketContainer().connectToServer(this, URI(uri))
    }

    fun close() {
        if (this::session.isInitialized && session.isOpen) {
            session.close()
        }
    }
}

object AuthContext {
    var token: String? = null
}

class AuthHeaderConfigurator : ClientEndpointConfig.Configurator() {
    override fun beforeRequest(headers: MutableMap<String, MutableList<String>>?) {
        AuthContext.token?.let {
            headers?.put("Authorization", mutableListOf("Bearer $it"))
        }
    }
}
