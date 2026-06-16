# AI For Developers — Scheduling Platform

A Calendly-like scheduling application where users create event types, share booking links, and let guests book time slots based on their weekly availability.

### Hexlet tests and linter status:
[![Actions Status](https://github.com/sers88/ai-for-developers-project-387/actions/workflows/hexlet-check.yml/badge.svg)](https://github.com/sers88/ai-for-developers-project-387/actions)

---

## Features

- **User authentication** — email/password registration & login, JWT-based sessions, Google OAuth2 login
- **Weekly schedules** — define available time slots for each day of the week
- **Event types** — create bookable event types (title, duration, buffers) linked to a schedule
- **Public booking page** — shareable link where guests pick a date, select a slot, and book
- **Booking management** — hosts view/cancel bookings from the dashboard; guests cancel via email link
- **Google Calendar integration** — connect a Google Calendar to check busy slots
- **Email notifications** — booking confirmation and cancellation emails via SMTP

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 3.4, Kotlin 2.1, Java 21, Gradle 8.13 |
| Database | PostgreSQL 16 |
| Frontend | Nuxt 3.15 (SPA mode), Vue 3.5, TypeScript, Pinia, VeeValidate + Zod |
| API Design | OpenAPI 3.1 (Design-First with Redocly) |
| Auth | JWT (jjwt 0.12.6) + Google OAuth2 |
| Migrations | Flyway |
| Containerization | Docker + Docker Compose |
| E2E Testing | Playwright |

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                     Browser                          │
│  ┌───────────────────────────────────────────────┐  │
│  │  Nuxt 3 SPA (Vue 3)  :3000                    │  │
│  │  - Pinia store (auth)                         │  │
│  │  - Typed API client (openapi-fetch)           │  │
│  └──────────────────┬────────────────────────────┘  │
└─────────────────────┼───────────────────────────────┘
                      │ HTTP / REST
┌─────────────────────┼───────────────────────────────┐
│              Docker Network                          │
│  ┌──────────────────▼──────────────────┐            │
│  │  Spring Boot API  :8080             │            │
│  │  - JWT auth + Google OAuth2         │            │
│  │  - Flyway migrations (auto)         │            │
│  │  - Actuator health endpoint         │            │
│  └──────────────────┬──────────────────┘            │
│                     │                                │
│  ┌──────────────────▼──────────────────┐            │
│  │  PostgreSQL 16  :5432               │            │
│  │  - pgdata volume (persistent)       │            │
│  └─────────────────────────────────────┘            │
└──────────────────────────────────────────────────────┘
```

## Requirements

- **Docker** 24+ with Docker Compose v2
- **Git** (to clone the repository)

> No Java, Node.js, or PostgreSQL installation is needed — everything runs in containers.

## Quick Start

```bash
# 1. Clone the repository
git clone https://github.com/sers88/ai-for-developers-project-387.git
cd ai-for-developers-project-387

# 2. Copy the environment template
cp .env.example .env

# 3. Start all services
docker compose up --build
```

That's it. Three services come up:

| Service | URL | Purpose |
|---------|-----|---------|
| Frontend | http://localhost:3000 | Nuxt SPA web application |
| Backend API | http://localhost:8080 | Spring Boot REST API |
| PostgreSQL | localhost:5432 | Database (internal to Docker network) |

Flyway migrations run automatically on backend startup — no manual DB setup needed.

### Health Check

Verify the backend is up:

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP",...}
```

## Configuration

All configuration is via environment variables in `.env` (see `.env.example` for the full template).

### Essential Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `JWT_SECRET` | — | Secret key for JWT signing (min 256 bits) |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `validate` | JPA schema validation (Flyway manages schema) |
| `NUXT_PUBLIC_API_BASE` | `http://localhost:8080` | Backend API URL for the frontend |

### Google OAuth2 (optional)

Set these to enable "Sign in with Google" and Google Calendar integration:

| Variable | Description |
|----------|-------------|
| `GOOGLE_CLIENT_ID` | Google OAuth2 client ID |
| `GOOGLE_CLIENT_SECRET` | Google OAuth2 client secret |
| `GOOGLE_REDIRECT_URI` | OAuth callback URL (`http://localhost:8080/api/auth/oauth2/google/callback`) |
| `GOOGLE_CALENDAR_REDIRECT_URI` | Calendar OAuth callback URL |
| `CALENDAR_ENCRYPTION_KEY` | Encryption key for stored OAuth tokens |
| `FRONTEND_URL` | Frontend URL for redirects (`http://localhost:3000`) |

### Email / SMTP (optional)

| Variable | Description |
|----------|-------------|
| `SMTP_HOST` | SMTP server hostname |
| `SMTP_PORT` | SMTP port (default 587) |
| `SMTP_USERNAME` / `SMTP_PASSWORD` | SMTP credentials |
| `MAIL_FROM` | From-address for outgoing emails |

## Screenshots

Screenshots are available in [`docs/screenshots/`](docs/screenshots/).

## Development

### Backend (`backend/`)

```bash
./gradlew build              # ktlint + compile + test
./gradlew ktlintFormat       # auto-fix lint
./gradlew build -x test      # compile only
```

### Frontend (`frontend/`)

```bash
npm run dev                  # dev server
npm run lint                 # ESLint
npm run typecheck            # vue-tsc (run `npx nuxt prepare` first)
npm run test                 # vitest
npm run build                # production build
```

### OpenAPI (Design-First)

```bash
npm run spec:lint && npm run spec:bundle && npm run generate:api
```

### E2E Tests (Playwright)

```bash
cd frontend
npx playwright install       # install browsers (first time only)
npm run test:e2e             # run E2E tests
```

> E2E tests require both backend and frontend to be running (`docker compose up`).

## CI/CD

GitHub Actions runs on every push and PR:

- **Frontend**: ESLint → Nuxt prepare → typecheck → build
- **Backend**: ktlint → compile (tests skipped in CI)

---

## License

This project is part of the [Hexlet](https://hexlet.io) "AI for Developers" course.
