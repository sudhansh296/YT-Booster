import React, { useEffect, useState } from 'react';
import api, { adjustCoins, toggleBan } from '../api';

export default function Users() {
  const [users, setUsers] = useState([]);
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState('all');
  const [coinInput, setCoinInput] = useState({});
  const [reason, setReason] = useState({});
  const [page, setPage] = useState(1);
  const PER_PAGE = 20;

  const load = () => {
    api.get('/ytadm1n_x9k2p7/users')
      .then(r => {
        const data = r.data;
        setUsers(Array.isArray(data) ? data : (data?.users || []));
      })
      .catch(e => console.error('Users error:', e));
  };

  useEffect(() => { load(); }, []);

  const isSuspicious = (u) => u.isSuspicious || (u.subscribersGiven > 5 && u.subscribersGiven > u.subscribersReceived * 3);

  const filtered = users
    .filter(u => u.channelName?.toLowerCase().includes(search.toLowerCase()))
    .filter(u => {
      if (filter === 'suspicious') return isSuspicious(u);
      if (filter === 'banned') return u.isBanned;
      return true;
    });

  const totalPages = Math.ceil(filtered.length / PER_PAGE);
  const paginated = filtered.slice((page - 1) * PER_PAGE, page * PER_PAGE);
  const suspiciousCount = users.filter(isSuspicious).length;

  const handleCoins = async (id) => {
    const coins = parseInt(coinInput[id] || 0);
    if (!coins) return;
    await adjustCoins(id, coins, reason[id] || 'Admin adjustment');
    load();
  };

  return (
    <div className="page">
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 16, flexWrap: 'wrap', gap: 10 }}>
        <h2 style={{ color: '#FFD700', margin: 0 }}>Users ({users.length})</h2>
        <div style={{ display: 'flex', gap: 8 }}>
          {['all', 'suspicious', 'banned'].map(f => (
            <button key={f} onClick={() => { setFilter(f); setPage(1); }}
              style={{ padding: '6px 14px', borderRadius: 6, border: 'none', cursor: 'pointer', fontSize: 13,
                background: filter === f ? (f === 'suspicious' ? '#ff6b00' : f === 'banned' ? '#f44336' : '#FFD700') : '#2a2a2a',
                color: filter === f ? '#000' : '#aaa', fontWeight: filter === f ? 'bold' : 'normal' }}>
              {f === 'suspicious' ? `⚠️ Suspicious (${suspiciousCount})` : f === 'banned' ? '🚫 Banned' : 'All'}
            </button>
          ))}
        </div>
      </div>

      <input placeholder="Search by channel name..." value={search}
        onChange={e => { setSearch(e.target.value); setPage(1); }}
        style={{ padding: 10, background: '#1a1a1a', border: '1px solid #333', borderRadius: 8, color: '#fff', marginBottom: 16, width: 300 }} />

      <div style={{ overflowX: 'auto' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead>
            <tr style={{ background: '#1a1a1a', textAlign: 'left' }}>
              {['Channel', 'Coins', 'Earned', 'Given', 'Received', 'Status', 'Joined', 'Actions'].map(h => (
                <th key={h} style={{ padding: '10px 12px', color: '#aaa', fontWeight: 'normal' }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {paginated.map(u => (
              <tr key={u._id} style={{ borderBottom: '1px solid #222', background: isSuspicious(u) ? '#1a0f00' : 'transparent' }}>
                <td style={{ padding: '10px 12px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    <img src={u.profilePic} alt="" style={{ width: 32, height: 32, borderRadius: '50%' }} onError={e => e.target.style.display='none'} />
                    <div>
                      <div>{u.channelName}</div>
                      {isSuspicious(u) && <div style={{ color: '#ff6b00', fontSize: 11 }}>⚠️ Suspicious</div>}
                    </div>
                  </div>
                </td>
                <td style={{ padding: '10px 12px', color: '#ffd700' }}>{u.coins}</td>
                <td style={{ padding: '10px 12px' }}>{u.totalEarned}</td>
                <td style={{ padding: '10px 12px', color: isSuspicious(u) ? '#ff6b00' : '#fff' }}>{u.subscribersGiven}</td>
                <td style={{ padding: '10px 12px' }}>{u.subscribersReceived}</td>
                <td style={{ padding: '10px 12px' }}>
                  <span style={{ color: u.isBanned ? '#ff4444' : '#4caf50' }}>{u.isBanned ? 'Banned' : 'Active'}</span>
                </td>
                <td style={{ padding: '10px 12px', color: '#aaa', fontSize: 12 }}>{new Date(u.createdAt).toLocaleDateString()}</td>
                <td style={{ padding: '10px 12px' }}>
                  <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                    <input type="number" placeholder="±coins" value={coinInput[u._id] || ''}
                      onChange={e => setCoinInput(p => ({ ...p, [u._id]: e.target.value }))}
                      style={{ width: 70, padding: 4, background: '#2a2a2a', border: 'none', borderRadius: 4, color: '#fff' }} />
                    <input placeholder="reason" value={reason[u._id] || ''}
                      onChange={e => setReason(p => ({ ...p, [u._id]: e.target.value }))}
                      style={{ width: 80, padding: 4, background: '#2a2a2a', border: 'none', borderRadius: 4, color: '#fff' }} />
                    <button onClick={() => handleCoins(u._id)}
                      style={{ padding: '4px 8px', background: '#ffd700', border: 'none', borderRadius: 4, cursor: 'pointer', color: '#000' }}>Set</button>
                    <button onClick={async () => { await toggleBan(u._id); load(); }}
                      style={{ padding: '4px 8px', background: u.isBanned ? '#4caf50' : '#ff4444', border: 'none', borderRadius: 4, cursor: 'pointer', color: '#fff' }}>
                      {u.isBanned ? 'Unban' : 'Ban'}
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {filtered.length === 0 && <p style={{ color: '#666', padding: 20 }}>Koi user nahi mila</p>}
      </div>

      {totalPages > 1 && (
        <div style={{ display: 'flex', gap: 8, marginTop: 16, alignItems: 'center', justifyContent: 'center' }}>
          <button onClick={() => setPage(p => Math.max(1, p-1))} disabled={page === 1}
            style={{ padding: '6px 14px', background: '#2a2a2a', border: 'none', borderRadius: 6, color: '#fff', cursor: 'pointer' }}>← Prev</button>
          <span style={{ color: '#aaa' }}>Page {page} / {totalPages} ({filtered.length} users)</span>
          <button onClick={() => setPage(p => Math.min(totalPages, p+1))} disabled={page === totalPages}
            style={{ padding: '6px 14px', background: '#2a2a2a', border: 'none', borderRadius: 6, color: '#fff', cursor: 'pointer' }}>Next →</button>
        </div>
      )}
    </div>
  );
}
