# Lab 4 WebSocket -- Project Report

## Description of Changes
### 1. Test Completion
- Completed the Eliza tests with special care with their asynchronous behaviour.
- [View Source Code] (./src/test/kotlin/websockets/ElizaServerTest.kt)

### 2. STOMP Protocol Support
- Enabled STOMP endpoints with `@EnableWebSocketMessageBroker`.
- Configured application and broker prefixes (`/app`, `/topic`, `/queue`).
- Configured `MappingJackson2MessageConverter` for JSON payloads.
- Implemented controllers using `@MessageMapping` for application-level commands and `SimpMessagingTemplate` for broadcasting and user-directed messages.
- Implemented STOMP error handling and support for `StompCommand.ERROR`.
- Built a STOMP-capable test client using `WebSocketStompClient`, `StompSessionHandlerAdapter`, and `StompFrameHandler`.

### 3. STOMP Client for Tests
Created a lightweight STOMP client used by integration tests:

- `WebSocketStompClient` + `SockJsClient(WebSocketTransport(StandardWebSocketClient()))`.
- `MappingJackson2MessageConverter` for automatic JSON → POJO conversion.
- `StompSessionHandlerAdapter` to manage connection lifecycle and intercept error/transport events.
- `StompFrameHandler` implementations to deserialize `ChatMessage` payloads and capture `ERROR` frames.
- Tests coordinate asynchronous flows with `CountDownLatch` and explicit ordering (connect → subscribe → send).

This client supports:
- subscribing to `/topic/*`,
- subscribing to `/user/queue/*`,
- sending to `/app/{room}/send`,
- intercepting STOMP `ERROR` frames sent by the server.

### 4. Session Management & Broadcast
- Tracked sessions with `SimpUserRegistry`.
- Listened to STOMP events (`SessionConnectedEvent`, `SessionSubscribeEvent`, `SessionDisconnectEvent`, `SessionUnsubscribeEvent`) to maintain session state and subscriptions.
- Implemented broadcast endpoints (`/topic/*`), first and then turned them into multicast/room-based delivery (`/topic/{room}`).
- Implemented user-targeted messaging via `convertAndSendToUser(username, "/queue/...")`.

This design supports efficient in-process broadcasting without external dependencies.

### 3. WebSocket Security with JWT Authentication
Secured STOMP handshakes and message handling with JWT-based authentication.

- Implemented handshake/token validation in an `InboundChannel` interceptor (`JwtChannelInterceptor`) for STOMP CONNECT frames.
- Extracted token from STOMP CONNECT headers (`Authorization: Bearer <token>`), validated it, and injected a `Principal` (with username and roles) into the STOMP session via `StompHeaderAccessor`.
- Implemented a `StompErrorSender` that sends STOMP `ERROR` frames and forces a disconnect for invalid or unauthorized clients.
- Added a simple `/token` REST endpoint to generate JWT tokens for test clients (secret loaded from environment).

**Role-based access control (RBAC)**
- Only authenticated users may connect.
- Role checks are applied inside controllers and command handlers.
- `ADMIN` role can execute privileged commands like `/subscribers`; normal users are blocked and receive error messages via `/user/queue/errors` or STOMP `ERROR` frames.
  
**Token Service**
- Added a simple REST endpoint for generating JWT tokens for testing clients.
- This service does not include user registration or login logic, since the main goal was to focus on WebSocket security and session handling.

**Secrets Management**
- Loaded the JWT secret key dynamically from an environment variable instead of being stored in source control
```application.properties
jwt.secret=${JWT_SECRET}
jwt.tokenExpiration=3600000
```
- The JWT_SECRET variable is defined in GitHub Secrets and securely injected into the CI environment via the env: block in the ci.yml pipeline configuration.
>##### Note on Secrets Management
>During development the secret key was hardcoded for simplicity and pushed to the repository by mistake.
> For that reason, in the next push the key was changed for a new one securely kept as previously described.
>
### Endpoints Added
- `/topic/{room}` - Secure endpoint to subscribe and receive messages sent to {room}. 
- `/app/{room}/send` – Secure endpoint to send messages to broadcast to all {room} subscribers.
- `/user/queue/command`, `/user/queue/errors` - Secure endpoints to subscribe and receive user-only messages from the server.
- `POST /token` – REST endpoint for generating JWT tokens for the Secure endpoints.

## Technical Decisions
- **STOMP over raw WebSockets:** Chosen for routing, structured messaging, and server-side simplicity.
- **STOMP ERROR frames:** Provide deterministic failure handling, unlike TCP-level close codes.
- **JWT for Authentication:** Chosen for its stateless nature and ability to carry user claims (roles, rooms) directly within the token. This allows secure handshake validation without maintaining external session storage.
- **Role-Based Access Control (RBAC):** Implemented directly in the message-handling logic to keep authorization checks lightweight and localized.
- **Simple Token Service:** Focused purely on JWT generation to support authenticated testing of WebSocket endpoints without unnecessary user management.
- **Environment-Based Secret Management:** Used to improve security by protecting important information (the signing-key for tokens).
- **Github Secrets for CI Integration:** Best way to integrate the environment variable in the Github Actions pipeline.
- **Async tests with CountDownLatch:** Essential for verifying message-order and ensuring subscriptions are active before sends.

## Learning Outcomes
- Learned how to implement a fully functional STOMP messaging system.
- Understood secure handshakes using JWT and integrating claims into STOMP sessions.
- Learned how to track sessions, users, and subscriptions in real time.
- Practiced designing admin commands and error-handling paths for interactive systems. 
- Learned how to make **robust tests for asynchronous systems**, using `CountDownLatch` and relaxed assertions to handle timing differences.

## AI Disclosure
### AI Tools Used
- ChatGPT (OpenAI GPT-5)

### AI-Assisted Work
- Wrote portions of the `ChatEndpoint`.
- Highlighted important aspects to test the new endpoints.
- Structured this report.

**Estimated AI assistance:** around 40% of total work.

**Modifications:** I adapted, reviewed, and integrated the AI suggestions into my own code and report to ensure correctness and alignment with my assignment.

### Original Work
- Completed the **Eliza WebSocket tests** (`ElizaServerTest`).
- Designed and implemented the **JWT-based security logic**, **role enforcement**, and **broadcast/multicast mechanisms** independently.
- Manually tested the STOMP endpoints using generated tokens via a simple html page and js client.
- Implemented tests for all extra assignments.
