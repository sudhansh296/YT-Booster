import React, { useEffect, useState } from 'react';
import axios from 'axios';

const BASE = import.meta.env.VITE_API_URL || 'https://api.picrypto.in';
const headers = () => ({ 'x-admin-secret': localStorage.getItem('admin_token') });

const stars = (n) => '★'.repeat(n) + '☆'.repeat(5 - n);
const ratingColor = (r) => r >= 4 ? '#4CAF50' : r === 3 ? '#FFB300' : '#FF5252';

export default function Reviews() {
  const [data, setData] = useState({ reviews: [], total: 0, avg: 0, dist: {} });
  const [filter, setFilter] = useState(0); // 0 = all
  const [loading, setLoading] = useState(true);

  const load = async () => {
    setLoading(true);
    try {
      const res = await axios.get(`${BASE}/review/all`, { headers: headers() });
      setData(res.data);
    } catch (e) { console.error(e); }
    setLoading(false);
  };

  useEffect(() => { load(); }, []);

  const del = async (id) => {
    if (!confirm('Delete this review?')) return;
    await axios.delete(`${BASE}/review/${id}`, { headers: headers() });
    load();
  };

  const filtered = filter === 0 ? data.reviews : data.reviews.filter(r => r.rating === filter);

  return (
    <div style={{ padding: 24, maxWidth: 900, margin: '0 auto' }}>
      <h2 style={{ color: '#fff', marginBottom: 20 }}>⭐ App Reviews</h2>

      {/* Stats */}
      <div style={{ display: 'flex', gap: 16, marginBottom: 24, flexWrap: 'wrap' }}>
        <div style={{ background: '#1a1a2e', borderRadius: 12, padding: '16px 24px', minWidth: 140 }}>
          <div style={{ fontSize: 36, fontWeight: 'bold', color: '#FFD700' }}>{data.avg}</div>
          <div style={{ color: '#FFD700', fontSize: 20 }}>{stars(Math.round(data.avg))}</div>
          <div style={{ color: '#888', fontSize: 13 }}>{data.total} reviews</div>
        </div>
        <div style={{ background: '#1a1a2e', borderRadius: 12, padding: '16px 24px', flex: 1, minWidth: 200 }}>
          {[5,4,3,2,1].map(s => (
            <div key={s} style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
              <span style={{ color: '#FFD700', width: 20 }}>{s}★</span>
              <div style={{ flex: 1, background: '#333', borderRadius: 4, height: 8 }}>
                <div style={{ width: `${data.total ? ((data.dist[s] || 0) / data.total * 100) : 0}%`, background: ratingColor(s), height: '100%', borderRadius: 4, transition: 'width 0.3s' }} />
              </div>
              <span style={{ color: '#888', fontSize: 12, width: 24 }}>{data.dist[s] || 0}</span>
            </div>
          ))}
        </div>
      </div>

      {/* Filter */}
      <div style={{ display: 'flex', gap: 8, marginBottom: 16, flexWrap: 'wrap' }}>
        {[0,5,4,3,2,1].map(s => (
          <button key={s} onClick={() => setFilter(s)}
            style={{ padding: '6px 14px', borderRadius: 20, border: 'none', cursor: 'pointer', fontSize: 13,
              background: filter === s ? '#E53935' : '#1a1a2e', color: filter === s ? '#fff' : '#aaa' }}>
            {s === 0 ? 'All' : `${s}★`}
          </button>
        ))}
      </div>

      {loading ? <div style={{ color: '#888' }}>Loading...</div> : (
        filtered.length === 0 ? <div style={{ color: '#888' }}>Koi review nahi</div> :
        filtered.map(r => (
          <div key={r._id} style={{ background: '#1a1a2e', borderRadius: 12, padding: 16, marginBottom: 12, borderLeft: `4px solid ${ratingColor(r.rating)}` }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 6 }}>
                  <span style={{ color: '#FFD700', fontSize: 18 }}>{stars(r.rating)}</span>
                  <span style={{ color: ratingColor(r.rating), fontWeight: 'bold', fontSize: 14 }}>{r.rating}/5</span>
                  {!r.isRead && <span style={{ background: '#E53935', color: '#fff', fontSize: 10, padding: '2px 6px', borderRadius: 4 }}>NEW</span>}
                </div>
                <div style={{ color: '#fff', fontWeight: 'bold', fontSize: 14 }}>{r.userName || 'Anonymous'}</div>
                {r.comment && <div style={{ color: '#ccc', fontSize: 13, marginTop: 6, lineHeight: 1.5 }}>{r.comment}</div>}
                <div style={{ color: '#555', fontSize: 11, marginTop: 8 }}>
                  v{r.appVersion || '?'} • {new Date(r.createdAt).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' })}
                </div>
              </div>
              <button onClick={() => del(r._id)}
                style={{ background: '#2a1a1a', border: 'none', color: '#ff6b6b', cursor: 'pointer', padding: '6px 12px', borderRadius: 8, fontSize: 12 }}>
                Delete
              </button>
            </div>
          </div>
        ))
      )}
    </div>
  );
}
