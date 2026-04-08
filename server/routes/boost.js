const express = require('express');
const router = express.Router();
const authMiddleware = require('../middleware/auth');
const User = require('../models/User');
const Transaction = require('../models/Transaction');

const BOOST_COSTS = { '6h': 20, '12h': 35, '24h': 60, '48h': 100 };

// Boost my channel
router.post('/channel', authMiddleware, async (req, res) => {
  try {
    const { duration } = req.body; // '6h', '12h', '24h', '48h'
    const cost = BOOST_COSTS[duration];
    if (!cost) return res.status(400).json({ error: 'Invalid duration' });

    const user = await User.findById(req.user._id);
    if (!user) return res.status(404).json({ error: 'User not found' });
    if (user.coins < cost) return res.status(400).json({ error: `Insufficient coins. Need ${cost} coins.` });

    const hours = parseInt(duration);
    const boostUntil = new Date(Date.now() + hours * 60 * 60 * 1000);

    user.coins -= cost;
    user.boostedUntil = boostUntil;
    await user.save();

    await Transaction.create({
      userId: user._id,
      type: 'boost_spend',
      coins: -cost,
      description: `Channel boost for ${duration}`
    });

    res.json({ success: true, coins: user.coins, boostedUntil });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// Get boost status
router.get('/status', authMiddleware, async (req, res) => {
  try {
    const user = await User.findById(req.user._id).select('boostedUntil coins').lean();
    const isBoosted = user.boostedUntil && new Date(user.boostedUntil) > new Date();
    res.json({ isBoosted, boostedUntil: user.boostedUntil, coins: user.coins, costs: BOOST_COSTS });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// Get boosted channels (for queue — show boosted first)
router.get('/boosted-channels', authMiddleware, async (req, res) => {
  try {
    const now = new Date();
    const boosted = await User.find({
      boostedUntil: { $gt: now },
      _id: { $ne: req.user._id }
    }).select('channelName channelUrl profilePic youtubeId boostedUntil').limit(10).lean();
    res.json({ channels: boosted });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

module.exports = router;
