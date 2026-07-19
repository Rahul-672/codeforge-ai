import { useState } from 'react';
import { ragSearch, getUserRepos } from '../api';
import { Search as SearchIcon, FileCode, Star } from 'lucide-react';
import { useEffect } from 'react';

export default function Search() {
  const [query, setQuery] = useState('');
  const [repoId, setRepoId] = useState('');
  const [repos, setRepos] = useState<any[]>([]);
  const [results, setResults] = useState<any>(null);
  const [loading, setLoading] = useState(false);
  const email = localStorage.getItem('email') || '';

  useEffect(() => {
    getUserRepos(email).then(res => {
      const completed = (res.data.data || [])
        .filter((r: any) => r.status === 'COMPLETED');
      setRepos(completed);
      if (completed.length > 0) setRepoId(completed[0].id);
    }).catch(console.error);
  }, []);

  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!query.trim() || !repoId) return;
    setLoading(true);
    try {
      const res = await ragSearch(query, repoId);
      setResults(res.data.data);
    } catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  return (
    <div style={{ maxWidth: '900px', margin: '0 auto', padding: '2rem 1rem' }}>
      <div style={{ marginBottom: '2rem' }}>
        <h1 style={{ fontSize: '20px', fontWeight: 600, marginBottom: '4px' }}>
          Semantic Search
        </h1>
        <p style={{ color: '#666', fontSize: '14px' }}>
          Ask questions about your codebase in natural language
        </p>
      </div>

      <form onSubmit={handleSearch} style={{ marginBottom: '2rem' }}>
        <div style={{ display: 'flex', gap: '10px', marginBottom: '10px' }}>
          <select
            value={repoId}
            onChange={e => setRepoId(e.target.value)}
            style={{
              padding: '10px 14px', background: '#161616',
              border: '1px solid #222', borderRadius: '8px',
              color: 'white', fontSize: '14px', outline: 'none',
              minWidth: '200px'
            }}
          >
            {repos.map(r => (
              <option key={r.id} value={r.id}>{r.name}</option>
            ))}
          </select>
        </div>

        <div style={{ display: 'flex', gap: '10px' }}>
          <input
            value={query}
            onChange={e => setQuery(e.target.value)}
            placeholder="How does the authentication work?"
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
            <SearchIcon size={16} />
            {loading ? 'Searching...' : 'Search'}
          </button>
        </div>
      </form>

      {results && (
        <div>
          {/* Evaluation scores */}
          {results.evaluation && (
            <div style={{
              display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)',
              gap: '10px', marginBottom: '1.5rem'
            }}>
              {[
                { label: 'Context Relevance',
                  val: results.evaluation.contextRelevance },
                { label: 'Faithfulness',
                  val: results.evaluation.answerFaithfulness },
                { label: 'Answer Relevance',
                  val: results.evaluation.answerRelevance },
                { label: 'Overall Score',
                  val: results.evaluation.overallScore }
              ].map(m => (
                <div key={m.label} style={{
                  background: '#161616', border: '1px solid #222',
                  borderRadius: '8px', padding: '12px'
                }}>
                  <p style={{ fontSize: '11px', color: '#555',
                    marginBottom: '4px' }}>{m.label}</p>
                  <p style={{
                    fontSize: '20px', fontWeight: 600,
                    color: m.val > 0.7 ? '#22c55e'
                      : m.val > 0.5 ? '#f59e0b' : '#ef4444'
                  }}>
                    {(m.val * 100).toFixed(0)}%
                  </p>
                </div>
              ))}
            </div>
          )}

          {/* Quality label */}
          <div style={{
            display: 'flex', alignItems: 'center', gap: '8px',
            marginBottom: '1.5rem'
          }}>
            <span style={{ fontSize: '13px', color: '#666' }}>
              Quality:
            </span>
            <span style={{
              fontSize: '12px', padding: '3px 10px', borderRadius: '4px',
              background: '#166534' + '33', color: '#22c55e'
            }}>
              {results.evaluation?.qualityLabel}
            </span>
            <span style={{ fontSize: '13px', color: '#444' }}>
              · {results.totalCandidates} candidates retrieved
              · reranked to {results.retrievedChunks?.length} chunks
            </span>
          </div>

          {/* Citations */}
          <h3 style={{
            fontSize: '12px', fontWeight: 500, color: '#999',
            marginBottom: '12px', textTransform: 'uppercase',
            letterSpacing: '0.05em'
          }}>
            Retrieved Code Chunks
          </h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {results.citations?.map((c: any) => (
              <div key={c.index} style={{
                background: '#161616', border: '1px solid #222',
                borderRadius: '10px', padding: '1rem 1.25rem'
              }}>
                <div style={{
                  display: 'flex', alignItems: 'center',
                  justifyContent: 'space-between', marginBottom: '10px'
                }}>
                  <div style={{
                    display: 'flex', alignItems: 'center', gap: '8px'
                  }}>
                    <span style={{
                      fontSize: '11px', background: '#6366f133',
                      color: '#818cf8', padding: '2px 7px',
                      borderRadius: '4px', fontWeight: 500
                    }}>[{c.index}]</span>
                    <FileCode size={14} color="#6366f1" />
                    <span style={{ fontSize: '13px', fontWeight: 500 }}>
                      {c.fileName}
                    </span>
                    {c.methodName && (
                      <span style={{ fontSize: '12px', color: '#555' }}>
                        → {c.methodName}
                      </span>
                    )}
                  </div>
                  <div style={{
                    display: 'flex', alignItems: 'center',
                    gap: '4px', color: '#f59e0b', fontSize: '12px'
                  }}>
                    <Star size={11} />
                    {(c.relevanceScore * 100).toFixed(0)}%
                  </div>
                </div>
                <pre style={{
                  background: '#0f0f0f', borderRadius: '6px',
                  padding: '10px', fontSize: '12px', color: '#a5b4fc',
                  overflow: 'auto', maxHeight: '200px',
                  fontFamily: 'monospace', lineHeight: 1.6
                }}>
                  {c.codeSnippet}
                </pre>
                <p style={{
                  fontSize: '11px', color: '#444', marginTop: '8px'
                }}>
                  {c.filePath}
                </p>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}