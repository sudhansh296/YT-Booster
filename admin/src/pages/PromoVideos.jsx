import { useEffect, useState } from 'react';
import api, { ADMIN_BASE } from '../api';

export default function PromoVideos() {
  const [videos, setVideos] = useState([]);
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState({ title: '', youtubeUrl: '', channelName: '', type: 'short', coinsReward: 5, priority: 0 });
  const [adding, setAdding] = useState(false);
  const [msg, setMsg] = useState('');

  const load = async () => {
    setLoading(true);
    try { const r = await api.get(`${ADMIN_BASE}/promo-videos`); setVideos(r.data); }
    catch (e) { console.error(e); }
    setLoading(false);
  };

  useEffect(() => { load(); }, []);

  const handleTypeChange = (type) => {
    setForm(f => ({ ...f, type, coinsReward: type === 'long' ? 15 : 5 }));
  };

  const addVideo = async () => {
    if (!form.youtubeUrl) return setMsg('❌ YouTube URL required');
    setAdding(true);
    try {
      await api.post(`${ADMIN_BASE}/promo-videos`, form);
      setMsg('✅ Video added!');
      setForm({ title: '', youtubeUrl: '', channelName: '', type: 'short', coinsReward: 5, priority: 0 });
      load();
    } catch (e) { setMsg('❌ ' + (e.response?.data?.error || e.message)); }
    setAdding(false);
  };

  const toggle = async (id) => {
    try {
      const r = await api.post(`${ADMIN_BASE}/promo-videos/${id}/toggle`);
      setVideos(v => v.map(x => x._id === id ? { ...x, isActive: r.data.isActive } : x));
    } catch (e) { alert('Error'); }
  };

  const del = async (id) => {
    if (!confirm('Delete this video?')) return;
    try { await api.delete(`${ADMIN_BASE}/promo-videos/${id}`); setVideos(v => v.filter(x => x._id !== id)); }
    catch (e) { alert('Error'); }
  };

  const getYtId = (url) => {
    const m = url.match(/(?:v=|youtu\.be\/|shorts\/)([a-zA-Z0-9_-]{11})/);
    return m ? m[1] : null;
  };

  return (
    <div className="page">
      <h2>🎬 Promo Videos</h2>
      <p style={{ color: '#888', marginBottom: 20 }}>Exchange Watch tab mein dikhne wale promotional videos manage karo</p>

      {/* Add Form */}
      <div style={{ background: '#1a1a1a', borderRadius: 12, padding: 20, marginBottom: 24 }}>
        <div style={{ color: '#FFD700', fontWeight: 700, marginBottom: 16 }}>➕ Naya Promo Video Add Karo</div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <div>
            <label style={{ color: '#aaa', fontSize: 12, display: 'block', marginBottom: 4 }}>YouTube URL *</label>
            <input value={form.youtubeUrl} onChange={e => setForm(f => ({ ...f, youtubeUrl: e.target.value }))}
              placeholder="https://youtube.com/watch?v=..." style={{ width: '100%', padding: '10px 12px', background: '#111', border: '1px solid #333', borderRadius: 8, color: '#fff', fontSize: 13, boxSizing: 'border-box' }} />
          </div>
          <div>
            <label style={{ color: '#aaa', fontSize: 12, display: 'block', marginBottom: 4 }}>Title</label>
            <input value={form.title} onChange={e => setForm(f => ({ ...f, title: e.target.value }))}
              placeholder="Video title..." style={{ width: '100%', padding: '10px 12px', background: '#111', border: '1px solid #333', borderRadius: 8, color: '#fff', fontSize: 13, boxSizing: 'border-box' }} />
          </div>
          <div>
            <label style={{ color: '#aaa', fontSize: 12, display: 'block', marginBottom: 4 }}>Channel Name</label>
            <input value={form.channelName} onChange={e => setForm(f => ({ ...f, channelName: e.target.value }))}
              placeholder="Channel name..." style={{ width: '100%', padding: '10px 12px', background: '#111', border: '1px solid #333', borderRadius: 8, color: '#fff', fontSize: 13, boxSizing: 'border-box' }} />
          </div>
          <div>
            <label style={{ color: '#aaa', fontSize: 12, display: 'block', marginBottom: 4 }}>Priority (higher = first)</label>
            <input type="number" value={form.priority} onChange={e => setForm(f => ({ ...f, priority: parseInt(e.target.value) || 0 }))}
              style={{ width: '100%', padding: '10px 12px', background: '#111', border: '1px solid #333', borderRadius: 8, color: '#fff', fontSize: 13, boxSizing: 'border-box' }} />
          </div>
        </div>
        <div style={{ display: 'flex', gap: 12, marginTop: 12, alignItems: 'center', flexWrap: 'wrap' }}>
          <div>
            <label style={{ color: '#aaa', fontSize: 12, display: 'block', marginBottom: 4 }}>Type</label>
            <div style={{ display: 'flex', gap: 8 }}>
              {[['short', '⚡ Short (60s)', 5], ['long', '🎬 Long (150s)', 15]].map(([t, label, coins]) => (
                <button key={t} onClick={() => handleTypeChange(t)}
                  style={{ padding: '8px 16px', background: form.type === t ? '#FF0000' : '#222', border: '1px solid #333', borderRadius: 8, color: '#fff', cursor: 'pointer', fontSize: 13 }}>
                  {label} • {coins} coins
                </button>
              ))}
            </div>
          </div>
          <div>
            <label style={{ color: '#aaa', fontSize: 12, display: 'block', marginBottom: 4 }}>Coins Reward</label>
            <input type="number" value={form.coinsReward} onChange={e => setForm(f => ({ ...f, coinsReward: parseInt(e.target.value) || 5 }))}
              style={{ width: 80, padding: '8px 12px', background: '#111', border: '1px solid #333', borderRadius: 8, color: '#FFD700', fontSize: 14, fontWeight: 700 }} />
          </div>
          <button onClick={addVideo} disabled={adding}
            style={{ padding: '10px 24px', background: adding ? '#555' : '#FF0000', border: 'none', borderRadius: 8, color: '#fff', cursor: adding ? 'not-allowed' : 'pointer', fontWeight: 700, marginTop: 20 }}>
            {adding ? '...' : '➕ Add Video'}
          </button>
        </div>
        {msg && <div style={{ marginTop: 10, color: msg.includes('✅') ? '#4CAF50' : '#ff6b6b', fontSize: 13 }}>{msg}</div>}
      </div>

      {/* Videos List */}
      {loading ? <p style={{ color: '#888' }}>Loading...</p> : (
        <div style={{ display: 'grid', gap: 12 }}>
          {videos.length === 0 ? <p style={{ color: '#555' }}>Koi promo video nahi</p> : videos.map(v => {
            const ytId = getYtId(v.youtubeUrl);
            return (
              <div key={v._id} style={{ background: '#1a1a1a', borderRadius: 12, padding: 16, display: 'flex', gap: 16, alignItems: 'center' }}>
                {ytId && <img src={`https://img.youtube.com/vi/${ytId}/mqdefault.jpg`} style={{ width: 120, height: 68, borderRadius: 8, objectFit: 'cover', flexShrink: 0 }} />}
                <div style={{ flex: 1 }}>
                  <div style={{ fontWeight: 700, marginBottom: 4 }}>{v.title || v.channelName}</div>
                  <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 6 }}>
                    <span style={{ background: v.type === 'long' ? '#1a2a3a' : '#2a1a1a', color: v.type === 'long' ? '#29B6F6' : '#FF6B6B', padding: '2px 8px', borderRadius: 4, fontSize: 11 }}>
                      {v.type === 'long' ? '🎬 Long 150s' : '⚡ Short 60s'}
                    </span>
                    <span style={{ background: '#1a2a1a', color: '#FFD700', padding: '2px 8px', borderRadius: 4, fontSize: 11 }}>+{v.coinsReward} coins</span>
                    <span style={{ background: '#1a1a2a', color: '#aaa', padding: '2px 8px', borderRadius: 4, fontSize: 11 }}>👁 {v.totalViews} views</span>
                    <span style={{ background: '#1a1a2a', color: '#aaa', padding: '2px 8px', borderRadius: 4, fontSize: 11 }}>🪙 {v.totalCoinsGiven} given</span>
                  </div>
                  <div style={{ color: '#555', fontSize: 11, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: 300 }}>{v.youtubeUrl}</div>
                </div>
                <div style={{ display: 'flex', gap: 8, flexShrink: 0 }}>
                  <button onClick={() => toggle(v._id)}
                    style={{ padding: '6px 14px', background: v.isActive ? '#1a2a1a' : '#2a1a1a', border: 'none', borderRadius: 6, color: v.isActive ? '#4CAF50' : '#ff6b6b', cursor: 'pointer', fontSize: 12 }}>
                    {v.isActive ? '✅ Active' : '⏸ Paused'}
                  </button>
                  <button onClick={() => del(v._id)}
                    style={{ padding: '6px 12px', background: '#2a1a1a', border: 'none', borderRadius: 6, color: '#ff6b6b', cursor: 'pointer', fontSize: 12 }}>🗑</button>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
