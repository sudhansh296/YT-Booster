import { useEffect, useRef, useState } from 'react';
import api, { ADMIN_BASE } from '../api';

export default function Settings() {
  const [form, setForm] = useState({ SMM_API_URL: '', SMM_API_KEY: '', SMM_SERVICE_ID: '' });
  const [saved, setSaved] = useState(false);
  const [loading, setLoading] = useState(true);

  // Admin profile
  const [profile, setProfile] = useState({ name: '', pic: '' });
  const [profileSaved, setProfileSaved] = useState(false);
  const [picFile, setPicFile] = useState(null);
  const [picPreview, setPicPreview] = useState('');
  const [profileLoading, setProfileLoading] = useState(false);
  const fileRef = useRef();

  useEffect(() => {
    Promise.all([
      api.get(`${ADMIN_BASE}/settings`),
      api.get(`${ADMIN_BASE}/profile`)
    ]).then(([s, p]) => {
      setForm(s.data);
      setProfile(p.data);
      setPicPreview(p.data.pic || '');
      setLoading(false);
    }).catch(() => setLoading(false));
  }, []);

  const save = async () => {
    await api.post(`${ADMIN_BASE}/settings`, form);
    setSaved(true);
    setTimeout(() => setSaved(false), 3000);
  };

  const saveProfile = async () => {
    setProfileLoading(true);
    try {
      const fd = new FormData();
      fd.append('name', profile.name);
      if (picFile) fd.append('pic', picFile);
      const res = await api.post(`${ADMIN_BASE}/profile`, fd, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
      setProfile(res.data);
      setPicPreview(res.data.pic || '');
      setPicFile(null);
      setProfileSaved(true);
      setTimeout(() => setProfileSaved(false), 3000);
    } catch (e) {
      alert('Save failed: ' + (e.response?.data?.error || e.message));
    } finally {
      setProfileLoading(false);
    }
  };

  const onPicChange = (e) => {
    const file = e.target.files[0];
    if (!file) return;
    setPicFile(file);
    setPicPreview(URL.createObjectURL(file));
  };

  return (
    <div className="page">
      <h2>⚙️ Settings</h2>
      <div style={{ maxWidth: 540, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 24 }}>

        {/* Admin Profile Card */}
        <div style={{ background: '#1a1a1a', borderRadius: 12, padding: 24 }}>
          <h3 style={{ color: '#fff', marginTop: 0, marginBottom: 20, fontSize: 16 }}>👤 Admin Profile</h3>
          {loading ? <p style={{ color: '#888' }}>Loading...</p> : (
            <>
              {/* Profile Pic */}
              <div style={{ display: 'flex', alignItems: 'center', gap: 16, marginBottom: 20 }}>
                <div
                  onClick={() => fileRef.current.click()}
                  style={{
                    width: 80, height: 80, borderRadius: '50%', background: '#222',
                    border: '2px dashed #444', cursor: 'pointer', overflow: 'hidden',
                    display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0
                  }}
                >
                  {picPreview
                    ? <img src={picPreview} alt="pic" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                    : <span style={{ fontSize: 28 }}>📷</span>
                  }
                </div>
                <div>
                  <div style={{ color: '#fff', fontWeight: 'bold', fontSize: 15 }}>{profile.name || 'Admin'}</div>
                  <div
                    onClick={() => fileRef.current.click()}
                    style={{ color: '#FF0000', fontSize: 13, cursor: 'pointer', marginTop: 4 }}
                  >
                    {picFile ? '✓ ' + picFile.name : 'Change Photo'}
                  </div>
                </div>
                <input ref={fileRef} type="file" accept="image/*" style={{ display: 'none' }} onChange={onPicChange} />
              </div>

              {/* Name */}
              <div style={{ marginBottom: 20 }}>
                <label style={{ color: '#aaa', fontSize: 13, display: 'block', marginBottom: 6 }}>Display Name</label>
                <input
                  value={profile.name}
                  onChange={e => setProfile({ ...profile, name: e.target.value })}
                  placeholder="Admin"
                  style={{ display: 'block', width: '100%', padding: '10px 12px', background: '#111', border: '1px solid #333', borderRadius: 8, color: '#fff', boxSizing: 'border-box' }}
                />
              </div>

              <button
                onClick={saveProfile}
                disabled={profileLoading}
                style={{ width: '100%', padding: 14, background: profileSaved ? '#4CAF50' : '#FF0000', color: '#fff', border: 'none', borderRadius: 8, cursor: 'pointer', fontSize: 15, fontWeight: 'bold' }}
              >
                {profileLoading ? 'Saving...' : profileSaved ? '✅ Saved!' : 'Save Profile'}
              </button>
            </>
          )}
        </div>

        {/* SMM Settings Card */}
        <div style={{ background: '#1a1a1a', borderRadius: 12, padding: 24 }}>
          <h3 style={{ color: '#fff', marginTop: 0, marginBottom: 20, fontSize: 16 }}>🔧 SMM Panel Settings</h3>
          {loading ? <p style={{ color: '#888' }}>Loading...</p> : (
            <>
              {[['API URL', 'SMM_API_URL', 'https://peakerr.com/api/v2'],
                ['API Key', 'SMM_API_KEY', 'Your SMM API Key'],
                ['Service ID', 'SMM_SERVICE_ID', 'e.g. 14265']].map(([label, key, ph]) => (
                <div key={key} style={{ marginBottom: 20 }}>
                  <label style={{ color: '#aaa', fontSize: 13, display: 'block', marginBottom: 6 }}>{label}</label>
                  <input value={form[key]} onChange={e => setForm({ ...form, [key]: e.target.value })}
                    placeholder={ph}
                    style={{ display: 'block', width: '100%', padding: '10px 12px', background: '#111', border: '1px solid #333', borderRadius: 8, color: '#fff', boxSizing: 'border-box' }} />
                </div>
              ))}
              <button onClick={save}
                style={{ width: '100%', padding: 14, background: saved ? '#4CAF50' : '#FF0000', color: '#fff', border: 'none', borderRadius: 8, cursor: 'pointer', fontSize: 15, fontWeight: 'bold' }}>
                {saved ? '✅ Saved!' : 'Save Settings'}
              </button>
            </>
          )}
        </div>

      </div>
    </div>
  );
}
