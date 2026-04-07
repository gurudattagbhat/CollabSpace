# Collab Workspace

Real-time collaborative workspace built with Spring Boot, WebSocket messaging, and MySQL.

## Highlights

- Secure authentication flow with signup, login, OTP verification, and password reset
- Real-time room collaboration with synchronized notes and whiteboard
- Export support for notes (`.txt`) and whiteboard (`.png`)
- Deployment-ready config for Render + Netlify proxy

## Tech Stack

- Java 21
- Spring Boot 3
- Spring Security
- Spring Data JPA
- WebSocket (STOMP + SockJS)
- Thymeleaf, JavaScript, CSS
- MySQL

## Quick Start

### 1) Prerequisites

- Java 21+
- Maven 3.9+
- MySQL 8+

Verify tools:

```powershell
java -version
mvn -version
```

### 2) Configure environment variables

Use `.env.example` as reference for local values.

Required variables:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_MAIL_USERNAME`
- `SPRING_MAIL_PASSWORD`
- `APP_MAIL_FROM`

Optional runtime variables:

- `APP_WEBSOCKET_ALLOWED_ORIGIN_PATTERNS`
- `APP_MAIL_REQUIRE_SUCCESS`
- `APP_SECURITY_OTP_EXPIRY_MINUTES`
- `APP_SECURITY_RESET_EXPIRY_MINUTES`

### 3) Run locally

```powershell
mvn spring-boot:run
```

Open:

- `http://localhost:8080/dashboard`

## Deployment

### Render (backend)

1. Push project to GitHub.
2. In Render, create a new Blueprint deployment from this repo.
3. Render will use `render.yaml` to build and run the app.
4. Add environment variables from `RENDER_SECRETS_TEMPLATE.txt`.
5. Redeploy after saving secret values.

### Netlify (front door proxy)

1. Create a Netlify site from this repository.
2. Set `NETLIFY_BACKEND_URL` to your backend URL (for example, Render URL).
3. Deploy. `netlify.toml` writes a rewrite file that proxies requests to backend.

## Security Checklist Before GitHub Push

- Never commit `.env` with real credentials.
- Keep secrets only in local environment or cloud secret manager.
- Rotate any credential that was ever exposed in git history.
- Keep `cookies.txt` empty or ignored.
- Do not commit logs containing tokens/session data.

## Project Layout

- `src/main/java/com/mca/collab` - backend code
- `src/main/resources/templates` - Thymeleaf templates
- `src/main/resources/static` - CSS/JS/assets
- `src/main/resources/application.properties` - env-based runtime config

## Notes

- Email failures fall back to server logs for OTP/reset continuity.
- Collaboration data is synchronized in real time per room.

## License

Add your preferred license (for example MIT) before open-source release.
