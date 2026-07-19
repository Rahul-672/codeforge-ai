import { useState, useEffect } from 'react';
import { orchestrateAnalysis, getUserRepos } from '../api';
import { Cpu, Bug, Shield, Code, ChevronDown,
         ChevronUp, AlertTriangle } from 'lucide-react';

export default function Analyze() {
  const [query, setQuery] = useState('');
  const [repoId, setRepoId] = useState('');
  const [repos, setRepos] = useState<any[]>([]);
  const [selectedAgents, setSelectedAgents] = useState<string[]>(['ALL']);
  const [results, setResults] = useState<any>(null);
  const [loading, setLoading] = useState(false);
  const [expanded, setExpanded] = useState<string | null>(null);
  const email = localStorage.getItem('email') || '';

  useEffect(() => {
    getUserRepos(email).then(res => {
      const completed = (res.data.data || [])
        .filter((r: any) => r.status === 'COMPLETED');
      setRepos(completed);
      if (completed.length > 0) setRepoId(completed[0].id);
    }).catch(console.error);
  }, []);

  const handleAnalyze = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!query.trim() || !repoId) return;
    setLoading(true);
    setResults(null);
    try {
      const agents = selectedAgents.includes('ALL')
        ? ['ALL'] : selectedAgents;
      const res = await orchestrateAnalysis(repoId, query, agents);
      setResults(res.data.data);
    } catch (err) { console.error(err); }
    finally { setLoading(false); }
  };

  const agentIcon = (type: string) => {
    switch (type) {
      case 'BUG_DIAGNOSIS': return <Bug size={14} color="#f87171" />;
      case 'SECURITY': return <Shield size={14} color="#fb923c" />;
      case 'CODE_REVIEW': return <Code size={14} color="#60a5fa" />;
      default: return <Cpu size={14} />;
    }
  };

  const agentColor = (type: string) => {
    switch (type) {
      case 'BUG_DIAGNOSIS': return '#f87171';
      case 'SECURITY': return '#fb923c';
      case 'CODE_REVIEW': return '#60a5fa';
      default: return '#a78bfa';
    }
  };

  const severityColor = (s: string) => {
    switch (s?.toUpperCase()) {
      case 'CRITICAL': return '#ef4444';
      case 'HIGH': return '#f97316';
      case 'MEDIUM': return '#f59e0b';
      case 'LOW': return '#22c55e';
      default: return '#666';
    }
  };

  const agentOptions = [
    { id: 'ALL', label: 'All Agents', icon: <Cpu size={13} /> },
    { id: 'BUG_DIAGNOSIS', label: 'Bug Diagnosis', icon: <Bug size={13} /> },
    { id: 'CODE_REVIEW', label: 'Code Review', icon: <Code size={13} /> },
    { id: 'SECURITY', label: 'Security', icon: <Shield size={13} /> },
  ];

  return (
    <div style={{ maxWidth: '900px', margin: '0 auto', padding: '2rem 1rem' }}>
      <div style={{ marginBottom: '2rem' }}>
        <h1 style={{ fontSize: '20px', fontWeight: 600, marginBottom: '4px' }}>
          AI Agent Analysis
        </h1>
        <p style={{ color: '#666', fontSize: '14px' }}>
          Multi-agent orchestration for comprehensive code analysis
        </p>
      </div>

      <form onSubmit={handleAnalyze} style={{ marginBottom: '2rem' }}>
        <div style={{ display: 'flex', gap: '10px', marginBottom: '10px' }}>
          <select value={repoId} onChange={e => setRepoId(e.target.value)}
            style={{
              padding: '10px 14px', background: '#161616',
              border: '1px solid #222', borderRadius: '8px',
              color: 'white', fontSize: '14px', outline: 'none',
              minWidth: '200px'
            }}>
            {repos.map(r => (
              <option key={r.id} value={r.id}>{r.name}</option>
            ))}
          </select>

          {/* Agent selector */}
          <div style={{ display: 'flex', gap: '6px' }}>
            {agentOptions.map(opt => (
              <button key={opt.id} type="button"
                onClick={() => {
                  if (opt.id === 'ALL') {
                    setSelectedAgents(['ALL']);
                  } else {
                    const without = selectedAgents
                      .filter(a => a !== 'ALL');
                    if (without.includes(opt.id)) {
                      const next = without.filter(a => a !== opt.id);
                      setSelectedAgents(next.length ? next : ['ALL']);
                    } else {
                      setSelectedAgents([...without, opt.id]);
                    }
                  }
                }}
                style={{
                  padding: '8px 12px', border: '1px solid',
                  borderRadius: '7px', cursor: 'pointer',
                  fontSize: '12px', fontWeight: 500,
                  display: 'flex', alignItems: 'center', gap: '5px',
                  background: selectedAgents.includes(opt.id)
                    ? '#6366f122' : '#161616',
                  borderColor: selectedAgents.includes(opt.id)
                    ? '#6366f1' : '#222',
                  color: selectedAgents.includes(opt.id)
                    ? '#818cf8' : '#666'
                }}>
                {opt.icon} {opt.label}
              </button>
            ))}
          </div>
        </div>

        <div style={{ display: 'flex', gap: '10px' }}>
          <textarea
            value={query}
            onChange={e => setQuery(e.target.value)}
            placeholder="Describe a bug, paste a stack trace, or ask for a code review..."
            rows={4}
            style={{
              flex: 1, padding: '10px 14px', background: '#161616',
              border: '1px solid #222', borderRadius: '8px',
              color: 'white', fontSize: '14px', outline: 'none',
              resize: 'vertical', fontFamily: 'inherit'
            }}
          />
          <button type="submit" disabled={loading} style={{
            padding: '10px 18px', background: '#6366f1', border: 'none',
            borderRadius: '8px', color: 'white', fontSize: '14px',
            fontWeight: 500, cursor: 'pointer', alignSelf: 'flex-end',
            display: 'flex', alignItems: 'center', gap: '6px',
            opacity: loading ? 0.7 : 1, minWidth: '110px',
            justifyContent: 'center'
          }}>
            <Cpu size={16} />
            {loading ? 'Analyzing...' : 'Analyze'}
          </button>
        </div>
      </form>

      {loading && (
        <div style={{
          textAlign: 'center', padding: '3rem', color: '#444'
        }}>
          <Cpu size={32} style={{
            marginBottom: '12px', color: '#6366f1',
            animation: 'spin 2s linear infinite'
          }} />
          <p style={{ fontSize: '14px' }}>
            Running agents in parallel...
          </p>
          <p style={{ fontSize: '12px', marginTop: '4px', color: '#333' }}>
            This may take 30-60 seconds
          </p>
        </div>
      )}

      {results && (
        <div>
          {/* Summary bar */}
          <div style={{
            background: '#161616', border: '1px solid #222',
            borderRadius: '10px', padding: '1rem 1.25rem',
            marginBottom: '1rem', display: 'flex',
            justifyContent: 'space-between', alignItems: 'center'
          }}>
            <div>
              <p style={{ fontSize: '13px', color: '#999',
                marginBottom: '4px' }}>
                {results.agentsExecuted?.length} agents ·{' '}
                {results.successfulAgents} succeeded ·{' '}
                {(results.totalProcessingTimeMs / 1000).toFixed(1)}s ·{' '}
                {results.parallelExecution ? 'parallel' : 'sequential'}
              </p>
              <p style={{ fontSize: '13px', color: '#ccc' }}>
                {results.overallSummary?.substring(0, 120)}...
              </p>
            </div>
            <div style={{
              display: 'flex', alignItems: 'center', gap: '6px',
              fontSize: '13px', fontWeight: 500,
              color: severityColor(results.overallSeverity)
            }}>
              <AlertTriangle size={14} />
              {results.overallSeverity}
            </div>
          </div>

          {/* Agent results */}
          <div style={{
            display: 'flex', flexDirection: 'column', gap: '8px'
          }}>
            {results.agentsExecuted?.map((agentType: string) => {
              const result = results.agentResults?.[agentType];
              if (!result) return null;
              const isOpen = expanded === agentType;

              return (
                <div key={agentType} style={{
                  background: '#161616', border: '1px solid #222',
                  borderRadius: '10px', overflow: 'hidden'
                }}>
                  <button
                    onClick={() => setExpanded(isOpen ? null : agentType)}
                    style={{
                      width: '100%', padding: '1rem 1.25rem',
                      background: 'none', border: 'none', cursor: 'pointer',
                      display: 'flex', alignItems: 'center',
                      justifyContent: 'space-between', color: 'white'
                    }}
                  >
                    <div style={{
                      display: 'flex', alignItems: 'center', gap: '10px'
                    }}>
                      {agentIcon(agentType)}
                      <span style={{
                        fontSize: '14px', fontWeight: 500,
                        color: agentColor(agentType)
                      }}>
                        {result.agentName}
                      </span>
                      {result.severity && (
                        <span style={{
                          fontSize: '11px', padding: '2px 7px',
                          borderRadius: '4px',
                          background: severityColor(result.severity) + '22',
                          color: severityColor(result.severity)
                        }}>
                          {result.severity}
                        </span>
                      )}
                      <span style={{ fontSize: '12px', color: '#444' }}>
                        {(result.processingTimeMs / 1000).toFixed(1)}s
                      </span>
                    </div>
                    {isOpen
                      ? <ChevronUp size={16} color="#666" />
                      : <ChevronDown size={16} color="#666" />}
                  </button>

                  {isOpen && (
                    <div style={{
                      padding: '0 1.25rem 1.25rem',
                      borderTop: '1px solid #1e1e1e'
                    }}>
                      {result.summary && (
                        <p style={{
                          fontSize: '13px', color: '#ccc',
                          margin: '1rem 0', lineHeight: 1.6
                        }}>
                          {result.summary}
                        </p>
                      )}

                      {result.recommendations?.length > 0 && (
                        <div style={{ marginBottom: '1rem' }}>
                          <p style={{
                            fontSize: '11px', color: '#555',
                            textTransform: 'uppercase',
                            letterSpacing: '0.05em', marginBottom: '8px'
                          }}>
                            Recommendations
                          </p>
                          {result.recommendations.map(
                            (rec: string, i: number) => (
                            <div key={i} style={{
                              fontSize: '13px', color: '#999',
                              padding: '6px 0',
                              borderBottom: '1px solid #1a1a1a',
                              lineHeight: 1.5
                            }}>
                              {rec}
                            </div>
                          ))}
                        </div>
                      )}

                      {result.citations?.length > 0 && (
                        <div>
                          <p style={{
                            fontSize: '11px', color: '#555',
                            textTransform: 'uppercase',
                            letterSpacing: '0.05em', marginBottom: '8px'
                          }}>
                            Source files
                          </p>
                          {result.citations.map((c: any) => (
                            <div key={c.index} style={{
                              fontSize: '12px', color: '#555',
                              padding: '4px 0',
                              display: 'flex', alignItems: 'center',
                              gap: '6px'
                            }}>
                              <span style={{ color: '#6366f1' }}>
                                [{c.index}]
                              </span>
                              {c.fileName}
                              <span style={{ color: '#333' }}>
                                {c.filePath}
                              </span>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  )}
                </div>
              );
            })}
          </div>

          {/* Combined recommendations */}
          {results.combinedRecommendations?.length > 0 && (
            <div style={{
              background: '#161616', border: '1px solid #2a2a1a',
              borderRadius: '10px', padding: '1rem 1.25rem',
              marginTop: '1rem'
            }}>
              <p style={{
                fontSize: '12px', color: '#f59e0b',
                textTransform: 'uppercase', letterSpacing: '0.05em',
                marginBottom: '10px'
              }}>
                Combined Recommendations
              </p>
              {results.combinedRecommendations.map(
                (rec: string, i: number) => (
                <div key={i} style={{
                  fontSize: '13px', color: '#999',
                  padding: '6px 0', borderBottom: '1px solid #1a1a1a',
                  lineHeight: 1.5
                }}>
                  {i + 1}. {rec}
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      <style>{`
        @keyframes spin { to { transform: rotate(360deg); } }
      `}</style>
    </div>
  );
}