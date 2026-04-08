import { useState, useEffect } from 'react';
import axios from 'axios';

const BASE = import.meta.env.VITE_API_URL || 'https://api.picrypto.in';
const headers = () => ({ 'x-admin-secret': localStorage.getItem('admin_token') });

const EVENT_TYPES = [
  { value: 'double_coins', label: '🔥 Double Coins Hour' },
  { value: 'bonus_hour', label: '⭐ Bonus Hour' },
  { value: 'tournament', label: '🏆 Tournament' },
  { value: 'custom', label: '🎯 Custom Event' },
];

export default function LiveEvents() {
  const [events, setEvents] = useState([]);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ title: '', description: '', type: 'double_coins', startTime: '', endTime: '', multiplier: 2, prizePool: 0, icon: '🔥', isActive: true });
  const [msg, setMsg] = useState('');

  const load = async () => {
    try { const r = await axios.get(`${BASE}/events/admin/all`, { headers: headers() }); setEvents(r.data); }
    catch (e) { console.error(e); }
  };

  useEffect(() => { load(); }, []);

  const save = async () => {
    try {
      await axios.post(`${BASE}/events/admin/create`, form, { headers: headers() });
      setMsg('✅ Event created! Users ko notification milegi.');
      setShowForm(false);
      setForm({ title: '', description: '', type: 'double_coins', startTime: '', endTime: '', multiplier: 2, prizePool: 0, icon: '🔥', isActive: true });
      load();
    } catch (e) { setMsg('❌ ' + (e.response?.data?.error || e.message)); }
    setTimeout(() => setMsg(''), 3000);
  };

  const del = async (id) => {
    if (!confirm('Delete event?')) return;
    await axios.delete(`${BASE}/events/admin/${id}`, { headers: headers() });
    load();
  };

  const toggle = async (event) => {
    await axios.put(`${BASE}/events/admin/${event._id}`, { isActive: !event.isActive }, { headers: headers() });
    load();
  };

  const now = new Date();
  const active = events.filter(e => e.isActive && new Date(e.startTime) <= now && new Date(e.endTime) >= now);
  const upcoming = events.filter(e => e.isActive && new Date(e.startTime) > now);
  const past = events.filter(e => !e.isActive || new Date(e.endTime) < now);

  return (
    <div style={{ padding: 24, maxWidth: 900, margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
        <h2 style={{ color: '#fff', margin: 0 }}>🔥 Live Events</h2>
        <button onClick={() => setShowForm(!showForm)} style={{ padding: '10px 20px', background: '#FF0000', border: 'none', borderRadius: 8, color: '#fff', cursor: 'pointer', fontWeight: 'bold' }}>
          {showForm ? '✕ Cancel' : '+ New Event'}
        </button>
      </div>

      {active.length > 0 && (
        <div style={{ background: '#1a3a1a', borderRadius: 12, padding: 16, marginBottom: 16, border: '1px solid #4CAF50' }}>
          <div style={{ color: '#4CAF50', fontWeight: 'bold', marginBottom: 8 }}>🟢 Active Now ({active.length})</div>
          {active.map(e => (
            <div key={e._id} style={{ color: '#fff', fontSize: 14 }}>{e.icon} {e.title} — ends {new Date(e.endTime).toLocaleString()}</div>
          ))}
        </div>
      )}

      {msg && <div style={{ padding: '10px 16px', background: msg.includes('✅') ? '#1a2a1a' : '#2a1a1a', borderRadius: 8, color: msg.includes('✅') ? '#4CAF50' : '#ff6b6b', marginBottom: 16 }}>{msg}</div>}

      {showForm && (
        <div style={{ background: '#1a1a1a', borderRadius: 12, padding: 20, marginBottom: 20, border: '1px solid #333' }}>
          <h3 style={{ color: '#FFD700', marginBottom: 16 }}>+ Create Event</h3>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            {[['Title *', 'title', 'text'], ['Icon', 'icon', 'text'], ['Description', 'description', 'text']].map(([label, key, type]) => (
              <div key={key}>
                <label style={{ color: '#aaa', fontSize: 12, display: 'block', marginBottom: 4 }}>{label}</label>
                <input type={type} value={form[key]} onChange={e => setForm({...form, [key]: e.target.value})}
                  style={{ width: '100%', padding: '10px 12px', background: '#111', border: '1px solid #333', borderRadius: 8, color: '#fff', fontSize: 14 }} />
              </div>
            ))}
            <div>
              <label style={{ color: '#aaa', fontSize: 12, display: 'block', marginBottom: 4 }}>Type</label>
              <select value={form.type} onChange={e => setForm({...form, type: e.target.value})}
                style={{ width: '100%', padding: '10px 12px', background: '#111', border: '1px solid #333', borderRadius: 8, color: '#fff', fontSize: 14 }}>
                {EVENT_TYPES.map(t => <option key={t.value} value={t.value}>{t.label}</option>)}
              </select>
            </div>
            <div>
              <label style={{ color: '#aaa', fontSize: 12, display: 'block', marginBottom: 4 }}>Start Time</label>
              <input type="datetime-local" value={form.startTime} onChange={e => setForm({...form, startTime: e.target.value})}
                style={{ width: '100%', padding: '10px 12px', background: '#111', border: '1px solid #333', borderRadius: 8, color: '#fff', fontSize: 14 }} />
            </div>
            <div>
              <label style={{ color: '#aaa', fontSize: 12, display: 'block', marginBottom: 4 }}>End Time</label>
              <input type="datetime-local" value={form.endTime} onChange={e => setForm({...form, endTime: e.target.value})}
                style={{ width: '100%', padding: '10px 12px', background: '#111', border: '1px solid #333', borderRadius: 8, color: '#fff', fontSize: 14 }} />
            </div>
            {form.type === 'double_coins' && (
              <div>
                <label style={{ color: '#aaa', fontSize: 12, display: 'block', marginBottom: 4 }}>Multiplier (e.g. 2 = 2x coins)</label>
                <input type="number" min="2" max="10" value={form.multiplier} onChange={e => setForm({...form, multiplier: parseInt(e.target.value)||2})}
                  style={{ width: '100%', padding: '10px 12px', background: '#111', border: '1px solid #333', borderRadius: 8, color: '#fff', fontSize: 14 }} />
              </div>
            )}
            {form.type === 'tournament' && (
              <div>
                <label style={{ color: '#aaa', fontSize: 12, display: 'block', marginBottom: 4 }}>Prize Pool 🪙</label>
                <input type="number" min="0" value={form.prizePool} onChange={e => setForm({...form, prizePool: parseInt(e.target.value)||0})}
                  style={{ width: '100%', padding: '10px 12px', background: '#111', border: '1px solid #333', borderRadius: 8, color: '#fff', fontSize: 14 }} />
              </div>
            )}
          </div>
          <button onClick={save} style={{ marginTop: 16, padding: '10px 24px', background: '#FF0000', border: 'none', borderRadius: 8, color: '#fff', cursor: 'pointer', fontWeight: 'bold' }}>
            Create Event
          </button>
        </div>
      )}

      <div style={{ display: 'grid', gap: 10 }}>
        {events.length === 0 ? <div style={{ background: '#1a1a1a', borderRadius: 12, padding: 40, textAlign: 'center', color: '#555' }}>Koi event nahi</div>
        : events.map(event => {
          const isActive = event.isActive && new Date(event.startTime) <= now && new Date(event.endTime) >= now;
          const isUpcoming = event.isActive && new Date(event.startTime) > now;
          return (
            <div key={event._id} style={{ background: '#1a1a1a', borderRadius: 12, padding: 16, display: 'flex', alignItems: 'center', gap: 14, borderLeft: `4px solid ${isActive ? '#4CAF50' : isUpcoming ? '#FFD700' : '#333'}` }}>
              <span style={{ fontSize: 28 }}>{event.icon}</span>
              <div style={{ flex: 1 }}>
                <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 4 }}>
                  <span style={{ color: '#fff', fontWeight: 'bold' }}>{event.title}</span>
                  <span style={{ fontSize: 11, padding: '2px 8px', borderRadius: 12, background: isActive ? '#1a3a1a' : isUpcoming ? '#2a2a0a' : '#2a2a2a', color: isActive ? '#4CAF50' : isUpcoming ? '#FFD700' : '#666' }}>
                    {isActive ? '🟢 LIVE' : isUpcoming ? '⏰ Upcoming' : '⏹ Ended'}
                  </span>
                  {event.type === 'double_coins' && <span style={{ fontSize: 11, color: '#FF9800' }}>{event.multiplier}x coins</span>}
                </div>
                <div style={{ color: '#888', fontSize: 12 }}>
                  {new Date(event.startTime).toLocaleString()} → {new Date(event.endTime).toLocaleString()}
                </div>
              </div>
              <div style={{ display: 'flex', gap: 8 }}>
                <button onClick={() => toggle(event)} style={{ padding: '5px 10px', background: event.isActive ? '#2a1a1a' : '#1a2a1a', border: 'none', borderRadius: 6, color: event.isActive ? '#ff6b6b' : '#4CAF50', cursor: 'pointer', fontSize: 12 }}>
                  {event.isActive ? 'Disable' : 'Enable'}
                </button>
                <button onClick={() => del(event._id)} style={{ padding: '5px 10px', background: '#2a0a0a', border: 'none', borderRadius: 6, color: '#ff4444', cursor: 'pointer', fontSize: 12 }}>🗑</button>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
