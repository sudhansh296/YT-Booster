const express = require('express');
const router = express.Router();
const authMiddleware = require('../middleware/auth');
const LiveEvent = require('../models/LiveEvent');

const adminAuth = (req, res, next) => {
  const secret = req.headers['x-admin-secret'];
  if (secret !== process.env.ADMIN_SECRET) return res.status(403).json({ error: 'Forbidden' });
  next();
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
