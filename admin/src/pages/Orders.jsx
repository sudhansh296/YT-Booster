import { useEffect, useState } from 'react';
import api, { ADMIN_BASE } from '../api';

export default function Orders() {
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);

  const load = async () => {
    setLoading(true);
    try {
      const res = await api.get(`${ADMIN_BASE}/orders`);
      setOrders(Array.isArray(res.data) ? res.data : []);
    } catch (e) { console.error(e); }
    setLoading(false);
  };

  useEffect(() => { load(); }, []);

  const updateStatus = async (id, status, note = '') => {
    try {
      await api.post(`${ADMIN_BASE}/orders/${id}/status`, { status, adminNote: note });
      load();
    } catch (e) { alert('Error: ' + e.message); }
  };

  const statusColor = (s) => {
    if (s === 'completed') return '#4CAF50';
    if (s === 'processing') return '#2196F3';
    if (s === 'failed') return '#f44336';
    return '#FFD700';
  };

  if (loading) return <div className="page"><p style={{ color: '#aaa' }}>Loading...</p></div>;

  return (
    <div className="page">
      <h2 style={{ color: '#FFD700' }}>Subscriber Orders</h2>
      <p style={{ color: '#aaa', marginBottom: 16 }}>Total: {orders.length} orders</p>
      {orders.length === 0 ? <p style={{ color: '#666' }}>No orders yet</p> : (
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead>
            <tr style={{ borderBottom: '1px solid #333' }}>
              {['Channel', 'Quantity', 'Coins', 'Status', 'Note', 'Date', 'Actions'].map(h => (
                <th key={h} style={{ padding: '10px 8px', color: '#aaa', textAlign: 'left', fontSize: 13 }}>{h}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {orders.map(o => (
              <tr key={o._id} style={{ borderBottom: '1px solid #1a1a1a' }}>
                <td style={{ padding: '10px 8px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                    {o.userId?.profilePic && <img src={o.userId.profilePic} style={{ width: 32, height: 32, borderRadius: '50%' }} alt="" onError={e => e.target.style.display='none'} />}
                    <div>
                      <div style={{ color: '#fff', fontSize: 13 }}>{o.userId?.channelName || 'Unknown'}</div>
                      <div style={{ color: '#666', fontSize: 11 }}>{o.channelUrl?.slice(0, 30)}</div>
                    </div>
                  </div>
                </td>
                <td style={{ padding: '10px 8px', color: '#fff', fontWeight: 'bold' }}>{o.quantity}</td>
                <td style={{ padding: '10px 8px', color: '#FFD700' }}>{o.coinsSpent}</td>
                <td style={{ padding: '10px 8px' }}>
                  <span style={{ color: statusColor(o.status), fontWeight: 'bold', fontSize: 12 }}>{o.status?.toUpperCase()}</span>
                </td>
                <td style={{ padding: '10px 8px', color: '#aaa', fontSize: 12, maxWidth: 150 }}>{o.adminNote || '-'}</td>
                <td style={{ padding: '10px 8px', color: '#666', fontSize: 12 }}>{new Date(o.createdAt).toLocaleDateString()}</td>
                <td style={{ padding: '10px 8px' }}>
                  <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
                    {o.status !== 'completed' && (
                      <button style={{ background: '#4CAF50', fontSize: 11, padding: '4px 8px', border: 'none', borderRadius: 4, color: '#fff', cursor: 'pointer' }}
                        onClick={() => updateStatus(o._id, 'completed', 'Manually completed')}>✓ Done</button>
                    )}
                    {o.status === 'pending' && (
                      <button style={{ background: '#f44336', fontSize: 11, padding: '4px 8px', border: 'none', borderRadius: 4, color: '#fff', cursor: 'pointer' }}
                        onClick={() => updateStatus(o._id, 'failed', 'Cancelled by admin')}>Cancel</button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}
