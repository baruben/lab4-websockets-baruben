package websockets

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class AuthController(
    private val tokenService: TokenService,
) {
    data class AuthRequest(
        val username: String,
        val claims: Map<String, Any> = emptyMap(),
    )

    data class AuthResponse(
        val token: String,
    )

    @PostMapping("/token")
    fun createToken(
        @RequestBody request: AuthRequest,
    ): ResponseEntity<AuthResponse> {
        val token = tokenService.generateToken(request.username, request.claims)
        return ResponseEntity.ok(AuthResponse(token))
    }
}
