# Lab 4 WebSocket -- Project Report

## Description of Changes
### 1. Test Completion
- Completed the Eliza tests with special care with their asynchronous behaviour.
- [View Source Code] (./src/test/kotlin/websockets/ElizaServerTest.kt)

### 2. Session Management and Broadcast
- Implemented **in-memory session tracking** using `ConcurrentHashMap` and `CopyOnWriteArraySet` to manage active WebSocket sessions.
- Developed a **broadcast system** (`/broadcast` and `/secure/broadcast`) to send messages to all connected clients efficiently.
- Implemented **multicast communication** (`/secure/multicast`) to allow room-based message delivery.
- Added proper session cleanup logic on disconnection events.

### 3. WebSocket Security with Authentication
- Added JWT-based authentication for secure WebSocket handshakes using a custom `WebSocketConfigurator`.
- Validated tokens and extracted claims (username, roles, and room) to enforce authenticated and authorized communication.
- Implemented **role-based access control (RBAC)** for message handling:
    - Only authenticated users can connect.
    - Users without roles cannot send messages.
    - Admin users can perform privileged commands such as `/kick <username>` to remove other users.
  
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
- `/broadcast` – Public WebSocket broadcast server (no authentication).
- `/secure/broadcast` – Authenticated and role-protected WebSocket server.
- `/secure/multicast` – Authenticated, room-based WebSocket server.
- `/token` – REST endpoint for generating JWT tokens.

## Technical Decisions
- **JWT for Authentication:** Chosen for its stateless nature and ability to carry user claims (roles, rooms) directly within the token. This allows secure handshake validation without maintaining external session storage.
- **In-Memory Session Storage:** Used `ConcurrentHashMap` and `CopyOnWriteArraySet` for thread-safe management of connected sessions without introducing additional complexity (for example, Redis or database persistence).
- **Role-Based Access Control (RBAC):** Implemented directly in the message-handling logic to keep authorization checks lightweight and localized.
- **WebSocketConfigurator:** Used to inject authentication and user claims into each session during the handshake phase, ensuring secure and verified connections.
- **Simple Token Service:** Focused purely on JWT generation to support authenticated testing of WebSocket endpoints without unnecessary user management.
- **Environment-Based Secret Management:** Used to improve security by protecting important information (the signing-key for tokens).
- **Github Secrets for CI Integration:** Best way to integrate the environment variable in the Github Actions pipeline.

## Learning Outcomes
- Learned how to **build secure and interactive WebSocket applications** with JWT-based authentication and role validation.
- Understood the **WebSocket connection lifecycle** and how to manage sessions efficiently.
- Gained experience handling **asynchronous communication** and designing reliable integration tests for concurrent systems.
- Practiced creating **room-based multicast** and **global broadcast** communication models.
- Learned how to make **robust tests for asynchronous systems**, using `CountDownLatch` and relaxed assertions to handle timing differences.

## AI Disclosure
### AI Tools Used
- ChatGPT (OpenAI GPT-5)

### AI-Assisted Work
- Wrote portions of the `SecureBroadcastEndpoint` and `SecureMulticastEndpoint`.
- Highlighted important aspects to test the new endpoints.
- Structured this report.

**Estimated AI assistance:** around 40% of total work.

**Modifications:** I adapted, reviewed, and integrated the AI suggestions into my own code and report to ensure correctness and alignment with my assignment.

### Original Work
- Completed the **Eliza WebSocket tests** (`ElizaServerTest`).
- Designed and implemented the **JWT-based security logic**, **role enforcement**, and **broadcast/multicast mechanisms** independently.
- Manually tested the WebSocket endpoints using generated tokens via Postman.
- Implemented tests for all extra assignments.

Through this project, I gained a strong understanding of **real-time communication testing**, **secure session handling**, and **concurrent client simulation**.