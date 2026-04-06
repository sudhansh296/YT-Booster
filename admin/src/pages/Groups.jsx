import { useEffect, useState } from 'react';
import api, { ADMIN_BASE } from '../api';

export default function Groups() {
  const [groups, setGroups] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');
  const [filterCode, setFilterCode] = useState('');
  const [viewGroup, setViewGroup] = useState(null);
  const [messages, setMessages] = useState([]);
  const [msgLoading, setMsgLoading] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      const params = {};
      if (filterCode) params.adminCode = filterCode;
      if (search) params.search = search;
      const res = await api.get(`${ADMIN_BASE}/groups`, { params });
      setGroups(res.data);
    } catch (e) { console.error(e); }
    setLoading(false);
  };

  useEffect(() => { load(); }, [filterCode]);

  const toggleBlock = async (id) => {
    try {
      const res = await api.post(`${ADMIN_BASE}/groups/${id}/block`);
      setGroups(g => g.map(x => x._id === id ? { ...x, isBlocked: res.data.isBlocked } : x));
    } catch (e) { alert('Error: ' + (e.response?.data?.error || e.message)); }
  };

  const viewMessages = async (group) => {
    setViewGroup(group);
    setMsgLoading(true);
    try {
      // Use subadmin-style endpoint via admin — reuse admin groups messages
      const res = await api.get(`${ADMIN_BASE}/groups/${group._id}/messages`);
      setMessages(res.data);
    } catch (e) { setMessages([]); }
    setMsgLoading(false);
  };

  const subCodes = [...new Set(groups.map(g => g.adminCode).filter(Boolean))].sort();

  if (viewGroup) {
    return (
      <div className="page">
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 20 }}>
          <button onClick={() => setViewGroup(null)} style={{ padding: '8px 16px', background: '#222', border: '1px solid #333', borderRadius: 8, color: '#aaa', cursor: 'pointer' }}>← Back</button>
          <h2 style={{ margin: 0 }}>💬 {viewGroup.name}</h2>
          <span style={{ color: '#666', fontSize: 13 }}>{viewGroup.memberCount} members</span>
          {viewGroup.adminCode && <span style={{ background: '#1a2a3a', color: '#29B6F6', padding: '3px 10px', borderRadius: 20, fontSize: 12 }}>{viewGroup.adminCode}</span>}
        </div>
        {msgLoading ? <p style={{ color: '#888' }}>Loading messages...</p> : (
          <div style={{ background: '#111', borderRadius: 12, padding: 20, maxHeight: 600, overflowY: 'auto' }}>
            {messages.length === 0 ? <p style={{ color: '#555' }}>Koi message nahi</p> : messages.map(m => (
              <div key={m._id} style={{ marginBottom: 14, display: 'flex', gap: 10, alignItems: 'flex-start' }}>
                <div style={{ width: 34, height: 34, borderRadius: '50%', background: '#333', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 13, flexShrink: 0, fontWeight: 'bold' }}>
                  {m.senderName?.[0]?.toUpperCase() || '?'}
                </div>
                <div>
                  <div style={{ color: '#FFD700', fontSize: 12, fontWeight: 600, marginBottom: 3 }}>{m.senderName}</div>
                  <div style={{ background: '#1a1a2e', borderRadius: 10, padding: '8px 12px', color: '#fff', fontSize: 13, maxWidth: 500 }}>{m.text}</div>
                  <div style={{ color: '#444', fontSize: 10, marginTop: 3 }}>{new Date(m.createdAt).toLocaleString()}</div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="page">
      <h2>💬 Groups</h2>
      <div style={{ display: 'flex', gap: 12, marginBottom: 20, flexWrap: 'wrap' }}>
        <input
          value={search} onChange={e => setSearch(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && load()}
          placeholder="Group name search..."
          style={{ padding: '10px 14px', background: '#1a1a1a', border: '1px solid #333', borderRadius: 8, color: '#fff', fontSize: 13, width: 240 }}
        />
        <select value={filterCode} onChange={e => setFilterCode(e.target.value)}
          style={{ padding: '10px 14px', background: '#1a1a1a', border: '1px solid #333', borderRadius: 8, color: '#fff', fontSize: 13 }}>
          <option value="">All Sub-Admins</option>
          {subCodes.map(c => <option key={c} value={c}>{c}</option>)}
        </select>
        <button onClick={load} style={{ padding: '10px 20px', background: '#FF0000', border: 'none', borderRadius: 8, color: '#fff', cursor: 'pointer', fontWeight: 'bold' }}>
          🔍 Search
        </button>
      </div>

      {loading ? <p style={{ color: '#888' }}>Loading...</p> : (
        <div style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr>
                {['Group Name', 'Members', 'Sub-Admin', 'Created By', 'Last Message', 'Status', 'Actions'].map(h => (
                  <th key={h} style={{ padding: '10px 12px', color: '#aaa', fontWeight: 'normal', textAlign: 'left', background: '#1a1a1a' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {groups.length === 0 ? (
                <tr><td colSpan={7} style={{ padding: 20, color: '#555' }}>Koi group nahi mila</td></tr>
              ) : groups.map(g => (
                <tr key={g._id}>
                  <td style={{ padding: '10px 12px', fontWeight: 600 }}>{g.name}</td>
                  <td style={{ padding: '10px 12px', color: '#29B6F6' }}>{g.memberCount}</td>
                  <td style={{ padding: '10px 12px' }}>
                    {g.adminCode
                      ? <span style={{ background: '#1a2a3a', color: '#29B6F6', padding: '3px 10px', borderRadius: 20, fontSize: 12 }}>{g.adminCode}</span>
                      : <span style={{ color: '#555' }}>—</span>}
                  </td>
                  <td style={{ padding: '10px 12px', color: '#aaa', fontSize: 13 }}>{g.createdBy?.name || '—'}</td>
                  <td style={{ padding: '10px 12px', color: '#666', fontSize: 12, maxWidth: 180, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{g.lastMessage || '—'}</td>
                  <td style={{ padding: '10px 12px' }}>
                    <span style={{ padding: '3px 10px', borderRadius: 20, fontSize: 11, fontWeight: 600, background: g.isBlocked ? '#2a1a1a' : '#1a2a1a', color: g.isBlocked ? '#ff6b6b' : '#4CAF50' }}>
                      {g.isBlocked ? '🚫 Blocked' : '✅ Active'}
                    </span>
                  </td>
                  <td style={{ padding: '10px 12px' }}>
                    <div style={{ display: 'flex', gap: 8 }}>
                      <button onClick={() => viewMessages(g)}
                        style={{ padding: '5px 12px', background: '#1a2a3a', border: 'none', borderRadius: 6, color: '#29B6F6', cursor: 'pointer', fontSize: 12 }}>
                        👁 View
                      </button>
                      <button onClick={() => toggleBlock(g._id)}
                        style={{ padding: '5px 12px', background: g.isBlocked ? '#1a2a1a' : '#2a1a1a', border: 'none', borderRadius: 6, color: g.isBlocked ? '#4CAF50' : '#ff6b6b', cursor: 'pointer', fontSize: 12 }}>
                        {g.isBlocked ? '✅ Unblock' : '🚫 Block'}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
