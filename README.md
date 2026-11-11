# DevPilot — GitHub Repository RAG Assistant

DevPilot is a full-stack **Spring Boot + PostgreSQL (`pgvector`) + Spring AI** application that connects to your GitHub account via OAuth2, indexes your repositories into vector embeddings, and lets you chat with your codebase using Retrieval-Augmented Generation (RAG) with clickable source file citations.

The frontend is built with **plain HTML5, CSS3, and vanilla JavaScript** and lives directly inside Spring Boot's **`backend/src/main/resources/static/`** directory. There is no separate `client/` folder, no Node.js/npm build step, and no second server to manage.

---

## 1. Project Architecture

```text
DEVPILOT/
├── .env.example                          # Template for required environment variables
├── docker-compose.yml                    # PostgreSQL 16 + pgvector container (port 5433)
├── docker/
│   └── postgres/
│       └── init-extensions.sql           # Enables vector, hstore, and uuid-ossp extensions
└── backend/                              # Spring Boot 4 / Java 21+ application (API + Static UI)
    ├── pom.xml                           # Maven dependencies (WebMVC, Security, OAuth2, JPA, Spring AI)
    ├── mvnw / mvnw.cmd                   # Maven Wrapper scripts
    └── src/
        ├── main/
        │   ├── java/devPilot/backend/
        │   │   ├── BackendApplication.java
        │   │   ├── config/               # AppConfig, CorsConfig, CryptoConfig, SecurityConfig
        │   │   ├── controllers/          # AuthController, RepoController, ChatController
        │   │   ├── dto/                  # Java record DTOs for requests and responses
        │   │   ├── entity/               # JPA entities (User, Repository, ChatSession, ChatMessage)
        │   │   ├── exceptions/           # Custom HTTP exceptions & GlobalExceptionHandler
        │   │   ├── repository/           # Spring Data JPA repositories
        │   │   ├── security/             # GithubOAuth2UserService, AppUserPrincipal, CurrentUser
        │   │   └── services/             # UserService, RepoService, ChatService, github/, indexing/, ai/
        │   └── resources/
        │       ├── application.properties
        │       └── static/               # Plain HTML, CSS, and Vanilla JS frontend served at /
        │           ├── index.html        # Single-page HTML5 structure (login, dashboard, chat)
        │           ├── css/
        │           │   └── styles.css    # Plain CSS3 layout and responsive styling
        │           └── js/
        │               ├── config.js     # API base URL and polling interval settings
        │               ├── api.js        # Fetch API wrapper + SSE stream reader for backend DTOs
        │               ├── auth.js       # GitHub OAuth login redirect, session check, and logout
        │               └── app.js        # Main UI logic (repo dashboard, indexing poll, RAG chat)
        └── test/
            └── java/devPilot/backend/services/
                |____ai/
                |     |__CodeContextRetrieverTest.java
                |____indexing/
                |       |__CodeChunkerTest.java
                |       |__CodeFileFilter.java
                |       |__RepoIndexingServiceTest.java
                |
                └── BackendApplicationTests.java
```

### How the Frontend and Backend Connect

Spring Boot automatically serves everything in `backend/src/main/resources/static/` at the root URL **`http://localhost:8080`**:
- Both the frontend UI and backend REST endpoints (`/api/**`, `/oauth2/**`) share the exact same origin (`http://localhost:8080`).
- Because they run on the same origin, the `HttpOnly` session cookie (`DEVPILOT_SESSION`) is sent automatically on every `fetch()` request without cross-origin cookie or CORS headaches, and you only ever start one process (`./mvnw spring-boot:run`).

---

## 2. Purpose of Main Backend and Frontend Files

### Backend (`backend/src/main/java/devPilot/backend/`)

| File | Purpose |
| :--- | :--- |
| `config/SecurityConfig.java` | Configures Spring Security, GitHub OAuth2 login, session cookies (`DEVPILOT_SESSION`), logout (`/api/auth/logout`), permits static resources (`/`, `/index.html`, `/css/**`, `/js/**`), and redirects to `/?oauth=callback` on login success. |
| `config/CorsConfig.java` | Configures allowed CORS origins (`app.cors.allowed-origins`) with `allowCredentials(true)`. |
| `config/CryptoConfig.java` | Configures Spring Security `TextEncryptor` to encrypt GitHub access tokens before storing them in PostgreSQL. |
| `config/AppConfig.java` | Provides `RestClient.Builder` and the `indexingExecutor` background thread pool for async repository indexing. |
| `security/GithubOAuth2UserService.java` | Runs after GitHub OAuth callback; fetches the user's GitHub profile and access token, encrypts and saves them via `UserService`, and creates `AppUserPrincipal`. |
| `controllers/AuthController.java` | Exposes `GET /api/auth/login-url` (returns `/oauth2/authorization/github`) and `GET /api/auth/me` (returns current `UserResponse`). |
| `controllers/RepoController.java` | Exposes `GET /api/repos?refresh=true\|false`, `GET /api/repos/{id}`, `GET /api/repos/{id}/status`, and `POST /api/repos/{id}/index`. |
| `controllers/ChatController.java` | Exposes `POST /api/chat/sessions`, `GET /api/chat/sessions?repositoryId=...`, `GET /api/chat/sessions/{id}`, and `POST /api/chat/sessions/{id}/messages` (SSE streaming). |
| `services/RepoService.java` | Synchronizes user repositories from GitHub via `GithubApiClient` and persists metadata in the `repositories` table. |
| `services/indexing/IndexingService.java` | Asynchronously fetches a repository's Git tree from GitHub, filters code files (`CodeFileFilter`), splits them into token chunks (`CodeChunker`), and writes embeddings to `pgvector` (`VectorStore`). |
| `services/ChatService.java` | Manages chat sessions and orchestrates RAG: saves the user message, retrieves similar code chunks (`CodeContextRetriever`), builds prompts (`ChatPromptBuilder`), and streams OpenAI tokens over SSE (`ChatStreamHandler`). |

### Frontend (`backend/src/main/resources/static/`)

| File | Purpose |
| :--- | :--- |
| `index.html` | Single-page HTML layout with 4 clear sections: `#loading-view`, `#login-view`, `#dashboard-view`, and `#chat-view`. |
| `css/styles.css` | Clean, responsive CSS using standard CSS custom properties (variables), flexbox, and grid. |
| `js/config.js` | Defines `CONFIG.API_BASE_URL` (defaults to `""` for same-origin requests) and `CONFIG.INDEX_POLL_INTERVAL_MS`. |
| `js/api.js` | Contains `apiRequest()` (handles `fetch`, JSON parsing, `credentials: "include"`, `ApiError`) and `Api.streamChatMessage()` (parses SSE `user_message`, `token`, `assistant_message`, and `done` events). |
| `js/auth.js` | Handles OAuth redirect query parameters (`?oauth=callback`, `?error=oauth_failed`), `Auth.checkSession()`, `Auth.startGithubLogin()`, and `Auth.logout()`. |
| `js/app.js` | Wires DOM events to `Api` and `Auth`: renders the user navbar, repository cards, search/status/visibility filters, live indexing progress bar polling, chat session list, and streaming AI responses with clickable GitHub file citations. |

---

## 3. Required Software Versions & Dependencies

1. **Java Development Kit (JDK)**: **Java 21 LTS** or newer (e.g., Eclipse Temurin 21 or 25).
2. **Maven**: Included via the project's Maven Wrapper (`./mvnw` on macOS/Linux, `mvnw.cmd` on Windows), or a local Maven 3.9+ installation.
3. **Docker & Docker Compose**: Required to run PostgreSQL 16 with the `pgvector` extension (`pgvector/pgvector:pg16`).
4. **GitHub Account**: To create a GitHub OAuth App (`GITHUB_CLIENT_ID` and `GITHUB_CLIENT_SECRET`).
5. **OpenAI API Key**: Required for repository embedding (`text-embedding-3-small`) and RAG chat (`gpt-4o-mini`).

---

## 4. GitHub OAuth Setup & Callback URL Requirements

1. Go to **[GitHub Developer Settings → OAuth Apps](https://github.com/settings/developers)** and click **New OAuth App**.
2. Fill in the form with these exact URLs:
   - **Application name**: `DevPilot Local`
   - **Homepage URL**: `http://localhost:8080`
   - **Authorization callback URL**: `http://localhost:8080/login/oauth2/code/github`
3. Click **Register application**.
4. Copy the **Client ID** (`GITHUB_CLIENT_ID`) and click **Generate a new client secret** to copy your **Client Secret** (`GITHUB_CLIENT_SECRET`).

> **Important**: Spring Security's built-in OAuth2 client always receives the GitHub callback at `http://localhost:8080/login/oauth2/code/github`. After Spring Security finishes exchanging the code for a token, `SecurityConfig.java` redirects the browser to `http://localhost:8080/?oauth=callback`.

---

## 5. Environment Variables & Configuration

Before starting the backend, set the following environment variables in your terminal or IDE run configuration (see `.env.example`):

| Variable | Required? | Default in `application.properties` | Description |
| :--- | :--- | :--- | :--- |
| `GITHUB_CLIENT_ID` | **Yes** | `your-github-client-id` | Client ID from your GitHub OAuth App |
| `GITHUB_CLIENT_SECRET` | **Yes** | `your-github-client-secret` | Client Secret from your GitHub OAuth App |
| `OPENAI_API_KEY` | **Yes** (for indexing & chat) | *(empty)* | OpenAI API key used by Spring AI |
| `DB_URL` | No | `jdbc:postgresql://localhost:5433/devpilot` | JDBC URL for the PostgreSQL + pgvector database |
| `DB_USERNAME` | No | `postgres` | Database username |
| `DB_PASSWORD` | No | `postgres` | Database password |
| `TOKEN_ENCRYPTOR_PASSWORD` | No | `devpilot-local-encrypt-key-change-me` | Password used to encrypt GitHub tokens in DB |
| `TOKEN_ENCRYPTOR_SALT` | No | `deadbeecfcafebab` | **Must be exactly 16 hex characters (`0-9`, `a-f`)** |
| `FRONTEND_URL` | No | `http://localhost:8080` | Where Spring Security redirects after GitHub login |
| `CORS_ALLOWED_ORIGINS` | No | `http://localhost:8080` | Comma-separated origins allowed for CORS |

---

## 6. Database Setup (PostgreSQL + pgvector)

From the root `DEVPILOT/` directory, start the PostgreSQL + `pgvector` container using Docker Compose:

```bash
docker compose up -d
```

- This starts container `devpilot-postgres` (`pgvector/pgvector:pg16`) mapped to host port **`5433`** (`localhost:5433`).
- On first startup, `docker/postgres/init-extensions.sql` automatically runs:
  ```sql
  CREATE EXTENSION IF NOT EXISTS vector;
  CREATE EXTENSION IF NOT EXISTS hstore;
  CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
  ```
- When Spring Boot starts, Hibernate (`spring.jpa.hibernate.ddl-auto=update`) creates the `users`, `repositories`, `chat_sessions`, and `chat_messages` tables, and Spring AI (`spring.ai.vectorstore.pgvector.initialize-schema=true`) initializes the `vector_store` table.

---

## 7. Exact Commands to Start the Application

1. **Start PostgreSQL**:
   ```bash
   docker compose up -d
   ```

2. **Set your environment variables**:
   - **Linux / macOS (bash/zsh)**:
     ```bash
     export GITHUB_CLIENT_ID="your_actual_github_client_id"
     export GITHUB_CLIENT_SECRET="your_actual_github_client_secret"
     export OPENAI_API_KEY="sk-your_actual_openai_api_key"
     ```
   - **Windows (PowerShell)**:
     ```powershell
     $env:GITHUB_CLIENT_ID="your_actual_github_client_id"
     $env:GITHUB_CLIENT_SECRET="your_actual_github_client_secret"
     $env:OPENAI_API_KEY="sk-your_actual_openai_api_key"
     ```

3. **Start the Spring Boot application**:
   - **Linux / macOS**:
     ```bash
     cd backend
     ./mvnw spring-boot:run
     ```
   - **Windows**:
     ```powershell
     cd backend
     .\mvnw.cmd spring-boot:run
     ```

4. **Open DevPilot in your browser**:
   - Navigate to **[http://localhost:8080](http://localhost:8080)**

---

## 8. Backend API Endpoints Reference

| Method & Path | Controller | Description |
| :--- | :--- | :--- |
| `GET /api/auth/login-url` | `AuthController` | Public endpoint returning `{"url": "/oauth2/authorization/github"}`. |
| `GET /oauth2/authorization/github` | Spring Security | Redirects user to GitHub OAuth sign-in page. |
| `GET /api/auth/me` | `AuthController` | Returns authenticated `UserResponse` (`id`, `githubId`, `githubUsername`, `displayName`, `avatarUrl`) or `401`. |
| `POST /api/auth/logout` | Spring Security | Invalidates HTTP session, clears `DEVPILOT_SESSION` cookie, and returns `204 No Content`. |
| `GET /api/repos?refresh=true\|false` | `RepoController` | Lists repositories (`refresh=true` syncs from GitHub API; `refresh=false` reads from PostgreSQL). |
| `GET /api/repos/{id}` | `RepoController` | Returns a single `RepositoryResponse` owned by the current user. |
| `GET /api/repos/{id}/status` | `RepoController` | Returns `IndexStatusResponse` (`indexStatus`, `filesTotal`, `filesProcessed`, `chunkCount`, `indexedAt`, `errorMessage`). |
| `POST /api/repos/{id}/index` | `RepoController` | Marks repo as `INDEXING` and starts async chunking + OpenAI embedding into `pgvector`. |
| `GET /api/chat/sessions?repositoryId={id}` | `ChatController` | Lists `ChatSessionResponse` records for the repository. |
| `POST /api/chat/sessions` | `ChatController` | Creates a new chat session (`{"repositoryId": "...", "title": "..."}`). Requires repo status `READY`. |
| `GET /api/chat/sessions/{id}` | `ChatController` | Returns chronological `ChatMessageResponse` list (including `citations`) for the session. |
| `POST /api/chat/sessions/{id}/messages` | `ChatController` | Streams AI answer via Server-Sent Events (`user_message`, `token`, `assistant_message`, `done`). |

---

## 9. Troubleshooting Common Startup Issues

- **`401 Unauthorized` or OAuth redirect error**:
  - Make sure you open the app at **`http://localhost:8080`** (not `http://127.0.0.1:8080`) so the domain matches your GitHub OAuth callback URL (`http://localhost:8080/login/oauth2/code/github`) and the `DEVPILOT_SESSION` cookie is sent properly.
- **Database connection refused (`localhost:5433`)**:
  - Ensure Docker Desktop / Docker Engine is running and run `docker compose up -d` from the project root. Note that `docker-compose.yml` maps PostgreSQL to host port **`5433`** (not `5432`) to avoid conflicting with any existing local Postgres installation.
- **`app.token-encryptor-salt must be exactly 16 hexadecimal characters`**:
  - `CryptoConfig` validates that `TOKEN_ENCRYPTOR_SALT` is 16 hex characters (`0-9`, `a-f`). Keep the default `deadbeecfcafebab` or generate another 16-char hex string. If you change the password or salt later, previously stored encrypted GitHub tokens in the `users` table cannot be decrypted until you log in with GitHub again.
- **Indexing fails immediately (`FAILED` status)**:
  - Check the error message displayed on the repository card or chat view. Almost always, this means `OPENAI_API_KEY` is missing, invalid, or out of quota (Spring AI calls OpenAI's `text-embedding-3-small` model to embed code chunks).

---

## 10. Summary of Migration Changes & Bug Fixes

1. **Frontend Migrated to `backend/src/main/resources/static/`**:
   - Removed the entire Next.js/React/TypeScript `client/` folder and all frontend build tooling.
   - Created the plain HTML5, CSS3, and vanilla JavaScript frontend directly inside `backend/src/main/resources/static/` (`index.html`, `css/styles.css`, `js/config.js`, `js/api.js`, `js/auth.js`, `js/app.js`) so Spring Boot serves it automatically at `http://localhost:8080`.
2. **Backend Fixes & Improvements (Minimal & Non-Breaking)**:
   - **`SecurityConfig.java`**: Updated OAuth2 success/failure handlers to redirect to `/?oauth=callback` and `/?error=oauth_failed` and explicitly permitted `/`, `/index.html`, `/css/**`, and `/js/**`.
   - **`RepoService.java`**: Fixed a bug where `repo.setOwner(parts[0])` ran before checking if `full_name` was missing, which could set `owner` to `Map.toString()` and overflow the `VARCHAR(100)` column before the fallback `ownerMap.get("login")` check could run.
   - **`CodeChunker.java`**: Fixed a property key mismatch where `application.properties` defined `app.indexing.max-chunk-size=800` while `CodeChunker` only looked up `app.indexing.chunk-size`. It now reads `${app.indexing.max-chunk-size:${app.indexing.chunk-size:800}}`.
   - **`GlobalExceptionHandler.java`**: Updated exception handlers to return structured JSON (`{"message": "..."}`) instead of raw strings so `fetch()` error handling receives valid JSON across all HTTP error statuses.
   - **`RagSettings.java` & `CorsConfig.java`**: Fixed a broken Javadoc package reference and removed an unused import; removed the unused duplicate `devPilot.backend.exceptions.MethodArgumentNotValidException` class.
   - **`application.properties` & `pom.xml`**: Replaced hardcoded GitHub client secret fallback with a safe placeholder, updated default `FRONTEND_URL` to `http://localhost:8080`, set `<java.version>21</java.version>` for compatibility with Java 21 LTS+ (including JDK 21 and JDK 25), and removed the leftover `hs_err_pid24892.log` crash dump.

---

## 11. Verification & Test Results

- **Passed Checks**:
  - **JavaScript Syntax Verification**: Ran `node --check` on `js/config.js`, `js/api.js`, `js/auth.js`, and `js/app.js` inside `backend/src/main/resources/static/` — 0 syntax errors.
  - **DOM & API Contract Verification**: Verified all 60 `document.getElementById(...)` references in `js/app.js` match IDs in `index.html`, and all frontend API calls match the exact controller routes and Java record DTO fields in `devPilot.backend.controllers` and `devPilot.backend.dto`.
  - **Java Source & Import Verification**: Verified all 48 Java source and test files for valid package declarations, file paths, balanced braces, and internal imports.
  - **Secrets Audit**: Confirmed no real API keys, tokens, or client secrets remain in `application.properties`, `.env.example`, or frontend files.
- **Environment Limitations (Not Run in Sandbox)**:
  - **Maven Build & Spring Boot Runtime (`./mvnw test` / `./mvnw spring-boot:run`)**: The sandbox VM has no outbound internet access, no pre-populated `~/.m2/repository` cache, no Docker daemon, and OpenJDK 17 installed (whereas Spring Boot 4.1.1 / Spring AI 2.0.1 requires Java 21+ and Maven Central access to download dependencies). Run `./mvnw test` and `./mvnw spring-boot:run` on your local machine with JDK 21+ and Docker running.
  - **Live GitHub OAuth & OpenAI RAG Streaming**: Requires live internet access, valid `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET`, and a valid `OPENAI_API_KEY`.
