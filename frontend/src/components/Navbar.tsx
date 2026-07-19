import { Link, useNavigate, useLocation } from 'react-router-dom';
import { Code2, LogOut, GitBranch, Search, Cpu } from 'lucide-react';

export default function Navbar() {
  const navigate = useNavigate();
  const location = useLocation();
  const email = localStorage.getItem('email') || '';

  const handleLogout = () => {
    localStorage.clear();
    navigate('/login');
  };

  const isActive = (path: string) =>
    location.pathname === path
      ? 'text-white border-b border-white pb-0.5'
      : 'text-gray-400 hover:text-white transition-colors';

  return (
    <nav style={{
      background: '#111',
      borderBottom: '1px solid #222',
      padding: '0 2rem',
      height: '56px',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      position: 'sticky',
      top: 0,
      zIndex: 100
    }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '2rem' }}>
        <Link to="/repos" style={{
          display: 'flex', alignItems: 'center', gap: '8px',
          textDecoration: 'none', color: 'white', fontWeight: 600,
          fontSize: '15px'
        }}>
          <Code2 size={20} color="#6366f1" />
          CodeForge AI
        </Link>

        <div style={{ display: 'flex', gap: '1.5rem' }}>
          <Link to="/repos" className={isActive('/repos')}
            style={{ textDecoration: 'none', fontSize: '14px' }}>
            <span style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
              <GitBranch size={14} /> Repositories
            </span>
          </Link>
          <Link to="/search" className={isActive('/search')}
            style={{ textDecoration: 'none', fontSize: '14px' }}>
            <span style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
              <Search size={14} /> Search
            </span>
          </Link>
          <Link to="/analyze" className={isActive('/analyze')}
            style={{ textDecoration: 'none', fontSize: '14px' }}>
            <span style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
              <Cpu size={14} /> Analyze
            </span>
          </Link>
        </div>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
        <span style={{ fontSize: '13px', color: '#666' }}>{email}</span>
        <button onClick={handleLogout} style={{
          background: 'none', border: '1px solid #333', color: '#999',
          padding: '6px 12px', borderRadius: '6px', cursor: 'pointer',
          fontSize: '13px', display: 'flex', alignItems: 'center', gap: '6px'
        }}>
          <LogOut size={13} /> Logout
        </button>
      </div>
    </nav>
  );
}