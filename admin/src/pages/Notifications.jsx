import { useState } from 'react';
import { sendNotification } from '../api';

export default function Notifications() {
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [targetType, setTargetType] = useState('all');
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);

  const handleSend = async () => {
    if (!title || !body) return alert('Title aur message dono required hain');
    setLoading(true);
    setResult(null);
    try {
      const res = await sendNotification({ title, body, targetType });
      setResult({ success: true, msg: `✅ ${res.data.sent} users ko notification bheja gaya!` });
      setTitle(''); setBody('');
    } catch (e) {
      setResult({ success: false, msg: `❌ Error: ${e.response?.data?.error || e.message}` });
    }
    setLoading(false);
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', minHeight: '70vh' }}>
      <h2>🔔 Push Notifications</h2>
      <p style={{ color: '#aaa', marginBottom: 24 }}>Saare users ko ek saath notification bhejo</p>

      <div style={{ background: '#1a1a1a', borderRadius: 12, padding: 24, width: '100%', maxWidth: 500 }}>
        <div style={{ marginBottom: 16 }}>
          <label style={{ display: 'block', marginBottom: 6, color: '#ccc' }}>Target</label>
          <select
            value={targetType}
            onChange={e => setTargetType(e.target.value)}
            style={{ width: '100%', padding: '10px 12px', borderRadius: 8, background: '#2a2a2a', color: '#fff', border: '1px solid #333' }}
          >
            <option value="all">All Users</option>
          </select>
        </div>

        <div style={{ marginBottom: 16 }}>
          <label style={{ display: 'block', marginBottom: 6, color: '#ccc' }}>Title</label>
          <input
            value={title}
            onChange={e => setTitle(e.target.value)}
            placeholder="e.g. 🆕 New Update Available!"
            style={{ width: '100%', padding: '10px 12px', borderRadius: 8, background: '#2a2a2a', color: '#fff', border: '1px solid #333', boxSizing: 'border-box' }}
          />
        </div>

        <div style={{ marginBottom: 20 }}>
          <label style={{ display: 'block', marginBottom: 6, color: '#ccc' }}>Message</label>
          <textarea
            value={body}
            onChange={e => setBody(e.target.value)}
            placeholder="e.g. YT-Booster v1.9 aa gaya! Naye features check karo."
            rows={4}
            style={{ width: '100%', padding: '10px 12px', borderRadius: 8, background: '#2a2a2a', color: '#fff', border: '1px solid #333', resize: 'vertical', boxSizing: 'border-box' }}
          />
        </div>

        <button
          onClick={handleSend}
          disabled={loading}
          style={{ width: '100%', padding: '12px', borderRadius: 8, background: loading ? '#555' : '#FF0000', color: '#fff', border: 'none', cursor: loading ? 'not-allowed' : 'pointer', fontSize: 15, fontWeight: 'bold' }}
        >
          {loading ? '⏳ Bhej raha hai...' : '🚀 Send Notification'}
        </button>

        {result && (
          <div style={{ marginTop: 16, padding: 12, borderRadius: 8, background: result.success ? '#1a3a1a' : '#3a1a1a', color: result.success ? '#4CAF50' : '#FF6B6B' }}>
            {result.msg}
          </div>
        )}
      </div>

      <div style={{ marginTop: 32, background: '#1a1a1a', borderRadius: 12, padding: 20, width: '100%', maxWidth: 500 }}>
        <h3 style={{ marginTop: 0 }}>✅ Setup Complete</h3>
        <p style={{ color: '#aaa', fontSize: 13 }}>
          Firebase Service Account configured. Notifications ready to send!<br/>
          Pehle new APK install karo users ke liye - tab FCM tokens register honge.
        </p>
      </div>
    </div>
  );
}
