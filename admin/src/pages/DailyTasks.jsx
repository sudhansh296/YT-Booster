import { useState, useEffect } from 'react';
import axios from 'axios';

const BASE = import.meta.env.VITE_API_URL || 'https://api.picrypto.in';
const headers = () => ({ 'x-admin-secret': localStorage.getItem('admin_token') });

const TASK_TYPES = [
  { value: 'login', label: '📱 Daily Login' },
  { value: 'subscribe', label: '📺 Subscribe Channel' },
  { value: 'community_message', label: '💬 Community Message' },
  { value: 'refer', label: '🎁 Refer a Friend' },
  { value: 'chat_message', label: '✉️ Send Chat Message' },
  { value: 'custom', label: '🎯 Custom Task' },
];

const ICONS = ['🎯','📱','💬','📺','🎁','✉️','🔥','⭐','💰','🏆','🎮','🎲'];

export default function DailyTasks() {
  const [tasks, setTasks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [editTask, setEditTask] = useState(null);
  const [form, setForm] = useState({ title: '', description: '', type: 'custom', targetCount: 1, coinReward: 5, icon: '🎯', isActive: true });
  const [msg, setMsg] = useState('');

  const load = async () => {
    setLoading(true);
    try {
      const r = await axios.get(`${BASE}/tasks/admin/all`, { headers: headers() });
      setTasks(r.data);
    } catch (e) { console.error(e); }
    setLoading(false);
  };

  useEffect(() => { load(); }, []);

  const save = async () => {
    try {
      if (editTask) {
        await axios.put(`${BASE}/tasks/admin/${editTask._id}`, form, { headers: headers() });
        setMsg('✅ Task updated!');
      } else {
        await axios.post(`${BASE}/tasks/admin/create`, form, { headers: headers() });
        setMsg('✅ Task created!');
      }
      setShowForm(false); setEditTask(null);
      setForm({ title: '', description: '', type: 'custom', targetCount: 1, coinReward: 5, icon: '🎯', isActive: true });
      load();
    } catch (e) { setMsg('❌ ' + (e.response?.data?.error || e.message)); }
    setTimeout(() => setMsg(''), 3000);
  };

  const del = async (id) => {
    if (!confirm('Delete this task?')) return;
    await axios.delete(`${BASE}/tasks/admin/${id}`, { headers: headers() });
    load();
  };

  const toggle = async (task) => {
    await axios.put(`${BASE}/tasks/admin/${task._id}`, { isActive: !task.isActive }, { headers: headers() });
    load();
  };

  const startEdit = (task) => {
    setEditTask(task);
    setForm({ title: task.title, description: task.description, type: task.type, targetCount: task.targetCount, coinReward: task.coinReward, icon: task.icon, isActive: task.isActive });
    setShowForm(true);
  };

  return (
    <div style={{ padding: 24, maxWidth: 900, margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
        <h2 style={{ color: '#fff', margin: 0 }}>🎯 Daily Tasks</h2>
        <button onClick={() => { setShowForm(!showForm); setEditTask(null); setForm({ title: '', description: '', type: 'custom', targetCount: 1, coinReward: 5, icon: '🎯', isActive: true }); }}
          style={{ padding: '10px 20px', background: '#FF0000', border: 'none', borderRadius: 8, color: '#fff', cursor: 'pointer', fontWeight: 'bold' }}>
          {showForm ? '✕ Cancel' : '+ New Task'}
        </button>
      </div>

      {msg && <div style={{ padding: '10px 16px', background: msg.includes('✅') ? '#1a2a1a' : '#2a1a1a', borderRadius: 8, color: msg.includes('✅') ? '#4CAF50' : '#ff6b6b', marginBottom: 16 }}>{msg}</div>}

      {showForm && (
        <div style={{ background: '#1a1a1a', borderRadius: 12, padding: 20, marginBottom: 20, border: '1px solid #333' }}>
          <h3 style={{ color: '#FFD700', marginBottom: 16 }}>{editTask ? '✏️ Edit Task' : '+ Create Task'}</h3>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <div>
              <label style={{ color: '#aaa', fontSize: 12, display: 'block', marginBottom: 4 }}>Title *</label>
              <input value={form.title} onChange={e => setForm({...form, title: e.target.value})} placeholder="Task title..."
                style={{ width: '100%', padding: '10px 12px', background: '#111', border: '1px solid #333', borderRadius: 8, color: '#fff', fontSize: 14 }} />
            </div>
            <div>
              <label style={{ color: '#aaa', fontSize: 12, display: 'block', marginBottom: 4 }}>Type</label>
              <select value={form.type} onChange={e => setForm({...form, type: e.target.value})}
                style={{ width: '100%', padding: '10px 12px', background: '#111', border: '1px solid #333', borderRadius: 8, color: '#fff', fontSize: 14 }}>
                {TASK_TYPES.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
              </select>
            </div>
            <div>
              <label style={{ color: '#aaa', fontSize: 12, display: 'block', marginBottom: 4 }}>Description</label>
              <input value={form.description} onChange={e => setForm({...form, description: e.target.value})} placeholder="Optional description..."
                style={{ width: '100%', padding: '10px 12px', background: '#111', border: '1px solid #333', borderRadius: 8, color: '#fff', fontSize: 14 }} />
            </div>
            <div>
              <label style={{ color: '#aaa', fontSize: 12, display: 'block', marginBottom: 4 }}>Icon</label>
              <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                {ICONS.map(ic => (
                  <span key={ic} onClick={() => setForm({...form, icon: ic})}
                    style={{ fontSize: 22, cursor: 'pointer', padding: 4, borderRadius: 6, background: form.icon === ic ? '#333' : 'transparent', border: form.icon === ic ? '1px solid #FFD700' : '1px solid transparent' }}>
                    {ic}
                  </span>
                ))}
              </div>
            </div>
            <div>
              <label style={{ color: '#aaa', fontSize: 12, display: 'block', marginBottom: 4 }}>Target Count</label>
              <input type="number" min="1" value={form.targetCount} onChange={e => setForm({...form, targetCount: parseInt(e.target.value)||1})}
                style={{ width: '100%', padding: '10px 12px', background: '#111', border: '1px solid #333', borderRadius: 8, color: '#fff', fontSize: 14 }} />
            </div>
            <div>
              <label style={{ color: '#aaa', fontSize: 12, display: 'block', marginBottom: 4 }}>Coin Reward 🪙</label>
              <input type="number" min="1" value={form.coinReward} onChange={e => setForm({...form, coinReward: parseInt(e.target.value)||1})}
                style={{ width: '100%', padding: '10px 12px', background: '#111', border: '1px solid #333', borderRadius: 8, color: '#fff', fontSize: 14 }} />
            </div>
          </div>
          <div style={{ marginTop: 16, display: 'flex', gap: 10, alignItems: 'center' }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: 6, cursor: 'pointer', color: '#aaa' }}>
              <input type="checkbox" checked={form.isActive} onChange={e => setForm({...form, isActive: e.target.checked})} />
              Active
            </label>
            <button onClick={save} style={{ padding: '10px 24px', background: '#FF0000', border: 'none', borderRadius: 8, color: '#fff', cursor: 'pointer', fontWeight: 'bold' }}>
              {editTask ? 'Update' : 'Create'}
            </button>
          </div>
        </div>
      )}

      {loading ? <p style={{ color: '#888' }}>Loading...</p> : (
        <div style={{ display: 'grid', gap: 12 }}>
          {tasks.length === 0 ? (
            <div style={{ background: '#1a1a1a', borderRadius: 12, padding: 40, textAlign: 'center', color: '#555' }}>
              Koi task nahi. "New Task" se banao!
            </div>
          ) : tasks.map(task => (
            <div key={task._id} style={{ background: '#1a1a1a', borderRadius: 12, padding: 16, display: 'flex', alignItems: 'center', gap: 14, opacity: task.isActive ? 1 : 0.5 }}>
              <span style={{ fontSize: 32 }}>{task.icon}</span>
              <div style={{ flex: 1 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                  <span style={{ color: '#fff', fontWeight: 'bold', fontSize: 15 }}>{task.title}</span>
                  <span style={{ background: task.isActive ? '#1a2a1a' : '#2a2a2a', color: task.isActive ? '#4CAF50' : '#666', padding: '2px 8px', borderRadius: 12, fontSize: 11 }}>
                    {task.isActive ? '● Active' : '○ Inactive'}
                  </span>
                  <span style={{ background: '#1a1a2e', color: '#29B6F6', padding: '2px 8px', borderRadius: 12, fontSize: 11 }}>
                    {TASK_TYPES.find(t => t.value === task.type)?.label || task.type}
                  </span>
                </div>
                {task.description && <div style={{ color: '#888', fontSize: 12, marginBottom: 4 }}>{task.description}</div>}
                <div style={{ display: 'flex', gap: 16, fontSize: 12, color: '#aaa' }}>
                  <span>Target: {task.targetCount}x</span>
                  <span style={{ color: '#FFD700' }}>+{task.coinReward} 🪙</span>
                  {task.todayStats && <span style={{ color: '#4CAF50' }}>Today: {task.todayStats.completed} completed, {task.todayStats.claimed} claimed</span>}
                </div>
              </div>
              <div style={{ display: 'flex', gap: 8 }}>
                <button onClick={() => toggle(task)} style={{ padding: '6px 12px', background: task.isActive ? '#2a1a1a' : '#1a2a1a', border: 'none', borderRadius: 6, color: task.isActive ? '#ff6b6b' : '#4CAF50', cursor: 'pointer', fontSize: 12 }}>
                  {task.isActive ? 'Disable' : 'Enable'}
                </button>
                <button onClick={() => startEdit(task)} style={{ padding: '6px 12px', background: '#1a2a3a', border: 'none', borderRadius: 6, color: '#29B6F6', cursor: 'pointer', fontSize: 12 }}>
                  ✏️ Edit
                </button>
                <button onClick={() => del(task._id)} style={{ padding: '6px 12px', background: '#2a0a0a', border: 'none', borderRadius: 6, color: '#ff4444', cursor: 'pointer', fontSize: 12 }}>
                  🗑
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      <div style={{ marginTop: 24, background: '#0d1a0d', borderRadius: 12, padding: 16, border: '1px solid #1a3a1a' }}>
        <div style={{ color: '#4CAF50', fontWeight: 'bold', marginBottom: 8 }}>ℹ️ Task Types Guide</div>
        <ul style={{ color: '#888', fontSize: 13, margin: 0, paddingLeft: 20, lineHeight: 1.8 }}>
          <li><b style={{ color: '#fff' }}>Daily Login</b> — App open karne pe auto-complete</li>
          <li><b style={{ color: '#fff' }}>Subscribe Channel</b> — Queue mein subscribe karne pe progress</li>
          <li><b style={{ color: '#fff' }}>Community Message</b> — Community mein message bhejne pe progress</li>
          <li><b style={{ color: '#fff' }}>Refer a Friend</b> — Referral se koi join kare toh progress</li>
          <li><b style={{ color: '#fff' }}>Custom</b> — Manual progress (future use)</li>
        </ul>
      </div>
    </div>
  );
}
