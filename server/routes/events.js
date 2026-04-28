const express = require('express');
const router = express.Router();
const authMiddleware = require('../middleware/auth');
const LiveEvent = require('../models/LiveEvent');

const adminAuth = (req, res, next) => {
  const secret = req.headers['x-admin-secret'];
  if (secret === process.env.ADMIN_SECRET) return next();
  const token = req.headers['x-admin-token'];
  if (token) {
    try {
      const jwt = require('jsonwebtoken');
      const decoded = jwt.verify(token, process.env.JWT_SECRET);
      if (decoded.role === 'admin') return next();
    } catch (e) {}
  }
  return res.status(403).json({ error: 'Forbidden' });
};

// Get active events
router.get('/active', authMiddleware, async (req, res) => {
  try {
    const now = new Date();
    const events = await LiveEvent.find({
      isActive: true,
      startTime: { $lte: now },
      endTime: { $gte: now }
    }).lean();
    res.json({ events });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// Get all events (upcoming + active)
router.get('/upcoming', authMiddleware, async (req, res) => {
  try {
    const now = new Date();
    const events = await LiveEvent.find({
      isActive: true,
      endTime: { $gte: now }
    }).sort({ startTime: 1 }).lean();
    res.json({ events });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// Check if double coins active
router.get('/multiplier', authMiddleware, async (req, res) => {
  try {
    const now = new Date();
    const event = await LiveEvent.findOne({
      isActive: true,
      type: 'double_coins',
      startTime: { $lte: now },
      endTime: { $gte: now }
    }).lean();
    res.json({ multiplier: event ? event.multiplier : 1, event: event || null });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// Admin CRUD
router.get('/admin/all', adminAuth, async (req, res) => {
  try {
    const events = await LiveEvent.find().sort({ startTime: -1 }).lean();
    res.json(events);
  } catch (e) { res.status(500).json({ error: e.message }); }
});

router.post('/admin/create', adminAuth, async (req, res) => {
  try {
    const event = await LiveEvent.create(req.body);
    
    // Notify all users via socket
    const io = req.app.get('io');
    if (io) io.emit('live_event_started', { event });

    // Send FCM to all users if it's a double_coins event
    if (event.type === 'double_coins' && event.isActive) {
      try {
        const admin = require('../firebase-admin');
        const User = require('../models/User');
const { sendFCMNotification } = require('../fcm-helper');
        if (admin) {
          const users = await User.find({ 
            fcmToken: { $exists: true, $ne: null, $ne: '' } 
          }).select('fcmToken _id').limit(1000).lean(); // Limit to avoid overload

          for (const user of users) {
            admin.messaging().send({
              token: user.fcmToken,
              notification: { 
                title: `${event.icon} ${event.title}`, 
                body: `${event.multiplier}x coins mil rahe hain! Abhi earn karo 🪙` 
              },
              data: { type: 'live_event', eventId: event._id.toString(), eventType: event.type },
              android: { priority: 'high', notification: { channelId: 'ytbooster_channel', sound: 'default' } }
            }).catch(async e => {
              if (e.message && (e.message.includes('not found') || e.message.includes('invalid') || e.message.includes('Unregistered'))) {
                await User.findByIdAndUpdate(user._id, { $unset: { fcmToken: 1 } });
              }
            });
          }
        }
      } catch (e) { console.error('FCM event notification error:', e.message); }
    }

    res.json({ success: true, event });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

router.put('/admin/:id', adminAuth, async (req, res) => {
  try {
    const event = await LiveEvent.findByIdAndUpdate(req.params.id, req.body, { new: true });
    res.json({ success: true, event });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

router.delete('/admin/:id', adminAuth, async (req, res) => {
  try {
    await LiveEvent.findByIdAndDelete(req.params.id);
    res.json({ success: true });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

module.exports = router;
