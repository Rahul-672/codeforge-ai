# Ingestion Service — Repository Clone Test Report

**Service:** `ingestion-service` (CodeForge AI)
**Scope:** End-to-end test of the repository *clone → scan → store* pipeline
**Date:** 2026-07-05
**Result:** ✅ Pipeline works. 3 issues found and fixed (2 bugs + 1 security), then re-verified.

---

## 1. Executive Summary

The ingestion service was tested by driving the real HTTP API against live infrastructure
(PostgreSQL, MinIO) and cloning an actual GitHub repository. The core flow —
**clone a git repo, scan its files, upload them to object storage, and record metadata in the
database** — works correctly and completed successfully (340 files ingested).

While verifying the artifacts, three defects were discovered and fixed:

| # | Type | Issue | Status |
|---|------|-------|--------|
| 1 | Bug (blocker) | Packaged jar crashes at startup on Windows (`Asia/Calcutta` timezone rejected by PostgreSQL) | ✅ Fixed & verified |
| 2 | Bug (data) | MinIO/S3 object keys used `\` on Windows → flat keys, no folder hierarchy | ✅ Fixed & verified |
| 3 | Security | Live GitHub Personal Access Token hardcoded in `application.yml` | ✅ Externalized (⚠ token must be revoked) |

---

## 2. What the Service Does

`ingestion-service` is a Spring Boot microservice that ingests a source-code repository into
CodeForge's storage layer so downstream services (analysis, embeddings, search) can use it.

**Flow:** accept a repo URL → clone it with JGit (shallow) → walk the working tree → filter out
noise (binaries, `.git`, `node_modules`, …) → upload each file to MinIO → persist file metadata
in PostgreSQL → track status (`PENDING → CLONING → PROCESSING → COMPLETED/FAILED`).

### API surface

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/api/ingest/repository` | Start ingestion (body: `{url, branch, description}`; header `X-User-Email`) |
| `GET`  | `/api/ingest/repository/{id}/status` | Poll ingestion status |
| `GET`  | `/api/ingest/repositories` | List a user's repositories |
| `GET`  | `/actuator/health` | Liveness / readiness / DB health |

Cloning runs **asynchronously** (`@Async`) — the POST returns immediately with `PENDING`, and the
caller polls the status endpoint.

---

## 3. Architecture

```mermaid
flowchart TB
    Client([Client / curl])

    subgraph SVC["ingestion-service : 8083 (Spring Boot 4.1)"]
        Ctrl["IngestionController<br/>REST endpoints"]
        Svc["IngestionService<br/>@Async orchestration"]
        Minio["MinioService<br/>object upload"]
        RepoRepo[("CodeRepositoryRepository")]
        FileRepo[("CodeFileRepository")]
    end

    subgraph INFRA["Infrastructure (Docker)"]
        PG[("PostgreSQL 16<br/>:5433<br/>repositories, code_files")]
        MO[("MinIO<br/>:9000<br/>bucket: codeforge-repos")]
    end

    GH["GitHub<br/>(git over HTTPS)"]

    Client -->|POST /api/ingest/repository| Ctrl
    Client -->|GET  /status| Ctrl
    Ctrl --> Svc
    Svc -->|JGit clone --depth 1| GH
    Svc --> Minio
    Minio --> MO
    Svc --> RepoRepo --> PG
    Svc --> FileRepo --> PG
```

### Key components

| Component | File | Responsibility |
|-----------|------|----------------|
| `IngestionController` | `controller/IngestionController.java` | HTTP endpoints, wraps responses in `ApiResponse` |
| `IngestionService` | `service/IngestionService.java` | Clone (JGit), file walk/filter, orchestration, status |
| `MinioService` | `service/MinioService.java` | Bucket bootstrap + object upload |
| `CodeRepository` | `entity/CodeRepository.java` | Repo record + `IngestionStatus` enum |
| `CodeFile` | `entity/CodeFile.java` | Per-file metadata (path, language, size, minio key) |

---

## 4. Working Diagram (End-to-End Sequence)

```mermaid
sequenceDiagram
    participant C as Client
    participant API as IngestionController
    participant S as IngestionService (@Async)
    participant G as GitHub (JGit)
    participant M as MinIO
    participant DB as PostgreSQL

    C->>API: POST /api/ingest/repository {url, branch}
    API->>S: startIngestion(request, userEmail)
    S->>DB: save repository (status=PENDING)
    API-->>C: 200 {repositoryId, status: PENDING}

    Note over S: async thread continues
    S->>DB: status = CLONING
    S->>G: git clone --depth 1 (branch, optional token)
    G-->>S: working tree on local disk
    S->>DB: status = PROCESSING

    loop each file (skip .git / binaries / build dirs)
        S->>M: uploadFile(key = repoId/relativePath)
        S->>S: build CodeFile metadata
    end
    S->>DB: saveAll(code_files)
    S->>DB: status = COMPLETED, totalFiles / processedFiles

    C->>API: GET /repository/{id}/status
    API->>DB: findById
    API-->>C: {status: COMPLETED, totalFiles: 340}
```

---

## 5. How the Problem Was Approached

1. **Read before running.** Mapped the code path first — controller → service → JGit clone →
   file walk → MinIO → JPA — and identified the external dependencies (PostgreSQL, MinIO, GitHub).
2. **Confirmed the environment.** Verified Java 23, Maven, Git, and that the Docker infra
   (Postgres `:5433`, MinIO `:9000`) was already running.
3. **Clarified the target.** Confirmed the private-repo question: a token only clones repos its own
   account can access. The configured token (`Rahul-672`, scope `repo`) had **0 private repos**, so
   the public path was tested against `Rahul-672/google-it-automation` (branch `master`).
4. **Drove the real API, not a mock.** `POST`ed the clone request, polled status to a terminal
   state, then **independently verified every side effect**: the clone on disk (`.git` + branch),
   the MinIO objects, and the PostgreSQL rows. This is what surfaced the bugs a status check alone
   would have missed.
5. **Fixed, rebuilt, re-verified.** Applied the fixes, rebuilt the jar, and re-ran the full flow —
   crucially starting the jar **without** the timezone workaround to prove the fix was real.

---

## 6. Test Execution & Results

**Target repo:** `https://github.com/Rahul-672/google-it-automation.git` (branch `master`, public)

### Initial run (with `-Duser.timezone` workaround to get past bug #1)

| Check | Expected | Actual | Result |
|-------|----------|--------|--------|
| POST response | `PENDING` + id | `PENDING`, id `571c7543…` | ✅ |
| Clone on disk | `.git` present, branch `master` | `.git` present, `HEAD → refs/heads/master` | ✅ |
| Files on disk | — | 344 (excl. `.git`) | ✅ |
| Files recorded | disk minus skipped | 340 (4 images skipped) | ✅ |
| MinIO objects | 340 | 340 | ✅ |
| `code_files` rows | 340 | 340 | ✅ |
| Final status | `COMPLETED` | `COMPLETED`, `errorMessage=null` | ✅ |

Language breakdown recorded: Markdown 193, Python 59, Unknown 58, Shell 26, HTML 3, JSON 1.

### Post-fix verification run (rebuilt jar, **no** timezone arg, **no** token)

| Check | Result |
|-------|--------|
| Jar starts standalone (no launch args) | ✅ `Started IngestionServiceApplication`, DB `UP` |
| Public clone without a token | ✅ `COMPLETED` |
| MinIO keys use `/` (folder hierarchy) | ✅ `…/c1_python-crash-course/1_hello-python/resources.md` |
| `code_files.minio_path` uses `/` | ✅ forward slashes |

---

## 7. Bugs Found & Fixes

### Bug #1 — Packaged jar crashes at startup (timezone) 🔴 Blocker

**Symptom:** `java -jar …` exits during boot:
```
FATAL: invalid value for parameter "TimeZone": "Asia/Calcutta"
org.hibernate.HibernateException: Unable to determine Dialect without JDBC metadata
```

**Root cause:** On connect, the PostgreSQL JDBC driver sends the JVM's default timezone to the
server. On this Windows host it resolves to the legacy Olson alias **`Asia/Calcutta`**, which
PostgreSQL 16 rejects. The existing workaround (`-Duser.timezone=Asia/Kolkata`) lived **only** in
the `spring-boot-maven-plugin` config, so it applied to `mvn spring-boot:run` but **not** to the
built jar / Docker image — i.e. the deployable artifact was broken.

**Fix:** Pin the timezone in `main()` before the datasource initializes, so the artifact is
self-contained (`IngestionServiceApplication.java`):
```java
TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
SpringApplication.run(IngestionServiceApplication.class, args);
```

**Verified:** rebuilt jar now boots with **no** launch args; DB health `UP`.

---

### Bug #2 — MinIO object keys use `\` on Windows 🟠 Data correctness

**Symptom:** Object keys / `code_files.minio_path` were stored as
`…/c1_python-crash-course\1_hello-python\video-quiz.md` — a single flat key containing literal
backslashes instead of a nested path.

**Root cause:** `repoPath.relativize(file).toString()` returns OS-specific separators (`\` on
Windows). S3/MinIO keys use `/` to express hierarchy, so every file became a flat key. Object
browsers show no folders, and any downstream code that splits keys on `/` breaks. (On Linux this
happens to work; on Windows it does not — a latent cross-platform bug.)

**Fix:** Normalize separators once, covering both the DB `filePath` and the MinIO key
(`IngestionService.java`):
```java
String relativePath = repoPath.relativize(file).toString().replace('\\', '/');
```

**Verified:** re-run produced proper nested keys and forward-slash `minio_path` values.

---

### Bug #3 — Hardcoded GitHub token 🔴 Security

**Symptom:** A live classic PAT was committed in `application.yml`:
```yaml
github:
  token: ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```
Confirmed live via GitHub API: account **`Rahul-672`**, scope **`repo`** (full private read/write).

**Fix:** Externalized to an environment variable; empty default keeps public clones working
(`application.yml`):
```yaml
github:
  token: ${GITHUB_TOKEN:}
```

**⚠ Action still required (manual, cannot be automated):** the exposed token must be
**revoked** at <https://github.com/settings/tokens> and a replacement supplied via the
`GITHUB_TOKEN` environment variable. Treat the old value as compromised — it was in source control.

---

## 8. How to Run / Reproduce

```bash
# 1. Infra (from repo root)
docker compose -f docker/docker-compose.yml up -d      # postgres:5433, minio:9000

# 2. Build
cd ingestion-service
./mvnw clean package -DskipTests

# 3. Run (token optional; only needed for private repos)
export GITHUB_TOKEN=ghp_your_new_token        # optional
java -jar target/ingestion-service-1.0.0-SNAPSHOT.jar

# 4. Trigger a clone
curl -X POST http://localhost:8083/api/ingest/repository \
  -H "Content-Type: application/json" \
  -H "X-User-Email: you@example.com" \
  -d '{"url":"https://github.com/Rahul-672/google-it-automation.git","branch":"master"}'

# 5. Poll status
curl http://localhost:8083/api/ingest/repository/<id>/status
```

---

## 9. Notes & Further Recommendations

- **Branch handling quirk (not fixed — by design here):** the clone only calls `setBranch()` when
  the branch is neither `main` nor `master`. It works because JGit defaults to the repo's default
  branch, but it means an explicit `main`/`master` request is ignored rather than enforced. Fine for
  now; revisit if non-default default-branches must be pinned.
- **Async error visibility:** clone/upload failures are caught and written to
  `repositories.error_message` (status `FAILED`) — good. Consider surfacing partial upload failures
  (currently a failed single-file upload is logged as a warning and silently skipped).
- **Cleanup:** cloned working trees under the OS temp dir are not deleted after upload; consider
  removing them once files are in MinIO to reclaim disk.
- **Config hygiene:** other credentials in `application.yml` (DB password, MinIO keys) are also
  literals — consider externalizing them the same way as the GitHub token for non-local envs.

---

## 10. Files Changed

| File | Change |
|------|--------|
| `src/main/java/com/codeforge/ingestion/IngestionServiceApplication.java` | Pin JVM timezone in `main()` (bug #1) |
| `src/main/java/com/codeforge/ingestion/service/IngestionService.java` | Normalize path separators to `/` (bug #2) |
| `src/main/resources/application.yml` | Externalize GitHub token to `${GITHUB_TOKEN:}` (bug #3) |
