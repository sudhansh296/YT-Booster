import { useState, useEffect } from 'react';
import api, { ADMIN_BASE } from '../api';

export default function SubAdmins() {
  const [codes, setCodes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [msg, setMsg] = useState('');
  const [form, setForm] = useState({ code: '', label: '', parentCode: '' });

  const load = async () => {
    setLoading(true);
    try {
      const res = await api.get(`${ADMIN_BASE}/admin-codes`);
      setCodes(Array.isArray(res.data) ? res.data.filter(c => c.role === 'sub_admin') : []);
    } catch (e) { setCodes([]); }
    setLoading(false);
  };

  useEffect(() => { load(); }, []);

  const create = async () => {
    if (!form.code || !form.label) return setMsg('❌ Code aur Label required hai');
    try {
      await api.post(`${ADMIN_BASE}/admin-codes`, { ...form, role: 'sub_admin' });
      setMsg('✅ Sub-admin create ho gaya!');
      setForm({ code: '', label: '', parentCode: '' });
      load();
    } catch (e) { setMsg('❌ ' + (e.response?.data?.error || 'Error')); }
    setTimeout(() => setMsg(''), 3000);
  };

  const toggle = async (id) => { await api.post(`${ADMIN_BASE}/admin-codes/${id}/toggle`); load(); };
  const del = async (id) => { if (!confirm('Delete karo?')) return; await api.delete(`${ADMIN_BASE}/admin-codes/${id}`); load(); };

  return (
    <div className="page">
      <h2 style={{ color: '#29B6F6', marginBottom: 24 }}>👥 Sub-Admin Management</h2>

      {/* Create new sub-admin */}
      <div style={{ background: '#1a1a1a', borderRadius: 12, padding: 20, marginBottom: 28, border: '1px solid #29B6F622' }}>
        <div style={{ color: '#aaa', fontWeight: 600, marginBottom: 14 }}>+ Naya Sub-Admin Banao</div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: 12 }}>
          <div>
            <label style={{ color: '#666', fontSize: 12 }}>Code (unique, uppercase)</label>
            <input value={form.code} onChange={e => setForm({ ...form, code: e.target.value.toUpperCase() })}
              placeholder="e.g. SUB001"
              style={inp} />
          </div>
          <div>
            <label style={{ color: '#666', fontSize: 12 }}>Label (naam)</label>
            <input value={form.label} onChange={e => setForm({ ...form, label: e.target.value })}
              placeholder="e.g. Rahul Bhai"
              style={inp} />
          </div>
          <div>
            <label style={{ color: '#666', fontSize: 12 }}>Parent Code (optional)</label>
            <input value={form.parentCode} onChange={e => setForm({ ...form, parentCode: e.target.value.toUpperCase() })}
              placeholder="e.g. OWNER2026"
              style={inp} />
          </div>
        </div>
        {msg && <div style={{ color: msg.includes('✅') ? '#4CAF50' : '#ff6b6b', fontSize: 13, marginTop: 10 }}>{msg}</div>}
        <button onClick={create}
          style={{ marginTop: 14, padding: '10px 24px', background: '#29B6F6', color: '#000', border: 'none', borderRadius: 8, cursor: 'pointer', fontWeight: 700 }}>
          + Create Sub-Admin
        </button>
      </div>

      {/* Sub-admin list */}
      {loading && <p style={{ color: '#555' }}>Loading...</p>}
      {!loading && codes.length === 0 && <p style={{ color: '#555' }}>Koi sub-admin nahi hai abhi. Upar se banao.</p>}

      {codes.map(c => <SubAdminCard key={c._id} c={c} onToggle={toggle} onDelete={del} onRefresh={load} />)}
    </div>
  );
}

function SubAdminCard({ c, onToggle, onDelete, onRefresh }) {
  const [pass, setPass] = useState('');
  const [passMsg, setPassMsg] = useState('');
  const [saving, setSaving] = useState(false);
  const [copied, setCopied] = useState(false);

  const setPassword = async () => {
    if (!pass || pass.length < 4) return setPassMsg('❌ Minimum 4 characters chahiye');
    setSaving(true);
    try {
      await api.post(`${ADMIN_BASE}/admin-codes/${c._id}/set-password`, { password: pass });
      setPassMsg('✅ Password set ho gaya!');
      setPass('');
      onRefresh();
    } catch (e) { setPassMsg('❌ Error: ' + (e.response?.data?.error || 'Try again')); }
    setSaving(false);
    setTimeout(() => setPassMsg(''), 3000);
  };

  const copy = () => {
    navigator.clipboard.writeText(c.link);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div style={{ background: '#111', border: '1px solid #29B6F622', borderRadius: 12, padding: 20, marginBottom: 16 }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: 10 }}>
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
            <span style={{ color: '#29B6F6', fontWeight: 700, fontSize: 20, letterSpacing: 2 }}>{c.code}</span>
            <span style={{ background: c.isActive ? '#1a2a1a' : '#2a1a1a', color: c.isActive ? '#4CAF50' : '#ff4444', padding: '3px 10px', borderRadius: 20, fontSize: 12 }}>
              {c.isActive ? '● Active' : '● Inactive'}
            </span>
            {c.password
              ? <span style={{ background: '#1a2a3a', color: '#29B6F6', padding: '3px 10px', borderRadius: 20, fontSize: 11 }}>🔐 Password Set</span>
              : <span style={{ background: '#2a1a1a', color: '#ff6b6b', padding: '3px 10px', borderRadius: 20, fontSize: 11 }}>⚠️ No Password</span>
            }
          </div>
          <div style={{ color: '#fff', fontWeight: 600, marginTop: 4 }}>{c.label}</div>
          {c.parentCode && <div style={{ color: '#555', fontSize: 12 }}>Parent: {c.parentCode}</div>}
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button onClick={() => onToggle(c._id)}
            style={{ padding: '6px 14px', background: c.isActive ? '#2a1a1a' : '#1a2a1a', border: 'none', borderRadius: 6, color: c.isActive ? '#ff6b6b' : '#4CAF50', cursor: 'pointer', fontSize: 13 }}>
            {c.isActive ? '⏸ Disable' : '▶ Enable'}
          </button>
          <button onClick={() => onDelete(c._id)}
            style={{ padding: '6px 14px', background: '#2a1a1a', border: 'none', borderRadius: 6, color: '#ff4444', cursor: 'pointer', fontSize: 13 }}>
            🗑 Delete
          </button>
        </div>
      </div>

      {/* Stats */}
      <div style={{ display: 'flex', gap: 24, margin: '14px 0', flexWrap: 'wrap' }}>
        <StatBox label="Clicks" value={c.totalClicks || 0} color="#aaa" />
        <StatBox label="Joined" value={c.totalJoined || 0} color="#4CAF50" />
        <StatBox label="Coins Given" value={c.totalCoinsGiven || 0} color="#FFD700" />
      </div>

      {/* Referral link */}
      <div style={{ background: '#1a1a1a', borderRadius: 8, padding: '10px 14px', marginBottom: 16, display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 10, flexWrap: 'wrap' }}>
        <span style={{ color: '#29B6F6', fontSize: 12, wordBreak: 'break-all', flex: 1 }}>{c.link}</span>
        <button onClick={copy}
          style={{ padding: '5px 12px', background: copied ? '#1a2a1a' : '#2a2a2a', border: 'none', borderRadius: 6, color: copied ? '#4CAF50' : '#aaa', cursor: 'pointer', fontSize: 12, flexShrink: 0 }}>
          {copied ? '✓ Copied' : '📋 Copy'}
        </button>
      </div>

      {/* Password change - always visible */}
      <div style={{ background: '#1a1a1a', borderRadius: 10, padding: 16, border: '1px solid #333' }}>
        <div style={{ color: '#FFD700', fontWeight: 600, fontSize: 13, marginBottom: 10 }}>
          🔐 {c.password ? 'Password Change Karo' : 'Password Set Karo'}
        </div>
        <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
          <input
            type="text"
            value={pass}
            onChange={e => setPass(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && setPassword()}
            placeholder="Naya password daalo..."
            style={{ flex: 1, minWidth: 200, padding: '10px 14px', background: '#111', border: '1px solid #333', borderRadius: 8, color: '#fff', fontSize: 14 }}
          />
          <button onClick={setPassword} disabled={saving}
            style={{ padding: '10px 20px', background: '#FF0000', color: '#fff', border: 'none', borderRadius: 8, cursor: 'pointer', fontWeight: 700, fontSize: 14, flexShrink: 0 }}>
            {saving ? 'Saving...' : 'Set Password'}
          </button>
        </div>
        {passMsg && (
          <div style={{ marginTop: 8, color: passMsg.includes('✅') ? '#4CAF50' : '#ff6b6b', fontSize: 13 }}>
            {passMsg}
          </div>
        )}
        <div style={{ color: '#444', fontSize: 11, marginTop: 8 }}>
          Sub-admin login karta hai: Code = <span style={{ color: '#555' }}>{c.code}</span> + ye password
        </div>
      </div>
    </div>
  );
}

function StatBox({ label, value, color }) {
  return (
    <div style={{ textAlign: 'center' }}>
      <div style={{ color, fontWeight: 700, fontSize: 22 }}>{value}</div>
      <div style={{ color: '#555', fontSize: 11, marginTop: 2 }}>{label}</div>
    </div>
  );
}

const inp = { display: 'block', width: '100%', padding: '10px 12px', background: '#111', border: '1px solid #333', borderRadius: 8, color: '#fff', fontSize: 14, marginTop: 4, boxSizing: 'border-box' };
