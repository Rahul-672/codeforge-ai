import axios from 'axios';

const AUTH_URL = 'http://localhost:8081';
const INGESTION_URL = 'http://localhost:8083';

const getToken = () => localStorage.getItem('token');

const authHeaders = () => ({
  headers: { Authorization: `Bearer ${getToken()}` }
});

// Auth
export const register = (name: string, email: string, password: string) =>
  axios.post(`${AUTH_URL}/api/auth/register`, { name, email, password });

export const login = (email: string, password: string) =>
  axios.post(`${AUTH_URL}/api/auth/login`, { email, password });

// Ingestion
export const ingestRepo = (url: string, userEmail: string) =>
  axios.post(`${INGESTION_URL}/api/ingest/repository`,
    { url },
    { headers: { 'X-User-Email': userEmail } }
  );

export const getRepoStatus = (id: string) =>
  axios.get(`${INGESTION_URL}/api/ingest/repository/${id}/status`);

export const getUserRepos = (userEmail: string) =>
  axios.get(`${INGESTION_URL}/api/ingest/repositories`,
    { headers: { 'X-User-Email': userEmail } }
  );

// RAG
export const embedRepo = (repositoryId: string) =>
  axios.post(`${INGESTION_URL}/api/rag/embed/${repositoryId}`);

export const ragSearch = (query: string, repositoryId: string) =>
  axios.post(`${INGESTION_URL}/api/rag/search`, { query, repositoryId });

// Orchestrator
export const orchestrateAnalysis = (
  repositoryId: string,
  query: string,
  agents?: string[]
) =>
  axios.post(`${INGESTION_URL}/api/orchestrator/analyze`, {
    repositoryId,
    query,
    agents
  });