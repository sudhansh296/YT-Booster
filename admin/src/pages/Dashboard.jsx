import { useEffect, useState } from 'react';
import { getStats } from '../api';
import api, { ADMIN_BASE } from '../api';

export default function Dashboard() {
  const [stats, setStats] = useState(null);
  const [error, setError] = useState('');
  const [subadminCodes, setSubadminCodes] = useState([]);

  useEffect(() => {
    getStats()
      .then(r => setStats(r.data))
      .catch(e => {
        if (e.response?.status === 403) {
          localStorage.removeItem('admin_token');
          window.location.reload();
        } else {
          setError('Stats load nahi hua. Server check karo.');
        }
      });
    api.get(`${ADMIN_BASE}/admin-codes`).then(r => {
      const codes = Array.isArray(r.data) ? r.data : [];
      setSubadminCodes(codes.filter(c => c.role === 'sub_admin'));
    }).catch(() => {});
  }, []);

  if (error) return <div className="page"><p style={{ color: '#ff6b6b' }}>{error}</p></div>;
  if (!stats) return <div className="page"><p style={{ color: '#888' }}>Loading...</p></div>;

  if (!stats) return <div className="page"><p style={{ color: '#888' }}>Loading...</p></div>;

  const mainCards = [
    { label: 'Total Users', value: stats.totalUsers, color: '#FF0000', icon: '👥' },
    { label: 'Total Coins', value: stats.totalCoins?.toLocaleString(), color: '#FFD700', icon: '🪙' },
    { label: 'In Queue', value: stats.activeUsers, color: '#4CAF50', icon: '⏳' },
    { label: 'Banned', value: stats.bannedUsers, color: '#f44336', icon: '🚫' },
    { label: 'Total Referrals', value: stats.totalReferrals, color: '#29B6F6', icon: '🎁' },
    { label: 'Coins Given', value: stats.totalCoinsGiven?.toLocaleString(), color: '#FF9800', icon: '💰' },
  ];

  const maxSignup = Math.max(...(stats.chart?.signups || [1]), 1);
  const maxCoins = Math.max(...(stats.chart?.coins || [1]), 1);

  return (
    <div className="page">
      <h2 style={{ color: '#FFD700', marginBottom: 24 }}>📊 Dashboard</h2>

      {/* Main Stats */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))', gap: 14, marginBottom: 24 }}>
        {mainCards.map(c => (
          <div key={c.label} style={{ background: '#1a1a1a', borderRadius: 12, padding: '18px 14px', textAlign: 'center', border: `1px solid ${c.color}22` }}>
            <div style={{ fontSize: 26 }}>{c.icon}</div>
            <div style={{ color: c.color, fontWeight: 700, fontSize: 24, margin: '6px 0 4px' }}>{c.value ?? '—'}</div>
            <div style={{ color: '#666', fontSize: 12 }}>{c.label}</div>
          </div>
        ))}
      </div>

      {/* User Growth Section - alag card */}
      <div style={{ background: '#1a1a1a', borderRadius: 12, padding: 20, marginBottom: 24, border: '1px solid #FF000022' }}>
        <div style={{ color: '#FF0000', fontWeight: 700, fontSize: 15, marginBottom: 16 }}>👤 User Growth</div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: 12 }}>
          <GrowthCard label="Aaj Joined" value={stats.dailyJoined} color="#4CAF50" sub="Today" />
          <GrowthCard label="Is Hafte" value={stats.weeklyJoined} color="#29B6F6" sub="Last 7 days" />
          <GrowthCard label="Is Mahine" value={stats.monthlyJoined} color="#FF9800" sub="This month" />
          <GrowthCard label="Total Users" value={stats.totalUsers} color="#FF0000" sub="All time" />
        </div>
      </div>

      {/* Charts */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20 }}>
        <ChartCard title="📈 Daily Signups (7 days)" days={stats.chart?.days} values={stats.chart?.signups} max={maxSignup} color="#FF0000" />
        <ChartCard title="🪙 Daily Coins Given (7 days)" days={stats.chart?.days} values={stats.chart?.coins} max={maxCoins} color="#FFD700" />
      </div>

      {/* Sub-Admin Summary */}
      {subadminCodes.length > 0 && (
        <div style={{ background: '#1a1a1a', borderRadius: 12, padding: 20, marginTop: 24, border: '1px solid #29B6F622' }}>
          <div style={{ color: '#29B6F6', fontWeight: 700, fontSize: 15, marginBottom: 16 }}>👥 Sub-Admin Performance</div>
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead>
                <tr style={{ textAlign: 'left' }}>
                  {['Sub-Admin', 'Clicks', 'Joined', 'Coins Given', 'Status'].map(h => (
                    <th key={h} style={{ padding: '8px 12px', color: '#555', fontWeight: 'normal', fontSize: 12 }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {subadminCodes.map(c => (
                  <tr key={c._id} style={{ borderTop: '1px solid #222' }}>
                    <td style={{ padding: '10px 12px' }}>
                      <div style={{ color: '#fff', fontWeight: 600 }}>{c.label}</div>
                      <div style={{ color: '#29B6F6', fontSize: 11 }}>{c.code}</div>
                    </td>
                    <td style={{ padding: '10px 12px', color: '#aaa' }}>{c.totalClicks || 0}</td>
                    <td style={{ padding: '10px 12px', color: '#4CAF50', fontWeight: 600 }}>{c.totalJoined || 0}</td>
                    <td style={{ padding: '10px 12px', color: '#FFD700' }}>{c.totalCoinsGiven || 0}</td>
                    <td style={{ padding: '10px 12px' }}>
                      <span style={{ color: c.isActive ? '#4CAF50' : '#ff4444', fontSize: 12 }}>{c.isActive ? '● Active' : '● Inactive'}</span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}

function GrowthCard({ label, value, color, sub }) {
  return (
    <div style={{ background: '#111', borderRadius: 10, padding: '16px 12px', textAlign: 'center', border: `1px solid ${color}33` }}>
      <div style={{ color, fontWeight: 700, fontSize: 32, lineHeight: 1 }}>{value ?? 0}</div>
      <div style={{ color: '#fff', fontSize: 13, marginTop: 6, fontWeight: 600 }}>{label}</div>
      <div style={{ color: '#555', fontSize: 11, marginTop: 2 }}>{sub}</div>
    </div>
  );
}

function ChartCard({ title, days = [], values = [], max, color }) {
  return (
    <div style={{ background: '#1a1a1a', borderRadius: 12, padding: 20 }}>
      <div style={{ color: '#aaa', fontWeight: 600, marginBottom: 16, fontSize: 14 }}>{title}</div>
      <div style={{ display: 'flex', alignItems: 'flex-end', gap: 8, height: 120 }}>
        {days.map((day, i) => {
          const pct = max > 0 ? (values[i] / max) * 100 : 0;
          return (
            <div key={day} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4 }}>
              <div style={{ fontSize: 10, color: '#aaa' }}>{values[i]}</div>
              <div style={{ width: '100%', background: '#333', borderRadius: 4, height: 90, display: 'flex', alignItems: 'flex-end' }}>
                <div style={{ width: '100%', background: color, borderRadius: 4, height: `${Math.max(pct, 2)}%`, transition: 'height 0.5s ease', opacity: 0.85 }} />
              </div>
              <div style={{ fontSize: 9, color: '#555' }}>{day?.slice(5)}</div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
