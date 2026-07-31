import axios from 'axios';

const BASE_URL = process.env.REACT_APP_API_URL || 'https://codeforge-gatewayy.onrender.com';

const getToken = () => localStorage.getItem('token');

const authHeaders = () => ({
  headers: { Authorization: `Bearer ${getToken()}` }
});

// Auth
export const register = (name: string, email: string, password: string) =>
  axios.post(`${BASE_URL}/api/auth/register`, { name, email, password });

export const login = (email: string, password: string) =>
  axios.post(`${BASE_URL}/api/auth/login`, { email, password });

// Ingestion
export const ingestRepo = (url: string, userEmail: string) =>
  axios.post(`${BASE_URL}/api/ingest/repository`,
    { url },
    { headers: { Authorization: `Bearer ${getToken()}`, 'X-User-Email': userEmail } }
  );

export const getRepoStatus = (id: string) =>
  axios.get(`${BASE_URL}/api/ingest/repository/${id}/status`, authHeaders());

export const getUserRepos = (userEmail: string) =>
  axios.get(`${BASE_URL}/api/ingest/repositories`,
    { headers: { Authorization: `Bearer ${getToken()}`, 'X-User-Email': userEmail } }
  );

// RAG
export const embedRepo = (repositoryId: string) =>
  axios.post(`${BASE_URL}/api/rag/embed/${repositoryId}`, {}, authHeaders());

export const ragSearch = (query: string, repositoryId: string) =>
  axios.post(`${BASE_URL}/api/rag/search`, { query, repositoryId }, authHeaders());

// Orchestrator
export const orchestrateAnalysis = (
  repositoryId: string,
  query: string,
  agents?: string[]
) =>
  axios.post(`${BASE_URL}/api/orchestrator/analyze`, {
    repositoryId,
    query,
    agents
  }, authHeaders());