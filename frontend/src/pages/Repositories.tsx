import { useState, useEffect } from 'react';
import { ingestRepo, getRepoStatus, getUserRepos, embedRepo } from '../api';
import { GitBranch, Plus, RefreshCw, Cpu, CheckCircle,
         Clock, XCircle, Loader } from 'lucide-react';

export default function Repositories() {
  const [url, setUrl] = useState('');
  const [repos, setRepos] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [embedding, setEmbedding] = useState<string | null>(null);
  const email = localStorage.getItem('email') || '';

  useEffect(() => { fetchRepos(); }, []);

  const fetchRepos = async () => {
    try {
      const res = await getUserRepos(email);
      setRepos(res.data.data || []);
    } catch (err) { console.error(err); }
  };

  const handleIngest = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!url.trim()) return;
    setLoading(true);
    try {
      await ingestRepo(url.trim(), email);
      setUrl('');
      setTimeout(fetchRepos, 2000);
    } catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  const handleEmbed = async (repoId: string) => {
    setEmbedding(repoId);
    try {
      await embedRepo(repoId);
      alert('Embedding started! This takes 3-6 minutes.');
    } catch (err) { console.error(err); }
    finally { setEmbedding(null); }
  };

  const statusIcon = (status: string) => {
    switch (status) {
      case 'COMPLETED': return <CheckCircle size={14} color="#22c55e" />;
      case 'FAILED': return <XCircle size={14} color="#ef4444" />;
      case 'PROCESSING':
      case 'CLONING': return <Loader size={14} color="#f59e0b"
        style={{ animation: 'spin 1s linear infinite' }} />;
      default: return <Clock size={14} color="#666" />;
    }
  };

  const statusColor = (status: string) => {
    switch (status) {
      case 'COMPLETED': return '#166534';
      case 'FAILED': return '#7f1d1d';
      default: return '#78350f';
    }
  };

  return (
    <div style={{ maxWidth: '900px', margin: '0 auto', padding: '2rem 1rem' }}>
      <div style={{ marginBottom: '2rem' }}>
        <h1 style={{ fontSize: '20px', fontWeight: 600, marginBottom: '4px' }}>
          Repositories
        </h1>
        <p style={{ color: '#666', fontSize: '14px' }}>
          Ingest GitHub repositories for AI analysis
        </p>
      </div>

      <form onSubmit={handleIngest} style={{
        display: 'flex', gap: '10px', marginBottom: '2rem'
      }}>
        <input
          value={url}
          onChange={e => setUrl(e.target.value)}
          placeholder="https://github.com/username/repository"
          style={{
            flex: 1, padding: '10px 14px', background: '#161616',
            border: '1px solid #222', borderRadius: '8px',
            color: 'white', fontSize: '14px', outline: 'none'
          }}
        />
        <button type="submit" disabled={loading} style={{
          padding: '10px 18px', background: '#6366f1', border: 'none',
          borderRadius: '8px', color: 'white', fontSize: '14px',
          fontWeight: 500, cursor: 'pointer', display: 'flex',
          alignItems: 'center', gap: '6px', opacity: loading ? 0.7 : 1
        }}>
          <Plus size={16} />
          {loading ? 'Ingesting...' : 'Ingest'}
        </button>
        <button type="button" onClick={fetchRepos} style={{
          padding: '10px', background: '#161616', border: '1px solid #222',
          borderRadius: '8px', color: '#999', cursor: 'pointer'
        }}>
          <RefreshCw size={16} />
        </button>
      </form>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
        {repos.length === 0 ? (
          <div style={{
            textAlign: 'center', padding: '3rem',
            color: '#444', border: '1px dashed #222', borderRadius: '12px'
          }}>
            <GitBranch size={32} style={{ marginBottom: '12px', opacity: 0.3 }} />
            <p style={{ fontSize: '14px' }}>No repositories yet</p>
            <p style={{ fontSize: '13px', marginTop: '4px' }}>
              Paste a GitHub URL above to get started
            </p>
          </div>
        ) : repos.map(repo => (
          <div key={repo.id} style={{
            background: '#161616', border: '1px solid #222',
            borderRadius: '10px', padding: '1rem 1.25rem',
            display: 'flex', alignItems: 'center',
            justifyContent: 'space-between'
          }}>
            <div>
              <div style={{
                display: 'flex', alignItems: 'center',
                gap: '8px', marginBottom: '4px'
              }}>
                <GitBranch size={14} color="#6366f1" />
                <span style={{ fontSize: '14px', fontWeight: 500 }}>
                  {repo.name}
                </span>
                <span style={{
                  fontSize: '11px', padding: '2px 8px', borderRadius: '4px',
                  background: statusColor(repo.status) + '33',
                  color: repo.status === 'COMPLETED' ? '#22c55e'
                    : repo.status === 'FAILED' ? '#ef4444' : '#f59e0b',
                  display: 'flex', alignItems: 'center', gap: '4px'
                }}>
                  {statusIcon(repo.status)} {repo.status}
                </span>
              </div>
              <p style={{ fontSize: '12px', color: '#555' }}>
                {repo.url} · {repo.totalFiles} files
              </p>
            </div>

            {repo.status === 'COMPLETED' && (
              <button
                onClick={() => handleEmbed(repo.id)}
                disabled={embedding === repo.id}
                style={{
                  padding: '7px 14px', background: 'transparent',
                  border: '1px solid #333', borderRadius: '7px',
                  color: '#999', fontSize: '13px', cursor: 'pointer',
                  display: 'flex', alignItems: 'center', gap: '6px'
                }}
              >
                <Cpu size={13} />
                {embedding === repo.id ? 'Starting...' : 'Embed'}
              </button>
            )}
          </div>
        ))}
      </div>

      <style>{`
        @keyframes spin { to { transform: rotate(360deg); } }
      `}</style>
    </div>
  );
}