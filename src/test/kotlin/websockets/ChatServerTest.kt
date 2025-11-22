@file:Suppress("NoWildcardImports")

package websockets

import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.messaging.simp.stomp.ConnectionLostException
import org.springframework.messaging.simp.stomp.StompFrameHandler
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import org.springframework.web.socket.sockjs.client.SockJsClient
import org.springframework.web.socket.sockjs.client.WebSocketTransport
import java.lang.reflect.Type
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

@SpringBootTest(webEnvironment = RANDOM_PORT)
class ChatServerTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private val restTemplate = TestRestTemplate()

    private fun endpoint(): String = "ws://localhost:$port/ws"

    @Test
    fun `authorized users join a room`() {
        val latch = CountDownLatch(3)
        val list1 = mutableListOf<String>()
        val list2 = mutableListOf<String>()
        val token1 = getToken(restTemplate, port, "alice")
        val token2 = getToken(restTemplate, port, "bob")

        val client1 = stompClientWithToken()
        val client2 = stompClientWithToken()
        val handler1 = object : StompSessionHandlerAdapter() {}
        val handler2 = object : StompSessionHandlerAdapter() {}
        val session1 = connectWithToken(client1, endpoint(), token1, handler1)
        val session2 = connectWithToken(client2, endpoint(), token2, handler2)

        session1.subscribe("/topic/test", TestStompFrameHandler(latch, list1))
        Thread.sleep(500)
        session2.subscribe("/topic/test", TestStompFrameHandler(latch, list2))

        latch.await()
        assertTrue(
            list1.any { it.contains("joined") },
            "Client1 should receive a connection notification",
        )
        assertTrue(
            list2.any { it.contains("joined") },
            "Client2 should receive a connection notification",
        )
        assertTrue(
            list1.size == 2 || list2.size == 2,
            "Expected one of the clients to have exactly 2 messages",
        )
    }

    @Test
    fun `unauthorized user cannot connect`() {
        val latch = CountDownLatch(2)
        val list1 = mutableListOf<String>()
        val list2 = mutableListOf<String>()
        val token1 = "invalid-token"
        val token2 = getToken(restTemplate, port, "alice")

        val client1 = stompClientWithToken()
        val client2 = stompClientWithToken()
        val handler1 = TestStompSessionHandler(latch, list1)
        val handler2 = object : StompSessionHandlerAdapter() {}

        val exception =
            assertThrows<ExecutionException> {
                connectWithToken(client1, endpoint(), token1, handler1)
            }
        assertTrue(exception.cause is ConnectionLostException)

        val session2 = connectWithToken(client2, endpoint(), token2, handler2)

        session2.subscribe("/topic/test", TestStompFrameHandler(latch, list2))

        latch.await()
        assertTrue(
            list1.any { it.contains("Connection closed") },
            "Client1 should receive connection closed error",
        )
        assertTrue(
            list2.any { it.contains("joined") },
            "Client2 should receive a connection notification",
        )
        assertTrue(
            list2.size == 1,
            "Client2 should ONLY receive the connection notification",
        )
    }

    @Test
    fun `authorized users chat`() {
        val latch = CountDownLatch(5)
        val list1 = mutableListOf<String>()
        val list2 = mutableListOf<String>()
        val token1 = getToken(restTemplate, port, "alice")
        val token2 = getToken(restTemplate, port, "bob")

        val client1 = stompClientWithToken()
        val client2 = stompClientWithToken()
        val handler1 = object : StompSessionHandlerAdapter() {}
        val handler2 = object : StompSessionHandlerAdapter() {}
        val session1 = connectWithToken(client1, endpoint(), token1, handler1)
        val session2 = connectWithToken(client2, endpoint(), token2, handler2)

        session1.subscribe("/topic/test", TestStompFrameHandler(latch, list1))
        Thread.sleep(500)
        session2.subscribe("/topic/test", TestStompFrameHandler(latch, list2))
        Thread.sleep(500)
        session1.send("/app/test/send", ChatMessage("alice", "prueba"))

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
    fun `admin sends command subscribers`() {
        val latch = CountDownLatch(4)
        val listAdmin = mutableListOf<String>()
        val list2 = mutableListOf<String>()
        val tokenAdmin = getToken(restTemplate, port, "alice", mapOf("roles" to listOf("ADMIN")))
        val token2 = getToken(restTemplate, port, "bob")

        val clientAdmin = stompClientWithToken()
        val client2 = stompClientWithToken()
        val handlerAdmin = object : StompSessionHandlerAdapter() {}
        val handler2 = object : StompSessionHandlerAdapter() {}
        val sessionAdmin = connectWithToken(clientAdmin, endpoint(), tokenAdmin, handlerAdmin)
        val session2 = connectWithToken(client2, endpoint(), token2, handler2)

        sessionAdmin.subscribe("/topic/test", TestStompFrameHandler(latch, listAdmin))
        sessionAdmin.subscribe("/user/queue/command", TestStompFrameHandler(latch, listAdmin))
        Thread.sleep(500)
        session2.subscribe("/topic/test", TestStompFrameHandler(latch, list2))
        Thread.sleep(500)
        sessionAdmin.send("/app/test/send", ChatMessage("alice", "/subscribers"))

        latch.await()
        assertTrue(
            listAdmin.any { it.contains("Subscribers") },
            "Admin should receive subscribers list",
        )
        assertTrue(
            list2.size == 1,
            "Client2 should ONLY receive the connection notification",
        )
    }

    @Test
    fun `not admin cannot send command subscribers`() {
        val latch = CountDownLatch(4)
        val list1 = mutableListOf<String>()
        val list2 = mutableListOf<String>()
        val token1 = getToken(restTemplate, port, "alice")
        val token2 = getToken(restTemplate, port, "bob")

        val client1 = stompClientWithToken()
        val client2 = stompClientWithToken()
        val handler1 = object : StompSessionHandlerAdapter() {}
        val handler2 = object : StompSessionHandlerAdapter() {}
        val session1 = connectWithToken(client1, endpoint(), token1, handler1)
        val session2 = connectWithToken(client2, endpoint(), token2, handler2)

        session1.subscribe("/topic/test", TestStompFrameHandler(latch, list1))
        session1.subscribe("/user/queue/errors", TestStompFrameHandler(latch, list1))
        Thread.sleep(500)
        session2.subscribe("/topic/test", TestStompFrameHandler(latch, list2))
        Thread.sleep(500)
        session1.send("/app/test/send", ChatMessage("alice", "/subscribers"))

        latch.await()
        assertTrue(
            list1.any { it.contains("not authorized") },
            "Client1 should receive not authorized error",
        )
        assertTrue(
            list2.size == 1,
            "Client2 should ONLY receive the connection notification",
        )
    }

    @Test
    fun `admin cannot send unknown command`() {
        val latch = CountDownLatch(4)
        val listAdmin = mutableListOf<String>()
        val list2 = mutableListOf<String>()
        val tokenAdmin = getToken(restTemplate, port, "alice", mapOf("roles" to listOf("ADMIN")))
        val token2 = getToken(restTemplate, port, "bob")

        val clientAdmin = stompClientWithToken()
        val client2 = stompClientWithToken()
        val handlerAdmin = object : StompSessionHandlerAdapter() {}
        val handler2 = object : StompSessionHandlerAdapter() {}
        val sessionAdmin = connectWithToken(clientAdmin, endpoint(), tokenAdmin, handlerAdmin)
        val session2 = connectWithToken(client2, endpoint(), token2, handler2)

        sessionAdmin.subscribe("/topic/test", TestStompFrameHandler(latch, listAdmin))
        sessionAdmin.subscribe("/user/queue/errors", TestStompFrameHandler(latch, listAdmin))
        Thread.sleep(500)
        session2.subscribe("/topic/test", TestStompFrameHandler(latch, list2))
        Thread.sleep(500)
        sessionAdmin.send("/app/test/send", ChatMessage("alice", "/command"))

        latch.await()
        assertTrue(
            listAdmin.any { it.contains("Unknown command") },
            "Client1 should receive unknown command error",
        )
        assertTrue(
            list2.size == 1,
            "Client2 should ONLY receive the connection notification",
        )
    }

    private fun getToken(
        restTemplate: TestRestTemplate,
        port: Int,
        username: String,
        claims: Map<String, Any> = emptyMap(),
    ): String {
        val body = mapOf("username" to username, "claims" to claims)
        val response = restTemplate.postForEntity("http://localhost:$port/token", body, AuthController.AuthResponse::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
        return response.body!!.token
    }

    private fun stompClientWithToken(): WebSocketStompClient {
        val transport = WebSocketTransport(StandardWebSocketClient())
        val stompClient = WebSocketStompClient(SockJsClient(listOf(transport)))
        stompClient.messageConverter = MappingJackson2MessageConverter()

        return stompClient
    }

    fun connectWithToken(
        stompClient: WebSocketStompClient,
        url: String,
        token: String,
        handler: StompSessionHandlerAdapter,
    ): StompSession {
        val wsHeaders = WebSocketHttpHeaders()

        val connectHeaders =
            StompHeaders().apply {
                add("Authorization", "Bearer $token") // Must be a STOMP header
            }

        val future =
            stompClient.connectAsync(
                url,
                wsHeaders,
                connectHeaders,
                handler,
            )

        return future.get(3, TimeUnit.SECONDS)
    }

    class TestStompFrameHandler(
        private val latch: CountDownLatch,
        private val messages: MutableList<String>,
    ) : StompFrameHandler {
        override fun getPayloadType(headers: StompHeaders): Type = ChatMessage::class.java

        override fun handleFrame(
            headers: StompHeaders,
            payload: Any?,
        ) {
            logger.info { "Message Received: $payload" }
            messages.add(payload.toString())
            logger.info { "Messages: $messages" }
            latch.countDown()
        }
    }

    class TestStompSessionHandler(
        private val latch: CountDownLatch,
        private val messages: MutableList<String>,
    ) : StompSessionHandlerAdapter() {
        override fun handleTransportError(
            session: StompSession?,
            exception: Throwable?,
        ) {
            logger.info { "Transport Error Received: ${exception?.message}" }
            messages.add(exception?.message.toString())
            latch.countDown()
        }
    }
}
