import React, { useState } from 'react';
import ReactDOM from 'react-dom/client';
import Dashboard from './pages/Dashboard';
import Users from './pages/Users';
import Promoted from './pages/Promoted';
import CoinRequests from './pages/CoinRequests';
import Notices from './pages/Notices';
import Settings from './pages/Settings';
import Orders from './pages/Orders';
import ReferralCodes from './pages/ReferralCodes';
import SubAdmins from './pages/SubAdmins';
import Notifications from './pages/Notifications';
import Groups from './pages/Groups';
import CommunityLive from './pages/CommunityLive';
import PromoVideos from './pages/PromoVideos';
import { adminLogin } from './api';
import './styles.css';

function App() {
  const [secret, setSecret] = useState('');
  const [authed, setAuthed] = useState(!!localStorage.getItem('admin_token'));
  const [tab, setTab] = useState('dashboard');
  const [loginError, setLoginError] = useState('');
  const [loading, setLoading] = useState(false);

  const doLogin = async () => {
    if (!secret) return;
    setLoading(true); setLoginError('');
    try {
      const res = await adminLogin(secret);
      localStorage.setItem('admin_token', res.data.token);
      setAuthed(true);
    } catch (e) {
      setLoginError(e.response?.data?.error || 'Wrong password');
    } finally {
      setLoading(false);
    }
  };

  if (!authed) {
    return (
      <div className="login-wrap">
        <div className="login-card">
          <h2>Admin Login</h2>
          <input type="password" placeholder="Admin Secret" value={secret}
            onChange={e => setSecret(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && doLogin()}
            className="inp" />
          {loginError && <div style={{ color: '#ff6b6b', fontSize: 13, marginBottom: 8 }}>{loginError}</div>}
          <button className="btn btn-red" style={{ width: '100%', padding: 12 }} onClick={doLogin} disabled={loading}>
            {loading ? 'Logging in...' : 'Login'}
          </button>
        </div>
      </div>
    );
  }

  const tabs = ['dashboard', 'users', 'orders', 'promoted', 'coin-requests', 'notices', 'sub-admins', 'referral-codes', 'notifications', 'groups', 'community', 'promo-videos', 'settings'];
  const tabLabels = {
    'dashboard': 'Dashboard', 'users': 'Users', 'orders': 'Orders',
    'promoted': 'Promoted', 'coin-requests': 'Coins', 'notices': 'Notices',
    'sub-admins': '👥 Sub-Admins', 'referral-codes': '🔗 Referrals', 'notifications': '🔔 Notify',
    'groups': '💬 Groups', 'community': '🔴 Community', 'promo-videos': '🎬 Promo Videos', 'settings': 'Settings'
  };

  return (
    <div>
      <nav className="nav">
        <span className="nav-brand">YT Admin</span>
        {tabs.map(t => (
          <button key={t} onClick={() => setTab(t)} className={`nav-btn ${tab === t ? 'active' : ''}`}>
            {tabLabels[t]}
          </button>
        ))}
        <button className="nav-logout" onClick={() => { localStorage.removeItem('admin_token'); setAuthed(false); }}>Logout</button>
      </nav>
      <div>
        {tab === 'dashboard' && <Dashboard />}
        {tab === 'users' && <Users />}
        {tab === 'orders' && <Orders />}
        {tab === 'promoted' && <Promoted />}
        {tab === 'coin-requests' && <CoinRequests />}
        {tab === 'notices' && <Notices />}
        {tab === 'referral-codes' && <ReferralCodes />}
        {tab === 'sub-admins' && <SubAdmins />}
        {tab === 'notifications' && <Notifications />}
        {tab === 'groups' && <Groups />}
        {tab === 'community' && <CommunityLive />}
        {tab === 'promo-videos' && <PromoVideos />}
        {tab === 'settings' && <Settings />}
      </div>
    </div>
  );
}

ReactDOM.createRoot(document.getElementById('root')).render(<App />);
