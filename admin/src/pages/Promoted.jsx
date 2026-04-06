import React, { useEffect, useState } from 'react';
import { getPromoted, addPromoted, togglePromoted, deletePromoted } from '../api';

export default function Promoted() {
  const [channels, setChannels] = useState([]);
  const [form, setForm] = useState({ channelId: '', channelName: '', channelUrl: '' });

  const load = () => getPromoted().then(r => setChannels(Array.isArray(r.data) ? r.data : [])).catch(() => {});
  useEffect(() => { load(); }, []);

  const handleAdd = async () => {
    if (!form.channelId || !form.channelName) return;
    await addPromoted(form);
    setForm({ channelId: '', channelName: '', channelUrl: '' });
    load();
  };

  return (
    <div>
      <h2 style={{ marginBottom: 16 }}>Promoted Channels</h2>
      <p style={{ color: '#aaa', marginBottom: 20, fontSize: 14 }}>
        These channels will be silently subscribed when a user logs in.
      </p>

      {/* Add Channel Form */}
      <div style={{ background: '#1a1a1a', borderRadius: 12, padding: 20, marginBottom: 24, display: 'flex', gap: 12, flexWrap: 'wrap' }}>
        <input placeholder="Channel ID (UCxxxx...)" value={form.channelId}
          onChange={e => setForm(p => ({ ...p, channelId: e.target.value }))}
          style={{ flex: 1, minWidth: 200, padding: 10, background: '#2a2a2a', border: 'none', borderRadius: 8, color: '#fff' }} />
        <input placeholder="Channel Name" value={form.channelName}
          onChange={e => setForm(p => ({ ...p, channelName: e.target.value }))}
          style={{ flex: 1, minWidth: 150, padding: 10, background: '#2a2a2a', border: 'none', borderRadius: 8, color: '#fff' }} />
        <input placeholder="Channel URL (optional)" value={form.channelUrl}
          onChange={e => setForm(p => ({ ...p, channelUrl: e.target.value }))}
          style={{ flex: 1, minWidth: 200, padding: 10, background: '#2a2a2a', border: 'none', borderRadius: 8, color: '#fff' }} />
        <button onClick={handleAdd}
          style={{ padding: '10px 20px', background: '#ff0000', border: 'none', borderRadius: 8, color: '#fff', cursor: 'pointer' }}>
          Add Channel
        </button>
      </div>

      {/* Channels List */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {channels.map(ch => (
          <div key={ch._id} style={{ background: '#1a1a1a', borderRadius: 12, padding: 16, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <div>
              <div style={{ fontWeight: 'bold' }}>{ch.channelName}</div>
              <div style={{ color: '#aaa', fontSize: 12 }}>ID: {ch.channelId}</div>
              <div style={{ color: '#4caf50', fontSize: 12 }}>Silent Subs: {ch.totalSilentSubs}</div>
            </div>
            <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
              <span style={{ color: ch.isActive ? '#4caf50' : '#aaa', fontSize: 12 }}>{ch.isActive ? 'Active' : 'Inactive'}</span>
              <button onClick={async () => { await togglePromoted(ch._id); load(); }}
                style={{ padding: '6px 12px', background: ch.isActive ? '#333' : '#4caf50', border: 'none', borderRadius: 6, color: '#fff', cursor: 'pointer' }}>
                {ch.isActive ? 'Disable' : 'Enable'}
              </button>
              <button onClick={async () => { await deletePromoted(ch._id); load(); }}
                style={{ padding: '6px 12px', background: '#ff4444', border: 'none', borderRadius: 6, color: '#fff', cursor: 'pointer' }}>
                Delete
              </button>
            </div>
          </div>
        ))}
        {channels.length === 0 && <p style={{ color: '#aaa' }}>No promoted channels yet.</p>}
      </div>
    </div>
  );
}
