# CodeForge AI

An autonomous AI software engineering platform using Spring Boot microservices, 
RAG pipeline with reranking & evaluation, and specialized AI agents.

## Tech Stack

- **Backend:** Spring Boot 4.1.0, Java 21
- **AI/RAG:** Ollama (nomic-embed-text), Qdrant vector DB, Reranking
- **Storage:** PostgreSQL, MinIO (S3-compatible)
- **Security:** JWT, Spring Security, API Gateway
- **Infrastructure:** Docker Compose

## Architecture
API Gateway (8080)
├── Auth Service (8081)
├── Project Manager (8082)
└── Ingestion Service (8083)
├── Repository Ingestion (JGit)
├── RAG Pipeline (Chunk → Embed → Search)
├── Reranker
├── Evaluation
└── Citations

## Setup

### Prerequisites
- Java 21
- Maven 3.9+
- Docker Desktop
- Ollama

### Infrastructure
```bash
cd docker
docker compose up -d
```

### Configuration
Copy example config files and fill in your values:
```bash
cp auth-service/src/main/resources/application.yml.example \
   auth-service/src/main/resources/application.yml

cp ingestion-service/src/main/resources/application.yml.example \
   ingestion-service/src/main/resources/application.yml

cp api-gateway/src/main/resources/application.yml.example \
   api-gateway/src/main/resources/application.yml
```

### Ollama Models
```bash
ollama pull nomic-embed-text
ollama pull qwen2.5:1.5b
```

### Build & Run
```bash
mvn clean install -DskipTests=true

# Run each service in separate terminal
cd auth-service && mvn spring-boot:run
cd api-gateway && mvn spring-boot:run
cd ingestion-service && mvn spring-boot:run
```

## API Endpoints

### Auth
- `POST /api/auth/register` — Register new user
- `POST /api/auth/login` — Login, get JWT token

### Ingestion
- `POST /api/ingest/repository` — Ingest a GitHub repo
- `GET /api/ingest/repository/{id}/status` — Check status

### RAG
- `POST /api/rag/embed/{repositoryId}` — Generate embeddings
- `POST /api/rag/search` — Semantic search with reranking

## Features

- **Repository Ingestion** — Clone any GitHub repo, scan files, store in MinIO
- **RAG Pipeline** — Chunk code at function boundaries, embed with nomic-embed-text
- **Reranking** — Multi-signal reranker improves retrieval precision
- **Citations** — Every answer includes source file + code snippet
- **Evaluation** — Measures context relevance, faithfulness, answer relevance
- **Guardrails** — Blocks prompt injection and off-topic queries