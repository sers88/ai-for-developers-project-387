---
name: aifordev-issue
description: Implements a GitHub issue end-to-end for the AiForDevelopers monorepo. Use when user says "приступай к реализации {url}", "реализуй issue #{N}", "make issue #{N}", or references a github.com/sers88/ai-for-developers-project-387 URL. Handles branch creation, implementation, verification, commit, push, CI check, PR creation, and merge+close.
---

# AiForDev Issue Implementation

Full end-to-end: issue → branch → implement → verify → commit → push → CI → PR → merge+close.

## Workflow

### 1. Understand

```sh
gh issue view {N} --repo sers88/ai-for-developers-project-387 --json title,body,labels
```

### 2. Branch

```sh
git checkout main && git pull origin main && git checkout -b {label-lower}-{N}-{short-slug}
```

**Naming rule**: extract the issue labels via `gh issue view {N} --json labels`. Use the **primary feature label** in lowercase as the prefix. Skip workflow labels (`ready-for-agent`, `triaged`, `in-progress`, etc.) — pick the label that describes the feature domain.

Examples:
- Label `design-first` → `design-first-23-schedules-spec`
- Label `feature` → `feature-google-oauth`
- Label `bug` → `bug-42-fix-login-redirect`
- If only workflow workflow labels exist, derive prefix from the issue title or parent PRD tag.

Branch template: `{feature-label-lowercase}-{issue-number}-{short-slug}`

### 3. Explore

Use **task agent (`explore`)** to read backend/frontend code. Backend: check controllers, DTOs, services. Frontend: check composables, pages, stores, config.

### 4. Implement

Write/edit files. Follow conventions from `AGENTS.md`:
- **OpenAPI**: multi-file `$ref` in `api-spec/`, schemas in `components/schemas/`, paths in `paths/`
- **Frontend**: `<script setup lang="ts">`, Nuxt auto-imports, `useRuntimeConfig().public.apiBase`
- **Backend**: trailing commas, multiline lambdas `{` on new line

### 5. Verify

| Scope | Command |
|---|---|
| Frontend typecheck | `npx nuxt prepare && npm run typecheck` (workdir: `frontend/`) |
| Frontend lint | `npm run lint` (workdir: `frontend/`) |
| OpenAPI lint | `npm run spec:lint` (workdir: `frontend/`) |
| OpenAPI full chain | `npm run spec:lint && npm run spec:bundle && npm run generate:api` |
| Backend | `./gradlew ktlintFormat && ./gradlew build -x test` (workdir: `backend/`) |

**Run the relevant checks based on what files changed.**

### 6. Commit

```sh
git add {files} && git commit -m "[{LABEL}] Title in English

Description bullet points.

Closes #{N}"
```

Format: `[{LABEL}]` is the uppercase issue label (e.g. `[DESIGN-FIRST]`), title in English.

### 7. Push & CI

```sh
git push -u origin {branch}
```

Wait for CI:
```sh
Start-Sleep -Seconds 20 ; gh run list --branch {branch} --limit 1 --json status,conclusion,name
```

If `conclusion` is not `success`, re-run or inspect with `gh run view {id}`.

### 8. Create PR

```sh
gh pr create --base main --head {branch} \
  --title "[{LABEL}] Title" \
  --body "Closes #{N}

- Change 1
- Change 2"
```

Always include `Closes #{N}` at top of body. Use markdown bullet list.

### 9. Merge & Close (on user command "смержи и закрой issues")

```sh
gh pr merge {PR_NUMBER} --rebase --delete-branch
gh issue close {N} --repo sers88/ai-for-developers-project-387
```

## Key Conventions

- **no `gh pr merge` until user explicitly asks** (they say "смержи", "вмержи", "закрой")
- Generated files (`api-spec/dist/openapi.json`, `frontend/api/generated/schema.d.ts`) are committed
- Frontend CI runs: lint → nuxt prepare → typecheck → build (in that order from AGENTS.md)
- Backend CI runs lint + compile only (`-x test`)
- Always run verification commands **before** committing
