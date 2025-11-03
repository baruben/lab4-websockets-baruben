package websockets

import jakarta.websocket.HandshakeResponse
import jakarta.websocket.server.HandshakeRequest
import jakarta.websocket.server.ServerEndpointConfig

class WebSocketConfigurator : ServerEndpointConfig.Configurator() {
    companion object {
        // Inject manually from Spring context
        lateinit var tokenService: TokenService
    }

    override fun modifyHandshake(
        config: ServerEndpointConfig,
        request: HandshakeRequest,
        response: HandshakeResponse,
    ) {
        // Extract Token from Authorization Headers
        val headers = request.headers
        var token: String? = null
        val authHeaders = headers["Authorization"] ?: headers["authorization"]
        if (!authHeaders.isNullOrEmpty()) {
            val a = authHeaders[0]
            if (a.startsWith("Bearer ", ignoreCase = true)) {
                token = a.substringAfter("Bearer ").trim()
            }
        }

        if (token.isNullOrBlank() || !tokenService.isValid(token)) {
            // Mark handshake as unauthenticated
            config.userProperties["ws.authenticated"] = false
        } else {
            val user = tokenService.parseToken(token)
            // Store username & roles into userProperties so endpoint can access them
            config.userProperties["ws.username"] = user.username
            config.userProperties["ws.claims"] = user.claims
            config.userProperties["ws.authenticated"] = true
        }

        super.modifyHandshake(config, request, response)
    }
}
