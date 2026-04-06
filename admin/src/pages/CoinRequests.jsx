import { useEffect, useState } from 'react';
import api, { ADMIN_BASE } from '../api';

export default function CoinRequests() {
  const [requests, setRequests] = useState([]);
  const [loading, setLoading] = useState(true);

  const load = async () => {
    setLoading(true);
    try {
      const res = await api.get(`${ADMIN_BASE}/coin-requests`);
      setRequests(Array.isArray(res.data) ? res.data : []);
    } catch (e) { console.error(e); }
    setLoading(false);
  };

  useEffect(() => { load(); }, []);

  const approve = async (id) => { await api.post(`${ADMIN_BASE}/coin-requests/${id}/approve`, {}); load(); };
  const reject = async (id) => { await api.post(`${ADMIN_BASE}/coin-requests/${id}/reject`, {}); load(); };

  const pending = requests.filter(r => r.status === 'pending');
  const done = requests.filter(r => r.status !== 'pending');

  return (
    <div style={{ padding: 24, color: '#fff' }}>
      <h2 style={{ color: '#FFD700' }}>Coin Purchase Requests</h2>

      <h3 style={{ color: '#ff4444' }}>Pending ({pending.length})</h3>
      {loading ? <p style={{ color: '#aaa' }}>Loading...</p> : pending.length === 0
        ? <p style={{ color: '#888' }}>No pending requests</p>
        : (
          <table style={{ width: '100%', borderCollapse: 'collapse', marginBottom: 32 }}>
            <thead>
              <tr style={{ background: '#1a1a1a' }}>
                {['Channel', 'Coins', 'Note', 'Date', 'Action'].map(h => (
                  <th key={h} style={{ padding: '10px 12px', textAlign: 'left', color: '#aaa', fontWeight: 'normal', fontSize: 13 }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {pending.map(r => (
                <tr key={r._id} style={{ borderBottom: '1px solid #222' }}>
                  <td style={{ padding: '10px 12px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      {r.userId?.profilePic && <img src={r.userId.profilePic} style={{ width: 32, height: 32, borderRadius: '50%' }} alt="" onError={e => e.target.style.display='none'} />}
                      {r.userId?.channelName || 'Unknown'}
                    </div>
                  </td>
                  <td style={{ padding: '10px 12px', color: '#FFD700', fontWeight: 'bold' }}>{r.coins}</td>
                  <td style={{ padding: '10px 12px', color: '#aaa' }}>{r.note || '-'}</td>
                  <td style={{ padding: '10px 12px', color: '#888', fontSize: 12 }}>{new Date(r.createdAt).toLocaleString()}</td>
                  <td style={{ padding: '10px 12px' }}>
                    <button onClick={() => approve(r._id)} style={{ padding: '6px 14px', border: 'none', borderRadius: 6, color: '#fff', cursor: 'pointer', background: '#4CAF50', marginRight: 8 }}>Approve</button>
                    <button onClick={() => reject(r._id)} style={{ padding: '6px 14px', border: 'none', borderRadius: 6, color: '#fff', cursor: 'pointer', background: '#f44336' }}>Reject</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}

      <h3 style={{ color: '#888' }}>History ({done.length})</h3>
      <table style={{ width: '100%', borderCollapse: 'collapse' }}>
        <thead>
          <tr style={{ background: '#1a1a1a' }}>
            {['Channel', 'Coins', 'Status', 'Date'].map(h => (
              <th key={h} style={{ padding: '10px 12px', textAlign: 'left', color: '#aaa', fontWeight: 'normal', fontSize: 13 }}>{h}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {done.map(r => (
            <tr key={r._id} style={{ borderBottom: '1px solid #222' }}>
              <td style={{ padding: '10px 12px' }}>{r.userId?.channelName || 'Unknown'}</td>
              <td style={{ padding: '10px 12px', color: '#FFD700' }}>{r.coins}</td>
              <td style={{ padding: '10px 12px', color: r.status === 'approved' ? '#4CAF50' : '#f44336' }}>{r.status}</td>
              <td style={{ padding: '10px 12px', color: '#888', fontSize: 12 }}>{new Date(r.createdAt).toLocaleString()}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
