const express = require('express');
const router = express.Router();
const authMiddleware = require('../middleware/auth');
const Review = require('../models/Review');

const adminAuth = (req, res, next) => {
  const secret = req.headers['x-admin-secret'];
  if (secret !== process.env.ADMIN_SECRET) return res.status(403).json({ error: 'Forbidden' });
  next();
};

// Submit review (user)
router.post('/', authMiddleware, async (req, res) => {
  try {
    const { rating, comment, appVersion } = req.body;
    if (!rating || rating < 1 || rating > 5) return res.status(400).json({ error: 'Rating 1-5 required' });

    // One review per user — upsert
    const review = await Review.findOneAndUpdate(
      { userId: req.user._id },
      { rating, comment: comment?.trim() || '', appVersion: appVersion || '', userName: req.user.channelName },
      { upsert: true, new: true }
    );
    res.json({ success: true, review });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Get my review (user)
router.get('/mine', authMiddleware, async (req, res) => {
  try {
    const review = await Review.findOne({ userId: req.user._id }).lean();
    res.json({ review: review || null });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Get all reviews + stats (admin)
router.get('/all', adminAuth, async (req, res) => {
  try {
    const reviews = await Review.find().sort({ createdAt: -1 }).lean();
    const total = reviews.length;
    const avg = total > 0 ? (reviews.reduce((s, r) => s + r.rating, 0) / total).toFixed(1) : 0;
    const dist = { 1: 0, 2: 0, 3: 0, 4: 0, 5: 0 };
    reviews.forEach(r => { dist[r.rating] = (dist[r.rating] || 0) + 1; });
    res.json({ reviews, total, avg, dist });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Mark review as read (admin)
router.patch('/:id/read', adminAuth, async (req, res) => {
  try {
    await Review.findByIdAndUpdate(req.params.id, { isRead: true });
    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Delete review (admin)
router.delete('/:id', adminAuth, async (req, res) => {
  try {
    await Review.findByIdAndDelete(req.params.id);
    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

module.exports = router;
