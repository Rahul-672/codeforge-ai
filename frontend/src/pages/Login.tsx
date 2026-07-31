import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { login, register } from '../api';
import { Code2 } from 'lucide-react';

export default function Login() {
  const [isLogin, setIsLogin] = useState(true);
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const res = isLogin
        ? await login(email, password)
        : await register(name, email, password);
      const { token, email: userEmail } = res.data.data;
      localStorage.setItem('token', token);
      localStorage.setItem('email', userEmail);
      navigate('/repos');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Something went wrong');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{
      minHeight: '100vh', display: 'flex',
      alignItems: 'center', justifyContent: 'center',
      background: '#0f0f0f'
    }}>
      <div style={{
        width: '100%', maxWidth: '380px', padding: '0 1rem'
      }}>
        <div style={{ textAlign: 'center', marginBottom: '2rem' }}>
          <div style={{
            display: 'inline-flex', alignItems: 'center',
            gap: '8px', marginBottom: '8px'
          }}>
            <Code2 size={24} color="#6366f1" />
            <span style={{ fontSize: '20px', fontWeight: 600 }}>
              CodeForge AI
            </span>
          </div>
          <p style={{ color: '#666', fontSize: '14px' }}>
            AI-powered code intelligence platform
          </p>
        </div>

        <div style={{
          background: '#161616', border: '1px solid #222',
          borderRadius: '12px', padding: '2rem'
        }}>
          <div style={{
            display: 'flex', marginBottom: '1.5rem',
            background: '#0f0f0f', borderRadius: '8px', padding: '3px'
          }}>
            {['Login', 'Register'].map((tab) => (
              <button key={tab} onClick={() => {
                setIsLogin(tab === 'Login');
                setError('');
              }} style={{
                flex: 1, padding: '8px', border: 'none', borderRadius: '6px',
                cursor: 'pointer', fontSize: '14px', fontWeight: 500,
                background: (isLogin ? tab === 'Login' : tab === 'Register')
                  ? '#222' : 'transparent',
                color: (isLogin ? tab === 'Login' : tab === 'Register')
                  ? 'white' : '#666',
                transition: 'all 0.15s'
              }}>{tab}</button>
            ))}
          </div>

          <form onSubmit={handleSubmit}>
            {!isLogin && (
              <div style={{ marginBottom: '1rem' }}>
                <label style={{
                  display: 'block', fontSize: '13px',
                  color: '#999', marginBottom: '6px'
                }}>Name</label>
                <input
                  value={name}
                  onChange={e => setName(e.target.value)}
                  placeholder="Your name"
                  required={!isLogin}
                  style={inputStyle}
                />
              </div>
            )}

            <div style={{ marginBottom: '1rem' }}>
              <label style={{
                display: 'block', fontSize: '13px',
                color: '#999', marginBottom: '6px'
              }}>Email</label>
              <input
                type="email"
                value={email}
                onChange={e => setEmail(e.target.value)}
                placeholder="you@example.com"
                required
                style={inputStyle}
              />
            </div>

            <div style={{ marginBottom: '1.5rem' }}>
              <label style={{
                display: 'block', fontSize: '13px',
                color: '#999', marginBottom: '6px'
              }}>Password</label>
              <input
                type="password"
                value={password}
                onChange={e => setPassword(e.target.value)}
                placeholder="••••••"
                required
                style={inputStyle}
              />
            </div>

            {error && (
              <div style={{
                background: '#2a1515', border: '1px solid #5a2020',
                borderRadius: '6px', padding: '10px 12px',
                fontSize: '13px', color: '#f87171', marginBottom: '1rem'
              }}>{error}</div>
            )}

            <button type="submit" disabled={loading} style={{
              width: '100%', padding: '10px', background: '#6366f1',
              border: 'none', borderRadius: '8px', color: 'white',
              fontSize: '14px', fontWeight: 500, cursor: 'pointer',
              opacity: loading ? 0.7 : 1
            }}>
              {loading ? 'Please wait...' : isLogin ? 'Sign in' : 'Create account'}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}

const inputStyle: React.CSSProperties = {
  width: '100%', padding: '9px 12px',
  background: '#0f0f0f', border: '1px solid #2a2a2a',
  borderRadius: '7px', color: 'white', fontSize: '14px',
  outline: 'none'
};