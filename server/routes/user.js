const express = require('express');
const axios = require('axios');
const authMiddleware = require('../middleware/auth');
const User = require('../models/User');
const Transaction = require('../models/Transaction');
const CoinRequest = require('../models/CoinRequest');
const router = express.Router();

// Referral milestones - kitne referrals pe kitne bonus coins
const REFERRAL_MILESTONES = [
  { count: 20,   bonus: 49 },
  { count: 99,   bonus: 200 },
  { count: 500,  bonus: 999 },
  { count: 2000, bonus: 4999 },
];

// Get my profile
router.get('/me', authMiddleware, async (req, res) => {
  res.json({
    _id: req.user._id,
    channelName: req.user.channelName,
    profilePic: req.user.profilePic,
    channelUrl: req.user.channelUrl,
    coins: req.user.coins,
    totalEarned: req.user.totalEarned,
    subscribersGiven: req.user.subscribersGiven,
    subscribersReceived: req.user.subscribersReceived
  });
});

// Combined init endpoint - profile + streak + referral in one call
router.get('/init', authMiddleware, async (req, res) => {
  const u = req.user;
  // Generate referral code if missing
  if (!u.referralCode) {
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    const AdminCode = require('../models/AdminCode');
    let newCode;
    do {
      newCode = Array.from({ length: 6 }, () => chars[Math.floor(Math.random() * chars.length)]).join('');
    } while (await User.findOne({ referralCode: newCode }) || await AdminCode.findOne({ code: newCode }));
    u.referralCode = newCode;
    await u.save();
  }

  const baseUrl = process.env.BASE_URL || 'https://api.picrypto.in';
  const linkToken = u.refToken || u.referralCode;

  // Referral stats - parallel queries
  const now = new Date();
  const startOfDay   = new Date(now); startOfDay.setHours(0,0,0,0);
  const startOfWeek  = new Date(now); startOfWeek.setDate(now.getDate() - 6); startOfWeek.setHours(0,0,0,0);
  const startOfMonth = new Date(now); startOfMonth.setDate(1); startOfMonth.setHours(0,0,0,0);

  const [dailyCount, weeklyCount, monthlyCount] = await Promise.all([
    User.countDocuments({ referralParent: u.referralCode, createdAt: { $gte: startOfDay } }),
    User.countDocuments({ referralParent: u.referralCode, createdAt: { $gte: startOfWeek } }),
    User.countDocuments({ referralParent: u.referralCode, createdAt: { $gte: startOfMonth } }),
  ]);

  res.json({
    profile: {
      channelName: u.channelName,
      profilePic: u.profilePic,
      channelUrl: u.channelUrl,
      coins: u.coins,
      totalEarned: u.totalEarned,
      subscribersGiven: u.subscribersGiven,
      subscribersReceived: u.subscribersReceived
    },
    streak: {
      currentStreak: u.currentStreak || 0,
      longestStreak: u.longestStreak || 0,
      lastDailyBonus: u.lastDailyBonus
    },
    referral: {
      referralCode: u.referralCode,
      referralLink: `${baseUrl}/ref/${linkToken}`,
      referralCount: u.referralCount || 0,
      referralEarned: u.referralEarned || 0,
      alreadyReferred: !!u.referredBy,
      adminCodeUsed: u.adminCodeUsed || null,
      stats: { today: dailyCount, thisWeek: weeklyCount, thisMonth: monthlyCount, total: u.referralCount || 0 },
      milestones: REFERRAL_MILESTONES.map(m => ({
        count: m.count, bonus: m.bonus,
        claimed: (u.milestoneClaimed || []).includes(m.count),
        reached: (u.referralCount || 0) >= m.count
      }))
    }
  });
});

// Daily bonus claim
router.post('/daily-bonus', authMiddleware, async (req, res) => {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const lastBonus = req.user.lastDailyBonus ? new Date(req.user.lastDailyBonus) : null;
  if (lastBonus) lastBonus.setHours(0, 0, 0, 0);

  if (lastBonus && lastBonus.getTime() === today.getTime()) {
    return res.json({ success: false, message: 'Aaj ka bonus already le liya', nextBonus: 'Kal aana' });
  }

  // Streak logic
  const yesterday = new Date(today);
  yesterday.setDate(yesterday.getDate() - 1);
  let newStreak = 1;
  if (lastBonus && lastBonus.getTime() === yesterday.getTime()) {
    newStreak = (req.user.currentStreak || 0) + 1;
  }
  const longestStreak = Math.max(newStreak, req.user.longestStreak || 0);

  // Streak bonus: every 7 days get extra 5 coins
  const streakBonus = (newStreak % 7 === 0) ? 5 : 0;
  const totalCoins = 2 + streakBonus;

  req.user.coins += totalCoins;
  req.user.totalEarned += totalCoins;
  req.user.lastDailyBonus = new Date();
  req.user.currentStreak = newStreak;
  req.user.longestStreak = longestStreak;
  await req.user.save();

  await Transaction.create({
    userId: req.user._id,
    type: 'admin_add',
    coins: totalCoins,
    description: `Daily login bonus (Streak: ${newStreak} days${streakBonus > 0 ? ` +${streakBonus} streak bonus` : ''})`
  });

  const msg = streakBonus > 0
    ? `+${totalCoins} Coins! 🔥 ${newStreak} din ki streak bonus!`
    : `+2 Daily Bonus Mila! 🔥 Streak: ${newStreak} din`;

  res.json({ success: true, coins: req.user.coins, message: msg, streak: newStreak });
});

// Get my transaction history
router.get('/transactions', authMiddleware, async (req, res) => {
  const txns = await Transaction.find({ userId: req.user._id }).sort({ createdAt: -1 }).limit(50);
  res.json(txns);
});

// Spend coins to get subscribers - fixed plans
router.post('/buy-subscribers', authMiddleware, async (req, res) => {
  const { coins } = req.body;
  if (!coins) return res.status(400).json({ error: 'Invalid plan' });

  // Conversion plans
  const plans = { 1000: 100, 5000: 500, 10000: 1500 };
  const subscribers = plans[coins];
  if (!subscribers) return res.status(400).json({ error: 'Invalid plan. Choose 1000, 5000, or 10000 coins.' });
  if (req.user.coins < coins) return res.status(400).json({ error: 'Not enough coins' });

  const SubscriberOrder = require('../models/SubscriberOrder');

  req.user.coins -= coins;
  req.user.totalSpent += coins;
  await req.user.save();

  await Transaction.create({
    userId: req.user._id,
    type: 'spend',
    coins: -coins,
    description: `Subscriber order: ${subscribers} subscribers for ${coins} coins`
  });

  const order = await SubscriberOrder.create({
    userId: req.user._id,
    channelUrl: req.user.channelUrl,
    channelName: req.user.channelName,
    quantity: subscribers,
    coinsSpent: coins
  });

  // Send to SMM panel automatically
  try {
    const Settings = require('../models/Settings');
    const AdminCode = require('../models/AdminCode');

    // Find which sub-admin's SMM to use based on user's referral tree
    // Walk up the referralParent chain to find nearest AdminCode with SMM set
    const getSmmConfig = async (userId) => {
      const u = await User.findById(userId).select('referralParent referralCode');
      if (!u || !u.referralParent) return null;

      const parent = u.referralParent.toUpperCase();
      // Check if parent is an AdminCode
      const adminCode = await AdminCode.findOne({ code: parent, isActive: true });
      if (adminCode && adminCode.smmApiUrl && adminCode.smmApiKey && adminCode.smmServiceId) {
        return { url: adminCode.smmApiUrl, key: adminCode.smmApiKey, serviceId: adminCode.smmServiceId, source: parent };
      }

      // Parent is a user code - find that user and go up
      const parentUser = await User.findOne({ referralCode: parent }).select('referralParent');
      if (parentUser) {
        return getSmmConfig(parentUser._id);
      }
      return null;
    };

    let smmUrl, smmKey, smmServiceId, smmSource;

    // Try to find sub-admin SMM first
    const subAdminSmm = await getSmmConfig(req.user._id);
    if (subAdminSmm) {
      smmUrl = subAdminSmm.url;
      smmKey = subAdminSmm.key;
      smmServiceId = subAdminSmm.serviceId;
      smmSource = subAdminSmm.source;
    } else {
      // Fallback to global settings (main admin)
      const getSetting = async (key) => {
        const s = await Settings.findOne({ key });
        return s ? s.value : process.env[key];
      };
      [smmUrl, smmKey, smmServiceId] = await Promise.all([
        getSetting('SMM_API_URL'),
        getSetting('SMM_API_KEY'),
        getSetting('SMM_SERVICE_ID')
      ]);
      smmSource = 'MAIN_ADMIN';
    }

    console.log(`[SMM] Sending order via ${smmSource}: ${subscribers} subs for ${req.user.channelUrl}`);

    const smmRes = await axios.post(smmUrl, new URLSearchParams({
      key: smmKey,
      action: 'add',
      service: smmServiceId,
      link: req.user.channelUrl,
      quantity: subscribers
    }).toString(), { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } });

    const smmData = smmRes.data;
    console.log('[SMM] Response:', JSON.stringify(smmData));

    if (smmData.order) {
      order.status = 'processing';
      order.adminNote = `SMM Order ID: ${smmData.order} (via ${smmSource})`;
    } else {
      order.status = 'failed';
      order.adminNote = JSON.stringify(smmData);
    }
    await order.save();
  } catch (e) {
    console.error('[SMM] Error:', e.message);
    order.status = 'failed';
    order.adminNote = e.message;
    await order.save();
  }

  res.json({ success: true, remainingCoins: req.user.coins, subscribers, status: order.status });
});

// Submit coin buy request (manual - admin approves)
router.post('/coin-request', authMiddleware, async (req, res) => {
  const { coins, note } = req.body;
  if (!coins || coins <= 0) return res.status(400).json({ error: 'Invalid coins' });
  // Check if already has pending request
  const existing = await CoinRequest.findOne({ userId: req.user._id, status: 'pending' });
  if (existing) return res.status(400).json({ error: 'Already have a pending request' });
  const request = await CoinRequest.create({ userId: req.user._id, coins, note });
  res.json({ success: true, requestId: request._id });
});

// Get my coin requests
router.get('/coin-requests', authMiddleware, async (req, res) => {
  const requests = await CoinRequest.find({ userId: req.user._id }).sort({ createdAt: -1 }).limit(10);
  res.json(requests);
});

// Get notices for this user (global + user-specific)
router.get('/notices', authMiddleware, async (req, res) => {
  const Notice = require('../models/Notice');
  const notices = await Notice.find({
    isActive: true,
    $or: [{ targetUserId: null }, { targetUserId: req.user._id }]
  }).sort({ createdAt: -1 }).limit(5);
  res.json(notices);
});

// Mark notice as seen (dismiss)
router.post('/notices/:id/dismiss', authMiddleware, async (req, res) => {
  res.json({ success: true });
});

// Leaderboard - top 20 by totalEarned
router.get('/leaderboard', authMiddleware, async (req, res) => {
  const users = await User.find({ isBanned: false })
    .sort({ totalEarned: -1 })
    .limit(20)
    .select('channelName profilePic totalEarned subscribersGiven subscribersReceived');
  const myRank = await User.countDocuments({ isBanned: false, totalEarned: { $gt: req.user.totalEarned } });
  res.json({ leaderboard: users, myRank: myRank + 1, myEarned: req.user.totalEarned });
});

// Get referral info with time-based stats
router.get('/referral', authMiddleware, async (req, res) => {
  // Generate 6-char code if not exists
  if (!req.user.referralCode) {
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    const AdminCode = require('../models/AdminCode');
    let newCode;
    do {
      newCode = Array.from({ length: 6 }, () => chars[Math.floor(Math.random() * chars.length)]).join('');
    } while (
      await User.findOne({ referralCode: newCode }) ||
      await AdminCode.findOne({ code: newCode })
    );
    req.user.referralCode = newCode;
    await req.user.save();
  }

  // Time-based referral counts
  const now = new Date();
  const startOfDay   = new Date(now); startOfDay.setHours(0,0,0,0);
  const startOfWeek  = new Date(now); startOfWeek.setDate(now.getDate() - 6); startOfWeek.setHours(0,0,0,0);
  const startOfMonth = new Date(now); startOfMonth.setDate(1); startOfMonth.setHours(0,0,0,0);

  const [dailyCount, weeklyCount, monthlyCount] = await Promise.all([
    User.countDocuments({ referralParent: req.user.referralCode, createdAt: { $gte: startOfDay } }),
    User.countDocuments({ referralParent: req.user.referralCode, createdAt: { $gte: startOfWeek } }),
    User.countDocuments({ referralParent: req.user.referralCode, createdAt: { $gte: startOfMonth } }),
  ]);

  const baseUrl = process.env.BASE_URL || 'https://api.picrypto.in';
  // Use refToken for link if available, else fallback to referralCode
  const linkToken = req.user.refToken || req.user.referralCode;

  res.json({
    referralCode: req.user.referralCode,
    referralLink: `${baseUrl}/ref/${linkToken}`,
    referralCount: req.user.referralCount || 0,
    referralEarned: req.user.referralEarned || 0,
    alreadyReferred: !!req.user.referredBy,
    adminCodeUsed: req.user.adminCodeUsed || null,
    stats: {
      today: dailyCount,
      thisWeek: weeklyCount,
      thisMonth: monthlyCount,
      total: req.user.referralCount || 0
    },
    milestones: REFERRAL_MILESTONES.map(m => ({
      count: m.count,
      bonus: m.bonus,
      claimed: (req.user.milestoneClaimed || []).includes(m.count),
      reached: (req.user.referralCount || 0) >= m.count
    }))
  });
});

// Check aur give milestone bonus if applicable
const checkReferralMilestone = async (referrerId) => {
  const referrer = await User.findById(referrerId);
  if (!referrer) return null;
  const count = referrer.referralCount || 0;
  const claimed = referrer.milestoneClaimed || [];
  for (const m of REFERRAL_MILESTONES) {
    if (count >= m.count && !claimed.includes(m.count)) {
      referrer.coins += m.bonus;
      referrer.totalEarned += m.bonus;
      referrer.milestoneClaimed = [...claimed, m.count];
      await referrer.save();
      await Transaction.create({
        userId: referrer._id,
        type: 'admin_add',
        coins: m.bonus,
        description: `🎉 Milestone Bonus: ${m.count} referrals complete! (+${m.bonus} coins)`
      });
      return { milestone: m.count, bonus: m.bonus };
    }
  }
  return null;
};

// Helper: Check karo ki targetUserId already currentUser ke neeche (downstream) hai ya nahi
// Agar hai toh circular hoga - block karo
const isDownstream = async (currentUserId, targetUserId) => {
  // BFS/DFS: currentUser ke referrals mein targetUser hai?
  const visited = new Set();
  const queue = [currentUserId.toString()];
  while (queue.length > 0) {
    const uid = queue.shift();
    if (visited.has(uid)) continue;
    visited.add(uid);
    // Find all users who were referred by this user
    const children = await User.find({ referredBy: uid }).select('_id');
    for (const child of children) {
      const childId = child._id.toString();
      if (childId === targetUserId.toString()) return true; // found downstream
      queue.push(childId);
    }
  }
  return false;
};

// Apply referral code
router.post('/referral/apply', authMiddleware, async (req, res) => {
  const { code } = req.body;
  if (!code) return res.status(400).json({ error: 'Code required' });
  if (req.user.referredBy) return res.status(400).json({ error: 'Already used a referral code' });
  if (req.user.adminCodeUsed) return res.status(400).json({ error: 'Tum pehle se admin/sub-admin link se join kar chuke ho, manual code use nahi kar sakte' });

  const upperCode = code.toUpperCase();
  const AdminCode = require('../models/AdminCode');

  // Check if it's a subadmin shortCode
  const adminCode = await AdminCode.findOne({ shortCode: upperCode, isActive: true });
  if (adminCode) {
    // Subadmin code use kiya - give 20 coins to user, mark adminCodeUsed
    req.user.coins += 20;
    req.user.totalEarned += 20;
    req.user.adminCodeUsed = adminCode.code;
    req.user.referredBy = adminCode._id;
    req.user.referralParent = adminCode.code;
    await req.user.save();
    await AdminCode.findByIdAndUpdate(adminCode._id, { $inc: { totalJoined: 1, totalCoinsGiven: 20 } });
    await Transaction.create({ userId: req.user._id, type: 'admin_add', coins: 20, description: `Joined via sub-admin code: ${adminCode.code} (+20 coins)` });
    return res.json({ success: true, message: `+20 Coins Mila! ${adminCode.label} ke team mein welcome!` });
  }

  const referrer = await User.findOne({ referralCode: upperCode });
  if (!referrer) return res.status(404).json({ error: 'Invalid referral code' });
  if (referrer._id.toString() === req.user._id.toString()) return res.status(400).json({ error: 'Apna code use nahi kar sakte' });

  // Hierarchy check:
  // - Upar wala (A) apne neeche wale (B) ka code use NAHI kar sakta → BLOCK
  //   Matlab: agar referrer (B) current user (A) ke downstream mein hai → block
  // - Neeche wala (B) apne upar wale (A) ka code use KAR SAKTA hai → ALLOW
  const referrerIsDownstreamOfMe = await isDownstream(req.user._id, referrer._id);
  if (referrerIsDownstreamOfMe) {
    return res.status(400).json({ error: 'Ye code use nahi kar sakte. Ye user tumhare referral tree mein neeche hai.' });
  }

  // Anti-fraud: referrer ke aaj 10+ referrals? (flood detection only)
  const today = new Date(); today.setHours(0,0,0,0);
  const referrerTodayCount = await User.countDocuments({ referredBy: referrer._id, createdAt: { $gte: today } });
  if (referrerTodayCount >= 10) {
    return res.status(400).json({ error: 'This referral code has reached its daily limit.' });
  }

  // Give 20 coins to new user, 20 coins to referrer
  req.user.coins += 20;
  req.user.totalEarned += 20;
  req.user.referredBy = referrer._id;
  await req.user.save();

  referrer.coins += 20;
  referrer.totalEarned += 20;
  referrer.referralCount = (referrer.referralCount || 0) + 1;
  referrer.referralEarned = (referrer.referralEarned || 0) + 20;
  await referrer.save();

  await Transaction.create({ userId: req.user._id, type: 'admin_add', coins: 20, description: `Referral bonus (used code: ${code})` });
  await Transaction.create({ userId: referrer._id, type: 'admin_add', coins: 20, description: `Referral reward (${req.user.channelName} joined)` });

  // Milestone bonus check
  await checkReferralMilestone(referrer._id);

  res.json({ success: true, message: '+20 Coins Mila! Referrer ko bhi +20 coins diye gaye.' });
});

// Update FCM token for push notifications
router.post('/fcm-token', authMiddleware, async (req, res) => {
  const { fcmToken } = req.body;
  if (!fcmToken) return res.status(400).json({ error: 'Token required' });
  await User.findByIdAndUpdate(req.user._id, { fcmToken });
  res.json({ success: true });
});

// Streak info
router.get('/streak', authMiddleware, async (req, res) => {
  res.json({
    currentStreak: req.user.currentStreak || 0,
    longestStreak: req.user.longestStreak || 0,
    lastDailyBonus: req.user.lastDailyBonus
  });
});

// ── Promo Videos (for Watch tab) ─────────────────────────────
router.get('/promo-videos', authMiddleware, async (req, res) => {
  try {
    const PromoVideo = require('../models/PromoVideo');
    const videos = await PromoVideo.find({ isActive: true }).sort({ priority: -1, createdAt: -1 }).limit(10).lean();
    res.json({ videos });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

router.post('/promo-video-reward', authMiddleware, async (req, res) => {
  try {
    const { videoId } = req.body;
    if (!videoId) return res.status(400).json({ error: 'videoId required' });

    const PromoVideo = require('../models/PromoVideo');
    const Transaction = require('../models/Transaction');
    const video = await PromoVideo.findById(videoId);
    if (!video || !video.isActive) return res.status(404).json({ error: 'Video not found' });

    // Check if already claimed today
    const today = new Date(); today.setHours(0, 0, 0, 0);
    const alreadyClaimed = await Transaction.findOne({
      userId: req.user._id,
      type: 'promo_watch',
      description: { $regex: videoId },
      createdAt: { $gte: today }
    });
    if (alreadyClaimed) return res.json({ success: false, message: 'Aaj ke liye already claim kar liya', coins: req.user.coins });

    req.user.coins += video.coinsReward;
    req.user.totalEarned += video.coinsReward;
    await req.user.save();

    await Transaction.create({
      userId: req.user._id,
      type: 'promo_watch',
      coins: video.coinsReward,
      description: `Watched promo: ${video.title} (${videoId})`
    });

    // Update video stats
    await PromoVideo.findByIdAndUpdate(videoId, { $inc: { totalViews: 1, totalCoinsGiven: video.coinsReward } });

    res.json({ success: true, coins: req.user.coins, earned: video.coinsReward });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// ── Video Submission Routes ─────────────────────────────
router.post('/video-order', authMiddleware, async (req, res) => {
  try {
    const { videoUrl, videoType } = req.body;
    if (!videoUrl || !videoType) return res.status(400).json({ error: 'videoUrl and videoType required' });

    // Video type rewards
    const rewards = {
      'Short': { watchers: 20, coins: 200 },
      'Long': { watchers: 10, coins: 300 }
    };

    const reward = rewards[videoType];
    if (!reward) return res.status(400).json({ error: 'Invalid video type. Use Short or Long' });

    // Validate YouTube URL
    if (!videoUrl.includes('youtube.com') && !videoUrl.includes('youtu.be')) {
      return res.status(400).json({ error: 'Valid YouTube URL required' });
    }

    // Award coins immediately
    req.user.coins += reward.coins;
    req.user.totalEarned += reward.coins;
    await req.user.save();

    // Create transaction record
    await Transaction.create({
      userId: req.user._id,
      type: 'video_submit',
      coins: reward.coins,
      description: `${videoType} video submitted: ${reward.watchers} watchers, ${reward.coins} coins`
    });

    res.json({ 
      success: true, 
      message: `${videoType} video submitted! ${reward.coins} coins earned for ${reward.watchers} watchers`,
      coins: req.user.coins,
      earned: reward.coins
    });

  } catch (e) { 
    res.status(500).json({ error: e.message }); 
  }
});

module.exports = router;
