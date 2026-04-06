// Firebase Admin SDK wrapper — safe init, returns null if not configured
let adminInstance = null;

try {
  const admin = require('firebase-admin');
  const path = require('path');
  const fs = require('fs');

  const serviceAccountPath = path.join(__dirname, 'firebase-service-account.json');
  if (fs.existsSync(serviceAccountPath)) {
    const serviceAccount = JSON.parse(fs.readFileSync(serviceAccountPath, 'utf8'));
    if (serviceAccount.project_id && serviceAccount.project_id !== 'YOUR_PROJECT_ID') {
      if (!admin.apps.length) {
        admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
        console.log('[Firebase] Admin SDK initialized');
      }
      // Always set instance if apps exist
      adminInstance = admin;
    }
  }
} catch (e) {
  console.log('[Firebase] Not configured:', e.message);
}

module.exports = adminInstance;
