import { useState, useEffect } from 'react';
import api, { ADMIN_BASE } from '../api';

export default function ReferralCodes() {
  const [codes, setCodes] = useState([]);
  const [form, setForm] = useState({ code: '', label: '', role: 'sub_admin', parentCode: '' });
  const [msg, setMsg] = useState('');
  const [selectedCode, setSelectedCode] = useState(null);
  const [codeUsers, setCodeUsers] = useState([]);
  const [treeCode, setTreeCode] = useState('');
  const [treeData, setTreeData] = useState(null);
  const [treeLoading, setTreeLoading] = useState(false);
  const [activeTab, setActiveTab] = useState('codes'); // codes | tree

  const load = async () => {
    try {
      const res = await api.get(`${ADMIN_BASE}/admin-codes`);
      setCodes(Array.isArray(res.data) ? res.data : []);
    } catch (e) {
      console.error('Failed to load codes:', e);
      setCodes([]);
    }
  };

  useEffect(() => { load(); }, []);

  const create = async () => {
    if (!form.code || !form.label) return setMsg('Code aur Label required hai');
    try {
      await api.post(`${ADMIN_BASE}/admin-codes`, form);
      setMsg('✅ Code create ho gaya!');
      setForm({ code: '', label: '', role: 'sub_admin', parentCode: '' });
      load();
    } catch (e) {
      setMsg('❌ ' + (e.response?.data?.error || 'Error'));
    }
  };

  const toggle = async (id) => { await api.post(`${ADMIN_BASE}/admin-codes/${id}/toggle`); load(); };
  const del = async (id) => { if (!confirm('Delete karo?')) return; await api.delete(`${ADMIN_BASE}/admin-codes/${id}`); load(); };

  const viewUsers = async (code) => {
    setSelectedCode(code);
    const res = await api.get(`${ADMIN_BASE}/admin-codes/${code}/users`);
    setCodeUsers(res.data);
  };

  const loadTree = async (code) => {
    if (!code) return;
    setTreeLoading(true);
    try {
      const res = await api.get(`${ADMIN_BASE}/referral-tree/${code}`);
      setTreeData(res.data);
    } catch (e) { setTreeData(null); }
    setTreeLoading(false);
  };

  const ownerCodes = codes.filter(c => c.role === 'owner');
  const subCodes = codes.filter(c => c.role === 'sub_admin');

  return (
    <div style={{ padding: 24, color: '#fff', maxWidth: 960, margin: '0 auto' }}>
      <h2 style={{ color: '#FFD700' }}>🔗 Referral Codes Manager</h2>

      {/* Tab switcher */}
      <div style={{ display: 'flex', gap: 8, marginBottom: 24 }}>
        {['codes', 'tree'].map(t => (
          <button key={t} onClick={() => setActiveTab(t)} style={{
            padding: '8px 20px', borderRadius: 8, border: 'none', cursor: 'pointer', fontWeight: 600,
            background: activeTab === t ? '#FF0000' : '#2a2a2a', color: '#fff'
          }}>
            {t === 'codes' ? '📋 Codes' : '🌳 Referral Tree'}
          </button>
        ))}
      </div>

      {activeTab === 'codes' && (
        <>
          {/* Create form */}
          <div style={{ background: '#1a1a1a', borderRadius: 12, padding: 20, marginBottom: 24 }}>
            <h3 style={{ color: '#aaa', marginTop: 0 }}>Naya Code Banao</h3>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <div>
                <label style={{ color: '#aaa', fontSize: 12 }}>Code (unique)</label>
                <input value={form.code} onChange={e => setForm({ ...form, code: e.target.value.toUpperCase() })}
                  placeholder="e.g. OWNER2026" style={inputStyle} />
              </div>
              <div>
                <label style={{ color: '#aaa', fontSize: 12 }}>Label (naam)</label>
                <input value={form.label} onChange={e => setForm({ ...form, label: e.target.value })}
                  placeholder="e.g. Main Admin" style={inputStyle} />
              </div>
              <div>
                <label style={{ color: '#aaa', fontSize: 12 }}>Role</label>
                <select value={form.role} onChange={e => setForm({ ...form, role: e.target.value })} style={inputStyle}>
                  <option value="owner">Owner (Main Admin)</option>
                  <option value="sub_admin">Sub-Admin</option>
                </select>
              </div>
              <div>
                <label style={{ color: '#aaa', fontSize: 12 }}>Parent Code (sub-admin ke liye)</label>
                <input value={form.parentCode} onChange={e => setForm({ ...form, parentCode: e.target.value.toUpperCase() })}
                  placeholder="e.g. OWNER2026" style={inputStyle} />
              </div>
            </div>
            {msg && <p style={{ color: msg.includes('✅') ? '#4CAF50' : '#ff6b6b', margin: '8px 0' }}>{msg}</p>}
            <button onClick={create} style={btnStyle}>+ Create Code</button>
          </div>

          {ownerCodes.length > 0 && (
            <div style={{ marginBottom: 24 }}>
              <h3 style={{ color: '#FFD700' }}>👑 Owner Codes</h3>
              {ownerCodes.map(c => <CodeCard key={c._id} c={c} onToggle={toggle} onDelete={del} onViewUsers={viewUsers} onViewTree={(code) => { setActiveTab('tree'); setTreeCode(code); loadTree(code); }} />)}
            </div>
          )}

          <div>
            <h3 style={{ color: '#29B6F6' }}>👥 Sub-Admin Codes</h3>
            {subCodes.length === 0 && <p style={{ color: '#555' }}>Koi sub-admin code nahi hai abhi</p>}
            {subCodes.map(c => <CodeCard key={c._id} c={c} onToggle={toggle} onDelete={del} onViewUsers={viewUsers} onViewTree={(code) => { setActiveTab('tree'); setTreeCode(code); loadTree(code); }} />)}
          </div>
        </>
      )}

      {activeTab === 'tree' && (
        <div>
          <div style={{ display: 'flex', gap: 10, marginBottom: 20 }}>
            <input
              value={treeCode}
              onChange={e => setTreeCode(e.target.value.toUpperCase())}
              placeholder="Code daalo (e.g. OWNER2026, SUB001)"
              style={{ ...inputStyle, flex: 1, maxWidth: 300 }}
            />
            <button onClick={() => loadTree(treeCode)} style={btnStyle} disabled={treeLoading}>
              {treeLoading ? 'Loading...' : '🌳 Tree Dekho'}
            </button>
          </div>

          {/* Quick buttons for admin codes */}
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 20 }}>
            {codes.map(c => (
              <button key={c.code} onClick={() => { setTreeCode(c.code); loadTree(c.code); }}
                style={{ padding: '4px 12px', background: '#2a2a2a', border: '1px solid #444', borderRadius: 6, color: '#aaa', cursor: 'pointer', fontSize: 12 }}>
                {c.code}
              </button>
            ))}
          </div>

          {treeData && (
            <div style={{ background: '#111', borderRadius: 12, padding: 20 }}>
              <div style={{ color: '#FFD700', fontWeight: 700, fontSize: 16, marginBottom: 16 }}>
                🌳 Tree for: <span style={{ color: '#FF0000' }}>{treeData.code}</span>
                <span style={{ color: '#555', fontSize: 13, marginLeft: 12 }}>({countNodes(treeData.tree)} users)</span>
              </div>
              {treeData.tree.length === 0
                ? <p style={{ color: '#555' }}>Is code se koi user nahi aaya abhi</p>
                : <TreeNodes nodes={treeData.tree} depth={0} />
              }
            </div>
          )}
        </div>
      )}

      {/* Users modal */}
      {selectedCode && (
        <div style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, background: 'rgba(0,0,0,0.85)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 100 }}>
          <div style={{ background: '#1a1a1a', borderRadius: 16, padding: 24, width: 500, maxHeight: '80vh', overflowY: 'auto' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
              <h3 style={{ margin: 0, color: '#FFD700' }}>Users via "{selectedCode}"</h3>
              <button onClick={() => { setSelectedCode(null); setCodeUsers([]); }} style={{ background: 'none', border: 'none', color: '#fff', fontSize: 20, cursor: 'pointer' }}>✕</button>
            </div>
            {codeUsers.length === 0
              ? <p style={{ color: '#555' }}>Koi user nahi aaya abhi is code se</p>
              : codeUsers.map(u => (
                <div key={u._id} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '10px 0', borderBottom: '1px solid #333' }}>
                  <img src={u.profilePic} alt="" style={{ width: 36, height: 36, borderRadius: '50%' }} />
                  <div>
                    <div style={{ color: '#fff', fontWeight: 600 }}>{u.channelName}</div>
                    <div style={{ color: '#aaa', fontSize: 12 }}>{u.coins} coins • {new Date(u.createdAt).toLocaleDateString()}</div>
                  </div>
                </div>
              ))
            }
          </div>
        </div>
      )}
    </div>
  );
}

function countNodes(nodes) {
  let count = nodes.length;
  for (const n of nodes) count += countNodes(n.children || []);
  return count;
}

function TreeNodes({ nodes, depth }) {
  return (
    <div style={{ marginLeft: depth * 20 }}>
      {nodes.map(node => (
        <TreeNode key={node._id} node={node} depth={depth} />
      ))}
    </div>
  );
}

function TreeNode({ node, depth }) {
  const [open, setOpen] = useState(depth < 2);
  const hasChildren = node.children?.length > 0;
  const colors = ['#FF0000', '#FFD700', '#29B6F6', '#4CAF50', '#FF9800'];
  const color = colors[depth % colors.length];

  return (
    <div style={{ marginBottom: 6 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '6px 10px', background: '#1a1a1a', borderRadius: 8, borderLeft: `3px solid ${color}` }}>
        {hasChildren && (
          <button onClick={() => setOpen(!open)} style={{ background: 'none', border: 'none', color: '#aaa', cursor: 'pointer', fontSize: 12, padding: 0, width: 16 }}>
            {open ? '▼' : '▶'}
          </button>
        )}
        {!hasChildren && <span style={{ width: 16 }} />}
        <img src={node.profilePic} alt="" style={{ width: 28, height: 28, borderRadius: '50%' }} onError={e => e.target.style.display = 'none'} />
        <span style={{ color: '#fff', fontSize: 13, fontWeight: 600 }}>{node.channelName}</span>
        {node.referralCode && <span style={{ color: color, fontSize: 11, background: '#2a2a2a', padding: '2px 6px', borderRadius: 4 }}>{node.referralCode}</span>}
        <span style={{ color: '#FFD700', fontSize: 11, marginLeft: 'auto' }}>{node.coins} coins</span>
        {hasChildren && <span style={{ color: '#555', fontSize: 11 }}>{node.children.length} referred</span>}
      </div>
      {open && hasChildren && <TreeNodes nodes={node.children} depth={depth + 1} />}
    </div>
  );
}

function CodeCard({ c, onToggle, onDelete, onViewUsers, onViewTree }) {
  const [copied, setCopied] = useState(false);
  const [showPassForm, setShowPassForm] = useState(false);
  const [passInput, setPassInput] = useState('');
  const [passMsg, setPassMsg] = useState('');

  const copy = () => { navigator.clipboard.writeText(c.link); setCopied(true); setTimeout(() => setCopied(false), 2000); };

  const setPassword = async () => {
    if (!passInput) return;
    try {
      await api.post(`${ADMIN_BASE}/admin-codes/${c._id}/set-password`, { password: passInput });
      setPassMsg('✅ Password set!');
      setPassInput('');
      setTimeout(() => { setPassMsg(''); setShowPassForm(false); }, 2000);
    } catch (e) { setPassMsg('❌ Error'); }
  };

  return (
    <div style={{ background: '#111', border: `1px solid ${c.role === 'owner' ? '#FFD700' : '#29B6F6'}33`, borderRadius: 12, padding: 16, marginBottom: 12 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
            <span style={{ color: c.role === 'owner' ? '#FFD700' : '#29B6F6', fontWeight: 700, fontSize: 18, letterSpacing: 2 }}>{c.code}</span>
            <span style={{ background: c.isActive ? '#1a2a1a' : '#2a1a1a', color: c.isActive ? '#4CAF50' : '#ff6b6b', padding: '2px 8px', borderRadius: 20, fontSize: 11 }}>
              {c.isActive ? 'Active' : 'Inactive'}
            </span>
          </div>
          <div style={{ color: '#aaa', fontSize: 13, marginTop: 4 }}>{c.label}</div>
          {c.parentCode && <div style={{ color: '#555', fontSize: 11 }}>Parent: {c.parentCode}</div>}
          <div style={{ color: '#29B6F6', fontSize: 12, marginTop: 6, wordBreak: 'break-all' }}>{c.link}</div>
          <div style={{ marginTop: 6, display: 'flex', gap: 6, flexWrap: 'wrap' }}>
            {c.password && <span style={{ background: '#1a2a3a', color: '#29B6F6', padding: '2px 8px', borderRadius: 20, fontSize: 10 }}>🔐 Pass Set</span>}
            {c.smmApiUrl ? <span style={{ background: '#1a2a1a', color: '#4CAF50', padding: '2px 8px', borderRadius: 20, fontSize: 10 }}>✅ SMM Set</span>
              : <span style={{ background: '#2a1a1a', color: '#ff6b6b', padding: '2px 8px', borderRadius: 20, fontSize: 10 }}>⚠️ No SMM (uses global)</span>}
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8, flexShrink: 0, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
          <button onClick={copy} style={{ ...smallBtn, background: '#333' }}>{copied ? '✓' : '📋'}</button>
          <button onClick={() => onViewUsers(c.code)} style={{ ...smallBtn, background: '#1a2a3a' }}>👥 {c.totalJoined}</button>
          <button onClick={() => onViewTree(c.code)} style={{ ...smallBtn, background: '#1a2a1a' }}>🌳 Tree</button>
          <button onClick={() => setShowPassForm(!showPassForm)} style={{ ...smallBtn, background: '#2a2a1a', color: '#FFD700' }}>🔐 Pass</button>
          <button onClick={() => onToggle(c._id)} style={{ ...smallBtn, background: c.isActive ? '#2a1a1a' : '#1a2a1a' }}>{c.isActive ? '⏸' : '▶'}</button>
          <button onClick={() => onDelete(c._id)} style={{ ...smallBtn, background: '#2a1a1a', color: '#ff6b6b' }}>🗑</button>
        </div>
      </div>

      {showPassForm && (
        <div style={{ marginTop: 12, display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
          <input
            type="text"
            value={passInput}
            onChange={e => setPassInput(e.target.value)}
            placeholder="Sub-admin panel password set karo"
            style={{ ...inputStyle, marginTop: 0, flex: 1, minWidth: 200 }}
          />
          <button onClick={setPassword} style={{ ...smallBtn, background: '#FF0000', padding: '8px 16px' }}>Set</button>
          {passMsg && <span style={{ color: passMsg.includes('✅') ? '#4CAF50' : '#ff6b6b', fontSize: 12 }}>{passMsg}</span>}
        </div>
      )}

      <div style={{ display: 'flex', gap: 20, marginTop: 12 }}>
        <Stat label="Clicks" value={c.totalClicks} />
        <Stat label="Joined" value={c.totalJoined} />
        <Stat label="Coins Given" value={c.totalCoinsGiven} />
      </div>
    </div>
  );
}

function Stat({ label, value }) {
  return (
    <div style={{ textAlign: 'center' }}>
      <div style={{ color: '#FFD700', fontWeight: 700, fontSize: 18 }}>{value}</div>
      <div style={{ color: '#555', fontSize: 11 }}>{label}</div>
    </div>
  );
}

const inputStyle = { width: '100%', padding: '10px 12px', background: '#111', border: '1px solid #333', borderRadius: 8, color: '#fff', fontSize: 14, marginTop: 4, boxSizing: 'border-box' };
const btnStyle = { marginTop: 12, padding: '10px 24px', background: '#FF0000', color: '#fff', border: 'none', borderRadius: 8, cursor: 'pointer', fontWeight: 600, fontSize: 14 };
const smallBtn = { padding: '6px 10px', border: 'none', borderRadius: 6, cursor: 'pointer', color: '#fff', fontSize: 13 };
