# ⚡ CodeForge AI — Autonomous Code Intelligence Platform

An enterprise-grade, autonomous AI software engineering platform built with **Spring Boot microservices**, **High-Dimensional RAG (4096-dim)**, **Upstash Redis Caching**, and **Parallel Multi-Agent LLM Orchestration**.

![Architecture](https://img.shields.io/badge/Architecture-Microservices-6366f1)
![Backend](https://img.shields.io/badge/Backend-Spring%20Boot%203.x%20%7C%20Java%2021-22c55e)
![AI-RAG](https://img.shields.io/badge/AI--RAG-Nvidia%20%7C%20Groq%20%7C%20Qdrant-f59e0b)
![Deployment](https://img.shields.io/badge/Deployment-Render%20%7C%20Vercel-ef4444)

---

## 🚀 Key Features

* **📦 Automated Repository Ingestion**: Clones any public GitHub repository, parses file trees, extracts metadata, and stores source code in **Supabase S3 Object Storage**.
* **🔍 High-Dimensional Vector Search (4096-dim)**: Generates 4096-dimensional code embeddings powered by **Nvidia AI**, stored and queried via **Qdrant Cloud Vector Database**.
* **⚡ High-Speed Caching**: Integrates **Upstash Redis (SSL/TLS)** with Spring `@Cacheable` for 5ms search response caching.
* **🤖 Parallel Multi-Agent Orchestration**: Executes specialized AI agents in parallel powered by **Groq (`llama-3.1-8b-instant`)**:
  * 🐞 **Bug Diagnosis Agent**: Detects logic bugs, concurrency issues, null pointer exceptions, and syntax flaws.
  * 🛡️ **Security Auditor Agent**: Scans for OWASP Top 10 vulnerabilities (XSS, SQL Injection, broken auth, secrets exposure).
  * 🎨 **Code Review Agent**: Evaluates clean code standards, Spring Boot design patterns, and modern syntax refactoring.
* **📊 RAG Evaluation & Citations**: Evaluates Context Relevance, Faithfulness, and Answer Relevance with exact line and file citations.
* **🛡️ Security & Proxy Handling**: API Gateway JWT authorization, custom CORS filters, and header encoding handling.

---

## 🏗️ System Architecture

```text
[ React / Vercel Frontend ]
             │
             ▼  HTTP / REST (JWT Auth)
    [ API Gateway : 8085 / Render ]
   ┌─────────┼────────────────────────┐
   ▼         ▼                        ▼
[ Auth ]  [ Projects ]    [ Ingestion Service : 8083 ]
(8081)     (8082)         ├── 1. JGit Repository Ingestion
                          ├── 2. Supabase S3 Object Storage
                          ├── 3. Nvidia 4096-dim Embeddings
                          ├── 4. Qdrant Cloud Vector DB
                          ├── 5. Upstash Redis Cache
                          └── 6. Groq Multi-Agent Orchestrator
```

---

## 🛠️ Technology Stack

| Layer | Technology |
| :--- | :--- |
| **Backend Microservices** | Spring Boot 3.x, Java 21, Spring WebFlux, Spring Data JPA |
| **Frontend UI** | React 18, TypeScript, Lucide Icons, Custom CSS Theme |
| **Relational Database** | Neon Serverless PostgreSQL (`neondb`) |
| **Object Storage** | Supabase S3 Object Storage (`codeforge-repos`) |
| **Vector Database** | Qdrant Cloud Vector Database (`4096-dim`) |
| **Distributed Caching** | Upstash Redis (SSL / TLS Encrypted) |
| **AI & LLM Engines** | Nvidia AI Code Embeddings, Groq (`llama-3.1-8b-instant`) |
| **Cloud Hosting** | Render (Backend Microservices), Vercel (Frontend UI) |

---

## 🌐 Production API Endpoints

### 🔐 Auth Service (`/api/auth/**`)
* `POST /api/auth/register` — Register a new user
* `POST /api/auth/login` — Authenticate user and receive JWT Bearer token

### 📦 Ingestion Service (`/api/ingest/**`)
* `POST /api/ingest/repository` — Ingest a public GitHub repository
* `GET /api/ingest/repositories` — Fetch all user ingested repositories and status (`PENDING`, `CLONING`, `PROCESSING`, `COMPLETED`, `FAILED`)

### 🔎 RAG & Vector Search (`/api/rag/**`)
* `POST /api/rag/embed/{repositoryId}` — Generate 4096-dim vector embeddings and store in Qdrant
* `POST /api/rag/search` — Perform high-precision semantic search with reranking & citations

### 🤖 Multi-Agent Orchestration (`/api/orchestrator/**`)
* `POST /api/orchestrator/analyze` — Trigger parallel Multi-Agent AI Analysis (`BUG_DIAGNOSIS`, `SECURITY`, `CODE_REVIEW`)

---

## ⚙️ Environment Configuration

Set the following environment variables on **Render** / **Local Environment**:

```env
# Database (Neon DB)
SPRING_DATASOURCE_URL=jdbc:postgresql://<neon-host>/neondb?sslmode=require
SPRING_DATASOURCE_USERNAME=<db-user>
SPRING_DATASOURCE_PASSWORD=<db-password>

# Upstash Redis
REDIS_HOST=<your-upstash-host>.upstash.io
REDIS_PORT=6379
REDIS_PASSWORD=<your-upstash-token>
REDIS_SSL=true

# Supabase S3 Storage
MINIO_URL=https://<project-ref>.storage.supabase.co/storage/v1/s3
MINIO_ACCESS_KEY=<supabase-access-key>
MINIO_SECRET_KEY=<supabase-secret-key>
MINIO_BUCKET=codeforge-repos

# Qdrant Vector DB
QDRANT_URL=https://<qdrant-instance>.cloud.qdrant.io
QDRANT_API_KEY=<qdrant-api-key>

# AI API Keys
NVIDIA_API_KEY=<nvidia-api-key>
GROQ_API_KEY=<groq-api-key>
GROQ_MODEL=llama-3.1-8b-instant

# JWT Security
JWT_SECRET=<your-super-secret-jwt-key>
```

---

## 💻 Local Development Setup

1. **Clone the repository**:
   ```bash
   git clone https://github.com/Rahul-672/codeforge-ai.git
   cd codeforge-ai
   ```

2. **Build backend modules**:
   ```bash
   mvn clean install -DskipTests=true
   ```

3. **Run microservices**:
   ```bash
   # Terminal 1: Auth Service
   cd auth-service && mvn spring-boot:run

   # Terminal 2: Ingestion Service
   cd ingestion-service && mvn spring-boot:run

   # Terminal 3: API Gateway
   cd api-gateway && mvn spring-boot:run
   ```

4. **Run React Frontend**:
   ```bash
   cd frontend
   npm install
   npm start
   ```

---

## 📝 License

Distributed under the MIT License. See `LICENSE` for more information.