const express = require('express');
const router = express.Router();
const authMiddleware = require('../middleware/auth');
const User = require('../models/User');

// Search users to add as friend
router.get('/search', authMiddleware, async (req, res) => {
  try {
    const { q } = req.query;
    if (!q || q.length < 2) return res.json({ users: [] });
    const users = await User.find({
      _id: { $ne: req.user._id },
      channelName: { $regex: q, $options: 'i' },
      isBanned: false
    }).select('channelName profilePic coins totalEarned subscribersGiven').limit(10).lean();
    res.json({ users });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// Get my friends list
router.get('/', authMiddleware, async (req, res) => {
  try {
    const user = await User.findById(req.user._id).select('friends').lean();
    const friendIds = user.friends || [];
    const friends = await User.find({ _id: { $in: friendIds } })
      .select('channelName profilePic coins totalEarned subscribersGiven').lean();
    res.json({ friends });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// Add friend
router.post('/add/:userId', authMiddleware, async (req, res) => {
  try {
    const targetId = req.params.userId;
    if (targetId === req.user._id.toString()) return res.status(400).json({ error: 'Apne aap ko add nahi kar sakte' });

    await User.findByIdAndUpdate(req.user._id, { $addToSet: { friends: targetId } });
    await User.findByIdAndUpdate(targetId, { $addToSet: { friends: req.user._id } });

    // Notify via socket
    const io = req.app.get('io');
    if (io) {
      io.to(`user_${targetId}`).emit('friend_added', {
        userId: req.user._id,
        name: req.user.channelName,
        pic: req.user.profilePic
      });
    }
    res.json({ success: true });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// Remove friend
router.delete('/remove/:userId', authMiddleware, async (req, res) => {
  try {
    await User.findByIdAndUpdate(req.user._id, { $pull: { friends: req.params.userId } });
    await User.findByIdAndUpdate(req.params.userId, { $pull: { friends: req.user._id } });
    res.json({ success: true });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

module.exports = router;
