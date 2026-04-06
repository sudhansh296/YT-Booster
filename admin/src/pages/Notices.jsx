import { useEffect, useState } from 'react';
import api, { ADMIN_BASE } from '../api';

export default function Notices() {
  const [notices, setNotices] = useState([]);
  const [users, setUsers] = useState([]);
  const [title, setTitle] = useState('');
  const [message, setMessage] = useState('');
  const [selectedUser, setSelectedUser] = useState(null);
  const [search, setSearch] = useState('');
  const [specificMode, setSpecificMode] = useState(false);
  const [sending, setSending] = useState(false);

  const load = async () => {
    try {
      const [n, u] = await Promise.all([
        api.get(`${ADMIN_BASE}/notices`),
        api.get(`${ADMIN_BASE}/users?limit=200`)
      ]);
      setNotices(Array.isArray(n.data) ? n.data : []);
      setUsers(Array.isArray(u.data) ? u.data : []);
    } catch (e) { console.error(e); }
  };

  useEffect(() => { load(); }, []);

  const filteredUsers = search.trim()
    ? users.filter(u => u.channelName?.toLowerCase().includes(search.toLowerCase()))
    : [];

  const send = async () => {
    if (!title.trim() || !message.trim()) return alert('Title aur message dono bharo');
    setSending(true);
    try {
      await api.post(`${ADMIN_BASE}/notices`, { title, message, targetUserId: selectedUser?._id || null });
      setTitle(''); setMessage(''); setSelectedUser(null); setSearch(''); setSpecificMode(false);
      load();
    } catch (e) { alert('Error: ' + (e.response?.data?.error || e.message)); }
    setSending(false);
  };

  const del = async (id) => {
    if (!confirm('Delete karna hai?')) return;
    await api.delete(`${ADMIN_BASE}/notices/${id}`);
    load();
  };

  return (
    <div className="page">
      <h2>📢 Notices</h2>

      <div style={{ maxWidth: 640, margin: '0 auto 32px' }}>
        <div style={{ background: '#1a1a1a', borderRadius: 12, padding: 24 }}>
          <h3 style={{ color: '#FFD700', marginBottom: 20, marginTop: 0 }}>New Notice</h3>
          <input placeholder="Title" value={title} onChange={e => setTitle(e.target.value)}
            style={{ display: 'block', width: '100%', padding: '12px 14px', background: '#111', border: '1px solid #333', borderRadius: 8, color: '#fff', marginBottom: 12, boxSizing: 'border-box' }} />
          <textarea placeholder="Message..." value={message} onChange={e => setMessage(e.target.value)} rows={4}
            style={{ display: 'block', width: '100%', padding: '12px 14px', background: '#111', border: '1px solid #333', borderRadius: 8, color: '#fff', marginBottom: 16, boxSizing: 'border-box', resize: 'vertical' }} />

          <div style={{ display: 'flex', gap: 10, marginBottom: 16 }}>
            <button onClick={() => { setSpecificMode(false); setSelectedUser(null); setSearch(''); }}
              style={{ flex: 1, padding: 12, background: !specificMode ? '#ff0000' : '#2a2a2a', color: '#fff', border: 'none', borderRadius: 8, cursor: 'pointer' }}>
              🌐 All Users
            </button>
            <button onClick={() => setSpecificMode(true)}
              style={{ flex: 1, padding: 12, background: specificMode ? '#29B6F6' : '#2a2a2a', color: specificMode ? '#000' : '#fff', border: 'none', borderRadius: 8, cursor: 'pointer' }}>
              👤 Specific User
            </button>
          </div>

          {selectedUser && (
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, background: '#1e3a4a', padding: '10px 14px', borderRadius: 10, marginBottom: 12 }}>
              <img src={selectedUser.profilePic} style={{ width: 32, height: 32, borderRadius: '50%' }} alt="" onError={e => e.target.style.display='none'} />
              <span style={{ color: '#29B6F6', fontWeight: 'bold' }}>{selectedUser.channelName}</span>
              <button onClick={() => { setSelectedUser(null); setSearch(''); }}
                style={{ marginLeft: 'auto', background: 'none', border: 'none', color: '#f44336', cursor: 'pointer', fontSize: 20 }}>×</button>
            </div>
          )}

          {specificMode && !selectedUser && (
            <div style={{ position: 'relative', marginBottom: 16 }}>
              <input placeholder="Channel name se search karo..." value={search} onChange={e => setSearch(e.target.value)}
                style={{ display: 'block', width: '100%', padding: '10px 14px', background: '#111', border: '1px solid #333', borderRadius: 8, color: '#fff', boxSizing: 'border-box' }} />
              {filteredUsers.length > 0 && (
                <div style={{ position: 'absolute', top: '100%', left: 0, right: 0, background: '#1a1a1a', border: '1px solid #333', borderRadius: 8, zIndex: 10, maxHeight: 200, overflowY: 'auto' }}>
                  {filteredUsers.map(u => (
                    <div key={u._id} onClick={() => { setSelectedUser(u); setSearch(''); }}
                      style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '10px 14px', cursor: 'pointer', borderBottom: '1px solid #222' }}>
                      <img src={u.profilePic} style={{ width: 28, height: 28, borderRadius: '50%' }} alt="" onError={e => e.target.style.display='none'} />
                      <span>{u.channelName}</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}

          <div style={{ color: '#888', fontSize: 13, marginBottom: 16 }}>
            Bhejega: <span style={{ color: selectedUser ? '#29B6F6' : '#4CAF50', fontWeight: 'bold' }}>
              {selectedUser ? `👤 ${selectedUser.channelName}` : '🌐 Sab users ko'}
            </span>
          </div>

          <button onClick={send} disabled={sending}
            style={{ width: '100%', padding: 14, background: sending ? '#555' : '#FF0000', color: '#fff', border: 'none', borderRadius: 8, cursor: 'pointer', fontSize: 15, fontWeight: 'bold' }}>
            {sending ? 'Bhej raha hai...' : '📤 Send Notice'}
          </button>
        </div>
      </div>

      <div style={{ maxWidth: 900, margin: '0 auto' }}>
        <h3>Sent Notices ({notices.length})</h3>
        {notices.length === 0
          ? <p style={{ color: '#888', textAlign: 'center', padding: 32 }}>Koi notice nahi bheja abhi tak</p>
          : notices.map(n => (
            <div key={n._id} style={{ background: '#1a1a1a', borderRadius: 10, padding: 16, marginBottom: 12, display: 'flex', gap: 16, alignItems: 'flex-start', flexWrap: 'wrap' }}>
              <div style={{ flex: 1 }}>
                <div style={{ fontWeight: 'bold', color: '#FFD700', marginBottom: 6 }}>{n.title}</div>
                <div style={{ color: '#ccc', fontSize: 14 }}>{n.message}</div>
              </div>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6, alignItems: 'flex-end' }}>
                <span style={{ background: n.targetUserId ? '#1e3a4a' : '#1a2a1a', color: n.targetUserId ? '#29B6F6' : '#4CAF50', padding: '3px 10px', borderRadius: 20, fontSize: 11 }}>
                  {n.targetUserId ? `👤 ${n.targetUserId.channelName || 'User'}` : '🌐 All Users'}
                </span>
                <span style={{ color: '#666', fontSize: 11 }}>{new Date(n.createdAt).toLocaleString()}</span>
                <button onClick={() => del(n._id)}
                  style={{ padding: '4px 10px', background: '#2a1a1a', border: 'none', borderRadius: 6, color: '#ff6b6b', cursor: 'pointer', fontSize: 12 }}>Delete</button>
              </div>
            </div>
          ))
        }
      </div>
    </div>
  );
}
