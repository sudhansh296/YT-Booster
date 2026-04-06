import { useEffect, useRef, useState } from 'react';
import api, { ADMIN_BASE } from '../api';

const S = {
  page: { padding: 20, maxWidth: 1100, margin: '0 auto' },
  card: { background: '#111', borderRadius: 12, padding: 20, marginBottom: 16 },
  row: { display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' },
  badge: (color) => ({ background: color, color: '#fff', padding: '4px 12px', borderRadius: 20, fontSize: 12, fontWeight: 700 }),
  btn: (bg) => ({ padding: '8px 18px', background: bg, border: 'none', borderRadius: 8, color: '#fff', cursor: 'pointer', fontWeight: 600, fontSize: 13 }),
  inp: { padding: '10px 14px', background: '#1a1a1a', border: '1px solid #333', borderRadius: 8, color: '#fff', fontSize: 13, flex: 1 },
  msgBox: { background: '#0d0d0d', borderRadius: 10, height: 420, overflowY: 'auto', padding: 12, display: 'flex', flexDirection: 'column', gap: 8 },
  msg: (isAdmin) => ({ display: 'flex', gap: 10, alignItems: 'flex-start', background: isAdmin ? '#1a2a1a' : '#1a1a2e', borderRadius: 8, padding: '8px 12px' }),
  statBox: { background: '#1a1a1a', borderRadius: 10, padding: '14px 20px', textAlign: 'center', flex: 1, minWidth: 120 },
};

export default function CommunityLive() {
  const [status, setStatus] = useState({ isOpen: true, onlineCount: 0, pinned: '', totalMessages: 0 });
  const [messages, setMessages] = useState([]);
  const [sendText, setSendText] = useState('');
  const [pinText, setPinText] = useState('');
  const [loading, setLoading] = useState(false);
  const [autoRefresh, setAutoRefresh] = useState(true);
  const msgEndRef = useRef(null);
  const intervalRef = useRef(null);

  const loadStatus = async () => {
    try {
      const r = await api.get(`${ADMIN_BASE}/community/status`);
      setStatus(r.data);
      setPinText(r.data.pinned || '');
    } catch (e) {}
  };

  const loadMessages = async () => {
    try {
      const r = await api.get(`${ADMIN_BASE}/community/messages`);
      setMessages(r.data);
      setTimeout(() => msgEndRef.current?.scrollIntoView({ behavior: 'smooth' }), 100);
    } catch (e) {}
  };

  useEffect(() => {
    loadStatus();
    loadMessages();
  }, []);

  useEffect(() => {
    if (autoRefresh) {
      intervalRef.current = setInterval(() => { loadStatus(); loadMessages(); }, 5000);
    } else {
      clearInterval(intervalRef.current);
    }
    return () => clearInterval(intervalRef.current);
  }, [autoRefresh]);

  const toggleChat = async () => {
    try {
      const r = await api.post(`${ADMIN_BASE}/community/toggle`);
      setStatus(s => ({ ...s, isOpen: r.data.isOpen }));
    } catch (e) { alert('Error: ' + e.message); }
  };

  const sendMsg = async () => {
    if (!sendText.trim()) return;
    setLoading(true);
    try {
      await api.post(`${ADMIN_BASE}/community/send`, { text: sendText.trim() });
      setSendText('');
      await loadMessages();
    } catch (e) { alert('Error: ' + e.message); }
    setLoading(false);
  };

  const savePin = async () => {
    try {
      await api.post(`${ADMIN_BASE}/community/pin`, { message: pinText });
      setStatus(s => ({ ...s, pinned: pinText }));
      alert('Pinned message updated!');
    } catch (e) { alert('Error: ' + e.message); }
  };

  const deleteMsg = async (id) => {
    if (!confirm('Delete this message?')) return;
    try {
      await api.delete(`${ADMIN_BASE}/community/message/${id}`);
      setMessages(m => m.filter(x => x._id !== id));
    } catch (e) { alert('Error: ' + e.message); }
  };

  const clearAll = async () => {
    if (!confirm('Saare messages delete karo? Yeh undo nahi hoga!')) return;
    try {
      await api.post(`${ADMIN_BASE}/community/clear`);
      setMessages([]);
    } catch (e) { alert('Error: ' + e.message); }
  };

  const fmt = (d) => new Date(d).toLocaleTimeString('en-IN', { hour: '2-digit', minute: '2-digit' });

  return (
    <div style={S.page}>
      <div style={{ ...S.row, marginBottom: 20 }}>
        <h2 style={{ margin: 0 }}>🔴 Community Live</h2>
        <span style={S.badge(status.isOpen ? '#4CAF50' : '#E53935')}>{status.isOpen ? '● OPEN' : '● CLOSED'}</span>
        <span style={{ color: '#aaa', fontSize: 13 }}>Auto-refresh: </span>
        <label style={{ display: 'flex', alignItems: 'center', gap: 6, cursor: 'pointer' }}>
          <input type="checkbox" checked={autoRefresh} onChange={e => setAutoRefresh(e.target.checked)} />
          <span style={{ color: '#aaa', fontSize: 13 }}>Every 5s</span>
        </label>
        <button onClick={() => { loadStatus(); loadMessages(); }} style={S.btn('#333')}>🔄 Refresh</button>
      </div>

      {/* Stats Row */}
      <div style={{ ...S.row, marginBottom: 16 }}>
        <div style={S.statBox}>
          <div style={{ fontSize: 28, fontWeight: 700, color: '#4CAF50' }}>{status.onlineCount}</div>
          <div style={{ color: '#888', fontSize: 12, marginTop: 4 }}>Online Now</div>
        </div>
        <div style={S.statBox}>
          <div style={{ fontSize: 28, fontWeight: 700, color: '#29B6F6' }}>{status.totalMessages}</div>
          <div style={{ color: '#888', fontSize: 12, marginTop: 4 }}>Total Messages</div>
        </div>
        <div style={S.statBox}>
          <div style={{ fontSize: 28, fontWeight: 700, color: '#FFD700' }}>{messages.length}</div>
          <div style={{ color: '#888', fontSize: 12, marginTop: 4 }}>Loaded</div>
        </div>
        <div style={{ flex: 2, display: 'flex', gap: 10 }}>
          <button onClick={toggleChat} style={S.btn(status.isOpen ? '#E53935' : '#4CAF50')}>
            {status.isOpen ? '🔒 Chat Band Karo' : '🔓 Chat Kholo'}
          </button>
          <button onClick={clearAll} style={S.btn('#555')}>🗑️ Clear All</button>
        </div>
      </div>

      {/* Pinned Message */}
      <div style={S.card}>
        <div style={{ color: '#FFD700', fontWeight: 700, marginBottom: 10, fontSize: 14 }}>📌 Pinned Message (sabko dikhega)</div>
        <div style={S.row}>
          <input value={pinText} onChange={e => setPinText(e.target.value)}
            placeholder="Pinned message likho... (khali chhodo hatane ke liye)"
            style={S.inp} onKeyDown={e => e.key === 'Enter' && savePin()} />
          <button onClick={savePin} style={S.btn('#FFD700')}>📌 Pin</button>
          {status.pinned && <button onClick={() => { setPinText(''); api.post(`${ADMIN_BASE}/community/pin`, { message: '' }).then(() => setStatus(s => ({ ...s, pinned: '' }))); }} style={S.btn('#555')}>✕ Unpin</button>}
        </div>
        {status.pinned && <div style={{ marginTop: 8, color: '#FFD700', fontSize: 13, background: '#1a1a00', padding: '8px 12px', borderRadius: 6 }}>Current: {status.pinned}</div>}
      </div>

      {/* Chat Window */}
      <div style={S.card}>
        <div style={{ ...S.row, marginBottom: 12, justifyContent: 'space-between' }}>
          <span style={{ fontWeight: 700, fontSize: 15 }}>💬 Live Messages</span>
          <span style={{ color: '#666', fontSize: 12 }}>{messages.length} messages</span>
        </div>

        <div style={S.msgBox}>
          {messages.length === 0 && <div style={{ color: '#444', textAlign: 'center', marginTop: 80 }}>Koi message nahi abhi</div>}
          {messages.map(m => (
            <div key={m._id} style={S.msg(m.isAdmin || m.senderId === 'admin' || m.senderId?.startsWith('subadmin_'))}>
              <div style={{ width: 32, height: 32, borderRadius: '50%', background: m.isAdmin ? '#1a3a1a' : '#1a1a3a', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 13, fontWeight: 700, flexShrink: 0 }}>
                {m.senderPic ? <img src={m.senderPic} style={{ width: 32, height: 32, borderRadius: '50%', objectFit: 'cover' }} /> : (m.senderName?.[0]?.toUpperCase() || '?')}
              </div>
              <div style={{ flex: 1 }}>
                <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 3 }}>
                  <span style={{ color: m.isAdmin ? '#4CAF50' : '#29B6F6', fontSize: 12, fontWeight: 700 }}>{m.senderName}</span>
                  <span style={{ color: '#444', fontSize: 10 }}>{fmt(m.createdAt)}</span>
                  {(m.isAdmin || m.senderId === 'admin' || m.senderId?.startsWith('subadmin_')) && <span style={{ background: '#1a3a1a', color: '#4CAF50', fontSize: 10, padding: '1px 6px', borderRadius: 4 }}>ADMIN</span>}
                </div>
                <div style={{ color: '#ddd', fontSize: 13 }}>{m.text}</div>
              </div>
              <button onClick={() => deleteMsg(m._id)} style={{ background: 'none', border: 'none', color: '#555', cursor: 'pointer', fontSize: 16, padding: '0 4px' }} title="Delete">🗑</button>
            </div>
          ))}
          <div ref={msgEndRef} />
        </div>

        {/* Admin Send */}
        <div style={{ ...S.row, marginTop: 12 }}>
          <input value={sendText} onChange={e => setSendText(e.target.value)}
            placeholder="Admin message bhejo community mein..."
            style={S.inp} onKeyDown={e => e.key === 'Enter' && sendMsg()} />
          <button onClick={sendMsg} disabled={loading || !sendText.trim()} style={S.btn(loading ? '#555' : '#FF0000')}>
            {loading ? '...' : '🚀 Send'}
          </button>
        </div>
        <div style={{ color: '#555', fontSize: 11, marginTop: 6 }}>Admin messages green badge ke saath dikhenge. Sub-admins bhi message kar sakte hain (⭐ badge).</div>
      </div>

      {/* Info Box */}
      <div style={{ ...S.card, background: '#0d1a0d', border: '1px solid #1a3a1a' }}>
        <div style={{ color: '#4CAF50', fontWeight: 700, marginBottom: 8 }}>ℹ️ Community Live Features</div>
        <ul style={{ color: '#888', fontSize: 13, margin: 0, paddingLeft: 20, lineHeight: 1.8 }}>
          <li>Platform ke <b style={{ color: '#fff' }}>saare users</b> automatically Community tab mein hain</li>
          <li><b style={{ color: '#fff' }}>Open/Close</b> toggle se chat band/khol sakte ho</li>
          <li><b style={{ color: '#fff' }}>Pinned message</b> sabko top pe dikhega</li>
          <li><b style={{ color: '#fff' }}>Admin messages</b> green badge ke saath highlight honge</li>
          <li><b style={{ color: '#fff' }}>Sub-admins</b> ⭐ badge ke saath message kar sakte hain</li>
          <li>Individual messages <b style={{ color: '#fff' }}>delete</b> kar sakte ho</li>
          <li>Online count <b style={{ color: '#fff' }}>real-time</b> update hota hai</li>
        </ul>
      </div>
    </div>
  );
}
