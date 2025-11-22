package websockets

import io.jsonwebtoken.Jwts
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Base64
import java.util.Date
import javax.crypto.spec.SecretKeySpec

@Service
class TokenService(
    @param:Value("\${jwt.secret}") private val secret: String,
    @param:Value("\${jwt.tokenExpiration}") private val tokenExpiration: Long,
) {
    private val signingKey: SecretKeySpec
        get() {
            val keyBytes: ByteArray = Base64.getDecoder().decode(secret)
            return SecretKeySpec(keyBytes, 0, keyBytes.size, "HmacSHA256")
        }

    fun generateToken(
        username: String,
        claims: Map<String, Any> = emptyMap(),
    ): String {
        val now = Date(System.currentTimeMillis())
        val expiration = Date(now.time + tokenExpiration)
        val builder =
            Jwts
                .builder()
                .subject(username)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(signingKey)

        claims.forEach { (key, value) ->
            builder.claim(key, value)
        }

        return builder.compact()
    }

    fun extractUsername(token: String): String {
        val payload =
            Jwts
                .parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .payload

        return payload.subject
    }

    fun extractRoles(token: String): List<String> {
        val claims =
            Jwts
                .parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .payload

        return when (val rolesClaim = claims["roles"]) {
            is List<*> -> rolesClaim.filterIsInstance<String>()
            is String -> listOf(rolesClaim)
            else -> emptyList()
        }
    }

    fun isValid(token: String): Boolean =
        try {
            extractUsername(token)
            true
        } catch (ex: Exception) {
            false
        }
}
