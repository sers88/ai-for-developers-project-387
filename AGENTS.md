# AGENTS.md

## Architecture

Monorepo: **backend** (Spring Boot 3.4, Kotlin 2.1, Gradle 8.13, Java 21) + **frontend** (Nuxt 4, Vue 3.5, Node 22, SPA mode `ssr: false`, Nuxt UI v4 + Tailwind CSS v4). PostgreSQL 16 via Docker Compose.

## Commands

### Backend (`backend/`)
```sh
./gradlew build                  # ktlint + compile + test
./gradlew test                   # tests only
./gradlew ktlintCheck            # lint only
./gradlew ktlintFormat           # auto-fix lint (run after writing Kotlin)
./gradlew build -x test          # compile only (CI uses this)
```

### Frontend (`frontend/`)
```sh
npm run dev                      # dev server
npm run lint                     # ESLint
npx nuxt prepare                 # generate .nuxt types → needed before typecheck
npm run typecheck                # vue-tsc
npm run test                     # vitest
npm run build                    # production build
```

## Critical gotchas

### Git Commits
- **All commits must follow Conventional Commits format** — `<type>: <subject>` where type is one of: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `chore`, `build`, `ci`, `revert`.
- Examples: `feat: add OAuth2 authentication`, `fix: resolve CORS issues`, `docs: update AGENTS.md`.
- Subject should be lowercase, no trailing period, max 50 chars.
- Body (optional) should explain what and why, not how. Wrap at 72 chars.

### Backend
- **ktlint is strict**: trailing commas required, multiline lambdas need `{` on new line. Always run `ktlintFormat` after writing Kotlin code, then `build`.
- **Tests use `@TestPropertySource`**, not `application-test.yml`. The profile-based config was unreliable — inline properties are used instead. H2 in PostgreSQL mode, Flyway disabled.
- **`kotlin.test.*` needs `testImplementation("org.jetbrains.kotlin:kotlin-test")`** in `build.gradle.kts` — it's not included by default.
- **`allOpen` plugin** is required for JPA entities (`kotlin("plugin.jpa")` + kotlin("plugin.spring")).
- **JWT library**: `io.jsonwebtoken:jjwt-api/impl/jackson:0.12.6` — api is `implementation`, impl+jackson are `runtimeOnly` to avoid leaking.

### Frontend
- **`.vue` SFCs must use `<script setup lang="ts">`** — bare `<script setup>` causes vue-tsc errors.
- **`npx nuxt prepare` before `npm run typecheck`** — generates `.nuxt/tsconfig.json` with auto-import paths.
- **ESLint is strict**: no unused imports, no `any` type, no self-closing on void HTML elements (`<input />` → `<input >`).
- **VeeValidate `<Form>`/`<Field>` components don't render in vitest** → test validation logic directly with `useForm()` composable instead.
- **Vitest config** needs `@vitejs/plugin-vue` and `environment: "happy-dom"`.
- **`@vee-validate/zod`** must be installed alongside `zod@3` (zod@4 is incompatible).

### Nuxt UI v4
- **`U*` components are auto-imported** — no import needed for `UButton`, `UInput`, `UCard`, `UForm`, `UDropdownMenu`, `UApp`, etc.
- **Icons**: use `<UIcon name="i-lucide-..." />` (Iconify Lucide set, bundled via `@nuxt/icon`).
- **Colors**: use semantic aliases (`primary`, `neutral`, `error`, `success`, etc.) on the `color` prop — NOT raw Tailwind palette names. Configured in `app.config.ts` (`ui.colors.primary = "green"`).
- **Design tokens**: `bg-default`, `bg-muted`, `text-highlighted`, `text-muted`, `border-default` etc. are Nuxt UI semantic aliases usable as Tailwind classes.
- **`UForm` integrates with `vee-validate + zod`** — use it with `:state` + `:schema` instead of manual `<Field>` wrappers.
- **Layouts**: `layouts/default.vue` (sidebar + topbar for auth zone), `layouts/auth.vue` (centered card), `layouts/public.vue` (minimal skeleton). Assign via `definePageMeta({ layout: "..." })`.
- **`<UApp>` must wrap everything in `app.vue`** — required for Toast/Tooltip/overlays.
- **Dark mode**: `@nuxtjs/color-mode` is auto-registered; toggle with `useColorMode().preference`. Persists between sessions.
- **Fonts**: configured via `--font-sans` in `@theme` block of `assets/css/main.css`; loaded automatically by `@nuxt/fonts`.
- **e2e selectors**: use `data-testid` attributes + Playwright `getByTestId()` — do NOT rely on CSS classes/ids.

### CI
- **Backend CI runs lint + compile only** — tests are skipped with `-x test`.
- **Frontend CI** runs lint → `nuxt prepare` → typecheck → build. No vitest step.
- **Order matters**: lint → prepare → typecheck → build.

## Environment
- `.env` (gitignored) holds Docker Compose variables.
- `.env.example` is the template — update both when adding vars.
- Backend env vars: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_JPA_HIBERNATE_DDL_AUTO`, `JWT_SECRET`.
- Google OAuth vars: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI`, `FRONTEND_URL`.
- Frontend env var: `NUXT_PUBLIC_API_BASE`.

## Google OAuth
- **Flow**: user clicks "Sign in with Google" → frontend redirects to `GET /api/auth/oauth2/google` → backend redirects to Google → Google redirects to `GET /api/auth/oauth2/google/callback?code=...` → backend exchanges code, creates/finds user, issues JWT → redirects to `{FRONTEND_URL}/auth/callback?accessToken=...&refreshToken=...&email=...`
- **User entity**: `password_hash` is nullable (OAuth users have no password). `google_id` (unique), `name`, `avatar_url` are optional fields.
- **Linking**: if a user exists by email (from password registration), Google OAuth links the `google_id` and updates `name`/`avatar_url`. If user exists by `google_id`, logs in directly.
- **`@EnableConfigurationProperties(GoogleOAuth2Properties::class)`** is on `AiForDevApplication` — properties prefix: `app.oauth2.google`.
- **Tests**: `OAuthIntegrationTest` mocks `OAuth2Service` via `@MockitoBean`. Integration tests require `app.oauth2.google.*` properties in `@TestPropertySource`.

## Design First (OpenAPI)

### Workflow

Process for new features — **spec first, code second**:

1. **Spec first** — write/update OpenAPI spec in `api-spec/paths/<resource>.yaml` before writing any code. Add reusable schemas to `api-spec/components/schemas/`.
2. **Lint and Bundle** — `npm run spec:lint && npm run spec:bundle` validates the spec with Redocly and bundles multi-file spec into a single `api-spec/dist/openapi.json`.
3. **Generate client** — `npm run generate:api` generates TypeScript types from the bundled spec into `frontend/api/generated/schema.d.ts`.
4. **Backend** — implement controllers matching the spec. Add contract tests to verify the implementation conforms to the OpenAPI spec.
5. **Frontend** — use the typed `apiClient` from `frontend/api/client.ts` instead of raw `$fetch()`. The generated types ensure type safety across API calls.
6. **CI** — all steps run in CI pipeline; PRs with spec/code mismatch are rejected.

**Full chain after editing spec**: `npm run spec:lint && npm run spec:bundle && npm run generate:api`

### API Spec Structure

```
api-spec/
├── openapi.yaml          # Info, servers, security, tags
├── paths/*.yaml          # One file per resource
├── components/schemas/   # Reusable schemas
├── dist/openapi.json     # Generated bundle (committed for CI stability)
├── redocly.yaml          # Linter config
└── codegen/              # Generator config
```

### Config

`api-spec/redocly.yaml` extends `recommended` with `info-license`, `operation-4xx-response`, `no-server-example.com` off.

### Scripts

All scripts run from `frontend/`:

| Script | Command | Description |
|--------|---------|-------------|
| Lint | `npm run spec:lint` | Validate OpenAPI spec with Redocly |
| Bundle | `npm run spec:bundle` | Bundle multi-file spec → `api-spec/dist/openapi.json` |
| Generate | `npm run generate:api` | Generate TypeScript types → `frontend/api/generated/schema.d.ts` |

### Generated files

`api-spec/dist/openapi.json` and `frontend/api/generated/schema.d.ts` are committed to git for CI stability.

## Auto-imports (Nuxt)
- `composables/use*.ts` → available without import in `.vue` files
- `stores/*.ts` → Pinia stores auto-imported via `@pinia/nuxt`
- `middleware/*.ts` → auto-discovered by Nuxt router
