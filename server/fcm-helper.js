
// ── FCM Notification Helper ──────────────────────────────────
// Background + Foreground dono mein kaam kare
async function sendFCMNotification(fcmToken, { title, body, type, data = {} }) {
  if (!fcmToken) return;
  const admin = require('./firebase-admin');
  if (!admin) return;

  try {
    await admin.messaging().send({
      token: fcmToken,
      notification: {
        title,
        body
      },
      data: {
        type: type || 'default',
        title,
        body,
        ...data
      },
      android: {
        priority: type === 'incoming_call' ? 'high' : 'normal',
        notification: {
          channelId: type === 'incoming_call' ? 'call_notifications' : 'ytbooster_channel',
          priority: type === 'incoming_call' ? 'max' : 'high',
          sound: 'default',
          defaultSound: true,
          defaultVibrateTimings: true
        }
      },
      apns: {
        payload: {
          aps: {
            sound: 'default',
            badge: 1
          }
        }
      }
    });
  } catch (e) {
    console.log('[FCM] Send failed:', e.message);
  }
}

module.exports.sendFCMNotification = sendFCMNotification;
