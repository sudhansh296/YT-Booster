const express = require('express');
const User = require('../models/User');
const Transaction = require('../models/Transaction');
const PromotedChannel = require('../models/PromotedChannel');
const CoinRequest = require('../models/CoinRequest');
const rateLimit = require('express-rate-limit');
const router = express.Router();

// Rate limiter - 5 login attempts per 15 min
const loginLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 5,
  message: { error: 'Too many login attempts. Try again after 15 minutes.' },
  standardHeaders: true,
  legacyHeaders: false,
});

const adminAuth = (req, res, next) => {
  const secret = req.headers['x-admin-secret'];
  if (secret !== process.env.ADMIN_SECRET) return res.status(403).json({ error: 'Forbidden' });
  next();
};

// Admin login - returns JWT session token
router.post('/login', loginLimiter, (req, res) => {
  const { secret } = req.body;
  if (!secret || secret !== process.env.ADMIN_SECRET) {
    return res.status(403).json({ error: 'Wrong password' });
  }
  const jwt = require('jsonwebtoken');
  const token = jwt.sign({ role: 'admin' }, process.env.JWT_SECRET, { expiresIn: '7d' });
  res.json({ success: true, token });
});

// JWT-based admin auth middleware
const adminAuthJwt = (req, res, next) => {
  // Support both old secret header (backward compat) and new JWT token
  const secret = req.headers['x-admin-secret'];
  if (secret === process.env.ADMIN_SECRET) return next();

  const token = req.headers['x-admin-token'];
  if (!token) return res.status(403).json({ error: 'Forbidden' });
  try {
    const jwt = require('jsonwebtoken');
    const decoded = jwt.verify(token, process.env.JWT_SECRET);
    if (decoded.role !== 'admin') return res.status(403).json({ error: 'Forbidden' });
    next();
  } catch (e) {
    return res.status(403).json({ error: 'Session expired' });
  }
};

router.get('/stats', adminAuthJwt, async (req, res) => {
  res.set('Cache-Control', 'no-store, no-cache, must-revalidate');
  res.set('Pragma', 'no-cache');
  try {
    const sevenDaysAgo = new Date(); sevenDaysAgo.setDate(sevenDaysAgo.getDate() - 6); sevenDaysAgo.setHours(0,0,0,0);
    const now = new Date();
    const startOfDay = new Date(now); startOfDay.setHours(0, 0, 0, 0);
    const startOfWeek = new Date(now); startOfWeek.setDate(now.getDate() - 6); startOfWeek.setHours(0, 0, 0, 0);
    const startOfMonth = new Date(now); startOfMonth.setDate(1); startOfMonth.setHours(0, 0, 0, 0);

    // Run all queries in parallel
    const [
      totalUsers, totalCoinsAgg, activeUsers, bannedUsers,
      totalReferrals, dailySignups, dailyCoins,
      dailyJoined, weeklyJoined, monthlyJoined, totalCoinsGivenAgg
    ] = await Promise.all([
      User.countDocuments(),
      User.aggregate([{ $group: { _id: null, total: { $sum: '$coins' } } }]),
      User.countDocuments({ inQueue: true }),
      User.countDocuments({ isBanned: true }),
      User.countDocuments({ referredBy: { $ne: null } }),
      User.aggregate([
        { $match: { createdAt: { $gte: sevenDaysAgo } } },
        { $group: { _id: { $dateToString: { format: '%Y-%m-%d', date: '$createdAt' } }, count: { $sum: 1 } } },
        { $sort: { _id: 1 } }
      ]),
      Transaction.aggregate([
        { $match: { createdAt: { $gte: sevenDaysAgo }, type: { $in: ['earn', 'admin_add'] } } },
        { $group: { _id: { $dateToString: { format: '%Y-%m-%d', date: '$createdAt' } }, total: { $sum: '$coins' } } },
        { $sort: { _id: 1 } }
      ]),
      User.countDocuments({ createdAt: { $gte: startOfDay } }),
      User.countDocuments({ createdAt: { $gte: startOfWeek } }),
      User.countDocuments({ createdAt: { $gte: startOfMonth } }),
      Transaction.aggregate([
        { $match: { createdAt: { $gte: sevenDaysAgo }, coins: { $gt: 0 } } },
        { $group: { _id: null, total: { $sum: '$coins' } } }
      ])
    ]);

    const days = [];
    for (let i = 6; i >= 0; i--) {
      const d = new Date(); d.setDate(d.getDate() - i);
      days.push(d.toISOString().split('T')[0]);
    }
    const signupMap = Object.fromEntries(dailySignups.map(x => [x._id, x.count]));
    const coinsMap = Object.fromEntries(dailyCoins.map(x => [x._id, x.total]));

    res.json({
      totalUsers,
      totalCoins: totalCoinsAgg[0]?.total || 0,
      activeUsers,
      bannedUsers,
      totalReferrals,
      totalCoinsGiven: totalCoinsGivenAgg[0]?.total || 0,
      dailyJoined,
      weeklyJoined,
      monthlyJoined,
      chart: {
        days,
        signups: days.map(d => signupMap[d] || 0),
        coins: days.map(d => coinsMap[d] || 0)
      }
    });
  } catch (e) {
    console.error('[stats]', e.message);
    res.status(500).json({ error: 'Stats load failed' });
  }
});

router.get('/users', adminAuthJwt, async (req, res) => {
  res.set('Cache-Control', 'no-store, no-cache, must-revalidate');
  res.set('Pragma', 'no-cache');
  const page = parseInt(req.query.page) || 1;
  const limit = parseInt(req.query.limit) || 30;
  const search = req.query.search || '';
  const filter = req.query.filter || 'all';

  let query = {};
  if (search) query.channelName = { $regex: search, $options: 'i' };
  if (filter === 'banned') query.isBanned = true;
  if (filter === 'suspicious') query.$expr = { $gt: ['$subscribersGiven', { $multiply: ['$subscribersReceived', 3] }] };

  const total = await User.countDocuments(query);
  const users = await User.find(query, 'channelName profilePic coins totalEarned totalSpent subscribersGiven subscribersReceived isBanned createdAt lastSeen referralCode referralCount referralEarned isSuspicious adminCodeUsed')
    .sort({ createdAt: -1 })
    .skip((page - 1) * limit)
    .limit(limit);

  // Return array directly for frontend compatibility + pagination meta in headers
  res.set('X-Total-Count', total);
  res.set('X-Page', page);
  res.set('X-Pages', Math.ceil(total / limit));
  res.json(users);
});

router.post('/users/:id/coins', adminAuthJwt, async (req, res) => {
  const { coins, reason } = req.body;
  const user = await User.findById(req.params.id);
  if (!user) return res.status(404).json({ error: 'User not found' });
  user.coins += coins;
  if (user.coins < 0) user.coins = 0;
  await user.save();
  await Transaction.create({ userId: user._id, type: coins > 0 ? 'admin_add' : 'admin_remove', coins, description: reason || 'Admin adjustment' });
  res.json({ success: true, newCoins: user.coins });
});

router.post('/users/:id/ban', adminAuthJwt, async (req, res) => {
  const user = await User.findById(req.params.id);
  if (!user) return res.status(404).json({ error: 'User not found' });
  user.isBanned = !user.isBanned;
  await user.save();
  res.json({ success: true, isBanned: user.isBanned });
});

router.get('/promoted', adminAuthJwt, async (req, res) => {
  const channels = await PromotedChannel.find();
  res.json(channels);
});

router.post('/promoted', adminAuthJwt, async (req, res) => {
  const { channelId, channelName, channelUrl } = req.body;
  const ch = await PromotedChannel.create({ channelId, channelName, channelUrl });
  res.json(ch);
});

router.post('/promoted/:id/toggle', adminAuthJwt, async (req, res) => {
  const ch = await PromotedChannel.findById(req.params.id);
  if (!ch) return res.status(404).json({ error: 'Not found' });
  ch.isActive = !ch.isActive;
  await ch.save();
  res.json({ success: true, isActive: ch.isActive });
});

router.delete('/promoted/:id', adminAuthJwt, async (req, res) => {
  await PromotedChannel.findByIdAndDelete(req.params.id);
  res.json({ success: true });
});

router.get('/coin-requests', adminAuthJwt, async (req, res) => {
  const requests = await CoinRequest.find().populate('userId', 'channelName profilePic').sort({ createdAt: -1 });
  res.json(requests);
});

router.post('/coin-requests/:id/approve', adminAuthJwt, async (req, res) => {
  const request = await CoinRequest.findById(req.params.id);
  if (!request || request.status !== 'pending') return res.status(400).json({ error: 'Invalid request' });
  request.status = 'approved';
  request.adminNote = req.body.adminNote || '';
  request.updatedAt = new Date();
  await request.save();
  const user = await User.findById(request.userId);
  user.coins += request.coins;
  await user.save();
  await Transaction.create({ userId: user._id, type: 'admin_add', coins: request.coins, description: `Coin purchase approved (${request.coins} coins)` });
  res.json({ success: true });
});

router.post('/coin-requests/:id/reject', adminAuthJwt, async (req, res) => {
  const request = await CoinRequest.findById(req.params.id);
  if (!request || request.status !== 'pending') return res.status(400).json({ error: 'Invalid request' });
  request.status = 'rejected';
  request.adminNote = req.body.adminNote || '';
  request.updatedAt = new Date();
  await request.save();
  res.json({ success: true });
});

// --- SUBSCRIBER ORDERS ---
const SubscriberOrder = require('../models/SubscriberOrder');

router.get('/orders', adminAuthJwt, async (req, res) => {
  const orders = await SubscriberOrder.find().populate('userId', 'channelName profilePic channelUrl').sort({ createdAt: -1 });
  res.json(orders);
});

router.post('/orders/:id/status', adminAuthJwt, async (req, res) => {
  const { status, adminNote } = req.body;
  const order = await SubscriberOrder.findById(req.params.id);
  if (!order) return res.status(404).json({ error: 'Not found' });
  order.status = status;
  order.adminNote = adminNote || '';
  order.updatedAt = new Date();
  await order.save();
  if (status === 'completed') {
    await User.findByIdAndUpdate(order.userId, { $inc: { subscribersReceived: order.quantity } });
  }
  res.json({ success: true });
});

// --- GROUPS (admin view all) ---
router.get('/groups', adminAuthJwt, async (req, res) => {
  try {
    const ChatRoom = require('../models/ChatRoom');
    const { adminCode, search } = req.query;
    const filter = { isGroup: true };
    if (adminCode) filter.adminCode = adminCode;
    if (search) filter.name = { $regex: search, $options: 'i' };

    const groups = await ChatRoom.find(filter)
      .populate('createdBy', 'channelName profilePic adminCodeUsed')
      .sort({ createdAt: -1 })
      .limit(200)
      .lean();

    res.json(groups.map(g => ({
      _id: g._id,
      name: g.name,
      memberCount: g.members.length,
      adminCode: g.adminCode || (g.createdBy?.adminCodeUsed) || null,
      createdBy: g.createdBy ? { name: g.createdBy.channelName, pic: g.createdBy.profilePic } : null,
      lastMessage: g.lastMessage,
      lastTime: g.lastTime,
      isBlocked: g.isBlocked || false,
      createdAt: g.createdAt
    })));
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

router.post('/groups/:id/block', adminAuthJwt, async (req, res) => {
  try {
    const ChatRoom = require('../models/ChatRoom');
    const group = await ChatRoom.findById(req.params.id);
    if (!group) return res.status(404).json({ error: 'Not found' });
    group.isBlocked = !group.isBlocked;
    group.blockedBy = group.isBlocked ? 'admin' : null;
    await group.save();
    res.json({ success: true, isBlocked: group.isBlocked });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Admin view group messages
router.get('/groups/:id/messages', adminAuthJwt, async (req, res) => {
  try {
    const ChatMessage = require('../models/ChatMessage');
    const messages = await ChatMessage.find({ roomId: req.params.id })
      .sort({ createdAt: -1 }).limit(100).lean();
    res.json(messages.reverse());
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Admin delete a message from group
router.delete('/groups/:id/messages/:msgId', adminAuthJwt, async (req, res) => {
  try {
    const ChatMessage = require('../models/ChatMessage');
    await ChatMessage.findByIdAndDelete(req.params.msgId);
    const io = req.app.get('io');
    if (io) io.to(`chat_${req.params.id}`).emit('message_deleted', { msgId: req.params.msgId, roomId: req.params.id });
    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Admin delete entire group
router.delete('/groups/:id', adminAuthJwt, async (req, res) => {
  try {
    const ChatRoom = require('../models/ChatRoom');
    const ChatMessage = require('../models/ChatMessage');
    const room = await ChatRoom.findById(req.params.id);
    if (!room) return res.status(404).json({ error: 'Not found' });
    // Notify all members
    const io = req.app.get('io');
    if (io) {
      room.members.forEach(mId => {
        io.to(`user_${mId.toString()}`).emit('group_removed', { roomId: req.params.id, roomName: room.name });
      });
    }
    await ChatMessage.deleteMany({ roomId: req.params.id });
    await ChatRoom.findByIdAndDelete(req.params.id);
    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// --- ADMIN PROFILE ---
const Settings = require('../models/Settings');
const multer = require('multer');
const path = require('path');
const fs = require('fs');

const uploadDir = path.join(__dirname, '../public/admin-assets');
if (!fs.existsSync(uploadDir)) fs.mkdirSync(uploadDir, { recursive: true });

const storage = multer.diskStorage({
  destination: (req, file, cb) => cb(null, uploadDir),
  filename: (req, file, cb) => cb(null, `admin_pic_${Date.now()}${path.extname(file.originalname)}`)
});
const upload = multer({ storage, limits: { fileSize: 2 * 1024 * 1024 }, fileFilter: (req, file, cb) => {
  if (!file.mimetype.startsWith('image/')) return cb(new Error('Only images allowed'));
  cb(null, true);
}});

router.get('/profile', adminAuthJwt, async (req, res) => {
  const nameVal = await Settings.findOne({ key: 'ADMIN_NAME' });
  const picVal = await Settings.findOne({ key: 'ADMIN_PIC' });
  res.json({
    name: nameVal?.value || 'Admin',
    pic: picVal?.value || ''
  });
});

router.post('/profile', adminAuthJwt, upload.single('pic'), async (req, res) => {
  try {
    const { name } = req.body;
    if (name) await Settings.findOneAndUpdate({ key: 'ADMIN_NAME' }, { value: name }, { upsert: true });
    if (req.file) {
      const baseUrl = process.env.BASE_URL || 'https://api.picrypto.in';
      const picUrl = `${baseUrl}/admin-assets/${req.file.filename}`;
      await Settings.findOneAndUpdate({ key: 'ADMIN_PIC' }, { value: picUrl }, { upsert: true });
    }
    const nameVal = await Settings.findOne({ key: 'ADMIN_NAME' });
    const picVal = await Settings.findOne({ key: 'ADMIN_PIC' });
    res.json({ success: true, name: nameVal?.value || 'Admin', pic: picVal?.value || '' });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// --- SETTINGS ---
const SMM_KEYS = ['SMM_API_URL', 'SMM_API_KEY', 'SMM_SERVICE_ID'];

router.get('/settings', adminAuthJwt, async (req, res) => {
  const settings = await Settings.find({ key: { $in: SMM_KEYS } });
  const result = {};
  SMM_KEYS.forEach(k => {
    const found = settings.find(s => s.key === k);
    result[k] = found ? found.value : process.env[k] || '';
  });
  res.json(result);
});

router.post('/settings', adminAuthJwt, async (req, res) => {
  const { SMM_API_URL, SMM_API_KEY, SMM_SERVICE_ID } = req.body;
  const updates = { SMM_API_URL, SMM_API_KEY, SMM_SERVICE_ID };
  for (const [key, value] of Object.entries(updates)) {
    if (value !== undefined) await Settings.findOneAndUpdate({ key }, { value }, { upsert: true });
  }
  res.json({ success: true });
});

// --- NOTICES ---
const Notice = require('../models/Notice');

router.get('/notices', adminAuthJwt, async (req, res) => {
  const notices = await Notice.find().populate('targetUserId', 'channelName').sort({ createdAt: -1 });
  res.json(notices);
});

router.post('/notices', adminAuthJwt, async (req, res) => {
  const { title, message, targetUserId } = req.body;
  const notice = await Notice.create({ title, message, targetUserId: targetUserId || null });
  res.json(notice);
});

// Broadcast to all users
router.post('/notices/broadcast', adminAuthJwt, async (req, res) => {
  const { title, message } = req.body;
  if (!title || !message) return res.status(400).json({ error: 'title and message required' });
  const notice = await Notice.create({ title, message, targetUserId: null, isActive: true });
  res.json({ success: true, notice });
});

router.delete('/notices/:id', adminAuthJwt, async (req, res) => {
  await Notice.findByIdAndDelete(req.params.id);
  res.json({ success: true });
});

// --- SUB-ADMIN / INFLUENCER REFERRAL CODES ---
const AdminCode = require('../models/AdminCode');

// Get all admin codes
router.get('/admin-codes', adminAuthJwt, async (req, res) => {
  const codes = await AdminCode.find().sort({ role: 1, createdAt: 1 });
  const baseUrl = process.env.BASE_URL || 'https://api.picrypto.in';

  // Generate refToken for any codes that don't have one yet
  for (const c of codes) {
    if (!c.refToken) {
      c.refToken = require('crypto').randomBytes(16).toString('hex');
      await c.save();
    }
  }

  const result = codes.map(c => ({
    _id: c._id,
    code: c.code, // admin can see code
    refToken: c.refToken,
    label: c.label,
    role: c.role,
    parentCode: c.parentCode,
    isActive: c.isActive,
    password: c.password ? true : false, // just boolean, not the hash
    smmApiUrl: c.smmApiUrl,
    totalClicks: c.totalClicks,
    totalJoined: c.totalJoined,
    totalCoinsGiven: c.totalCoinsGiven,
    createdAt: c.createdAt,
    link: `${baseUrl}/ref/${c.refToken}` // token-based link, code hidden
  }));
  res.json(result);
});

// Create admin/sub-admin code
router.post('/admin-codes', adminAuthJwt, async (req, res) => {
  const { code, label, role, parentCode } = req.body;
  if (!code || !label) return res.status(400).json({ error: 'code and label required' });

  const upperCode = code.toUpperCase();
  const existing = await AdminCode.findOne({ code: upperCode });
  if (existing) return res.status(400).json({ error: 'Code already exists' });

  // Also check user referral codes
  const userWithCode = await User.findOne({ referralCode: upperCode });
  if (userWithCode) return res.status(400).json({ error: `Code already used by user: ${userWithCode.channelName}` });

  const newCode = await AdminCode.create({
    code: upperCode,
    label,
    role: role || 'sub_admin',
    parentCode: parentCode ? parentCode.toUpperCase() : null
  });

  const baseUrl = process.env.BASE_URL || 'https://api.picrypto.in';
  res.json({ ...newCode.toObject(), link: `${baseUrl}/ref/${upperCode}` });
});

// Set sub-admin password (main admin only) - bcrypt hashed
router.post('/admin-codes/:id/set-password', adminAuthJwt, async (req, res) => {
  const { password } = req.body;
  if (!password) return res.status(400).json({ error: 'Password required' });
  const bcrypt = require('bcryptjs');
  const hashed = await bcrypt.hash(password, 10);
  await AdminCode.findByIdAndUpdate(req.params.id, { password: hashed });
  res.json({ success: true });
});

// Toggle active/inactive
router.post('/admin-codes/:id/toggle', adminAuthJwt, async (req, res) => {
  const c = await AdminCode.findById(req.params.id);
  if (!c) return res.status(404).json({ error: 'Not found' });
  c.isActive = !c.isActive;
  await c.save();
  res.json({ success: true, isActive: c.isActive });
});

// Delete code
router.delete('/admin-codes/:id', adminAuthJwt, async (req, res) => {
  await AdminCode.findByIdAndDelete(req.params.id);
  res.json({ success: true });
});

// Get users who joined via a specific code
router.get('/admin-codes/:code/users', adminAuthJwt, async (req, res) => {
  const code = req.params.code.toUpperCase();
  const users = await User.find({ adminCodeUsed: code }, 'channelName profilePic coins createdAt').sort({ createdAt: -1 });
  res.json(users);
});

// Get referral stats for a specific user code
router.get('/referral-codes/:code/stats', adminAuthJwt, async (req, res) => {
  const code = req.params.code.toUpperCase();
  const referrer = await User.findOne({ referralCode: code });
  if (!referrer) return res.status(404).json({ error: 'Code not found' });
  const referredUsers = await User.find({ referredBy: referrer._id }, 'channelName createdAt coins').sort({ createdAt: -1 });
  res.json({
    code,
    referrerChannel: referrer.channelName,
    referrerProfilePic: referrer.profilePic,
    totalReferrals: referrer.referralCount || 0,
    totalEarned: referrer.referralEarned || 0,
    referredUsers
  });
});

// Get all users with referral stats
router.get('/referral-stats', adminAuthJwt, async (req, res) => {
  const users = await User.find({ referralCount: { $gt: 0 } }, 'channelName profilePic referralCode referralCount referralEarned')
    .sort({ referralCount: -1 }).limit(50);
  res.json(users);
});

// Full referral tree for an admin code
router.get('/referral-tree/:code', adminAuthJwt, async (req, res) => {
  const code = req.params.code.toUpperCase();

  const buildTree = async (parentCode, depth = 0) => {
    if (depth > 4) return []; // max 5 levels deep
    const children = await User.find({ referralParent: parentCode }, 'channelName profilePic referralCode referralCount coins createdAt').lean();
    const result = [];
    for (const child of children) {
      const subChildren = await buildTree(child.referralCode, depth + 1);
      result.push({ ...child, children: subChildren });
    }
    return result;
  };

  const tree = await buildTree(code);
  res.json({ code, tree });
});

// --- PUSH NOTIFICATIONS (FCM V1) ---
let firebaseAdmin = null;
const getFirebaseAdmin = () => {
  if (!firebaseAdmin) {
    const admin = require('firebase-admin');
    if (!admin.apps.length) {
      const serviceAccount = require('../firebase-service-account.json');
      admin.initializeApp({
        credential: admin.credential.cert(serviceAccount)
      });
    }
    firebaseAdmin = admin;
  }
  return firebaseAdmin;
};

router.post('/send-notification', adminAuthJwt, async (req, res) => {
  const { title, body, targetType, userId } = req.body;
  if (!title || !body) return res.status(400).json({ error: 'title and body required' });

  try {
    const admin = getFirebaseAdmin();

    if (targetType === 'user' && userId) {
      const user = await User.findById(userId).select('fcmToken channelName');
      if (!user || !user.fcmToken) return res.status(404).json({ error: 'User has no FCM token' });
      await admin.messaging().send({
        token: user.fcmToken,
        notification: { title, body },
        android: { priority: 'high' }
      });
      res.json({ success: true, sent: 1 });
    } else {
      // All users - send in batches of 500
      const users = await User.find({ fcmToken: { $ne: null }, isBanned: false }).select('fcmToken');
      const tokens = users.map(u => u.fcmToken).filter(Boolean);
      if (tokens.length === 0) return res.json({ success: true, sent: 0, message: 'No users with FCM tokens yet. Install new APK first.' });

      let sent = 0;
      for (let i = 0; i < tokens.length; i += 500) {
        const batch = tokens.slice(i, i + 500);
        const response = await admin.messaging().sendEachForMulticast({
          tokens: batch,
          notification: { title, body },
          android: { priority: 'high' }
        });
        sent += response.successCount;
      }
      res.json({ success: true, sent, total: tokens.length });
    }
  } catch (e) {
    console.error('[FCM]', e.message);
    res.status(500).json({ error: e.message });
  }
});

// --- COMMUNITY CHAT CONTROL ---
router.get('/community/status', adminAuthJwt, async (req, res) => {
  try {
    const io = req.app.get('io');
    const onlineCount = io?.sockets?.adapter?.rooms?.get('community_global')?.size || 0;
    const isOpen = (await Settings.findOne({ key: 'COMMUNITY_OPEN' }))?.value !== 'false';
    const pinned = (await Settings.findOne({ key: 'COMMUNITY_PINNED' }))?.value || '';
    const ChatMessage = require('../models/ChatMessage');
    const totalMessages = await ChatMessage.countDocuments({ roomId: 'community_global' });
    res.json({ isOpen, onlineCount, pinned, totalMessages });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

router.post('/community/toggle', adminAuthJwt, async (req, res) => {
  try {
    const current = (await Settings.findOne({ key: 'COMMUNITY_OPEN' }))?.value;
    const newVal = current === 'false' ? 'true' : 'false';
    await Settings.findOneAndUpdate({ key: 'COMMUNITY_OPEN' }, { value: newVal }, { upsert: true });
    const io = req.app.get('io');
    if (io) io.to('community_global').emit('community_status', { isOpen: newVal !== 'false' });
    res.json({ success: true, isOpen: newVal !== 'false' });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

router.post('/community/pin', adminAuthJwt, async (req, res) => {
  try {
    const { message } = req.body;
    await Settings.findOneAndUpdate({ key: 'COMMUNITY_PINNED' }, { value: message || '' }, { upsert: true });
    const io = req.app.get('io');
    if (io) io.to('community_global').emit('community_pinned', { message: message || '' });
    res.json({ success: true });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

router.post('/community/send', adminAuthJwt, async (req, res) => {
  try {
    const { text } = req.body;
    if (!text) return res.status(400).json({ error: 'text required' });
    const ChatMessage = require('../models/ChatMessage');
    const nameVal = await Settings.findOne({ key: 'ADMIN_NAME' });
    const picVal = await Settings.findOne({ key: 'ADMIN_PIC' });
    const msg = await ChatMessage.create({
      roomId: 'community_global',
      senderId: 'admin',
      senderName: `🛡️ ${nameVal?.value || 'Admin'}`,
      senderPic: picVal?.value || '',
      text,
      isAdmin: true
    });
    const io = req.app.get('io');
    if (io) io.to('community_global').emit('community_message', {
      _id: msg._id, senderId: 'admin', senderName: msg.senderName,
      senderPic: msg.senderPic, text: msg.text, createdAt: msg.createdAt, isAdmin: true
    });
    res.json({ success: true, msg });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

router.get('/community/messages', adminAuthJwt, async (req, res) => {
  try {
    const ChatMessage = require('../models/ChatMessage');
    const msgs = await ChatMessage.find({ roomId: 'community_global' })
      .sort({ createdAt: -1 }).limit(100).lean();
    res.json(msgs.reverse());
  } catch (e) { res.status(500).json({ error: e.message }); }
});

router.delete('/community/message/:id', adminAuthJwt, async (req, res) => {
  try {
    const ChatMessage = require('../models/ChatMessage');
    await ChatMessage.findByIdAndDelete(req.params.id);
    const io = req.app.get('io');
    if (io) io.to('community_global').emit('community_message_deleted', { msgId: req.params.id });
    res.json({ success: true });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

router.post('/community/clear', adminAuthJwt, async (req, res) => {
  try {
    const ChatMessage = require('../models/ChatMessage');
    await ChatMessage.deleteMany({ roomId: 'community_global' });
    const io = req.app.get('io');
    if (io) io.to('community_global').emit('community_cleared', {});
    res.json({ success: true });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// Sub-admin community send (limited power - only text)
router.post('/community/subadmin-send', async (req, res) => {
  try {
    const token = req.headers['x-subadmin-token'];
    if (!token) return res.status(401).json({ error: 'Unauthorized' });
    const jwt = require('jsonwebtoken');
    const decoded = jwt.verify(token, process.env.JWT_SECRET);
    const AdminCode = require('../models/AdminCode');
    const sa = await AdminCode.findById(decoded.saId);
    if (!sa || !sa.isActive) return res.status(401).json({ error: 'Invalid session' });

    const { text } = req.body;
    if (!text) return res.status(400).json({ error: 'text required' });

    // Check community is open
    const isOpen = (await Settings.findOne({ key: 'COMMUNITY_OPEN' }))?.value !== 'false';
    if (!isOpen) return res.status(403).json({ error: 'Community chat band hai' });

    const ChatMessage = require('../models/ChatMessage');
    const msg = await ChatMessage.create({
      roomId: 'community_global',
      senderId: `subadmin_${sa._id}`,
      senderName: `⭐ ${sa.label}`,
      senderPic: '',
      text,
      isAdmin: true
    });
    const io = req.app.get('io');
    if (io) io.to('community_global').emit('community_message', {
      _id: msg._id, senderId: msg.senderId, senderName: msg.senderName,
      senderPic: '', text: msg.text, createdAt: msg.createdAt, isAdmin: true
    });
    res.json({ success: true });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// --- PROMO VIDEOS ---
const PromoVideo = require('../models/PromoVideo');

router.get('/promo-videos', adminAuthJwt, async (req, res) => {
  try {
    const videos = await PromoVideo.find().sort({ priority: -1, createdAt: -1 });
    res.json(videos);
  } catch (e) { res.status(500).json({ error: e.message }); }
});

router.post('/promo-videos', adminAuthJwt, async (req, res) => {
  try {
    const { title, youtubeUrl, channelName, type, coinsReward, priority } = req.body;
    if (!youtubeUrl) return res.status(400).json({ error: 'youtubeUrl required' });
    const watchSeconds = type === 'long' ? 150 : 60;
    const coins = coinsReward || (type === 'long' ? 15 : 5);
    const video = await PromoVideo.create({
      title: title || channelName || 'Promo Video',
      youtubeUrl, channelName: channelName || '',
      type: type || 'short',
      coinsReward: coins,
      watchSeconds,
      priority: priority || 0,
      addedBy: 'admin'
    });
    res.json(video);
  } catch (e) { res.status(500).json({ error: e.message }); }
});

router.post('/promo-videos/:id/toggle', adminAuthJwt, async (req, res) => {
  try {
    const v = await PromoVideo.findById(req.params.id);
    if (!v) return res.status(404).json({ error: 'Not found' });
    v.isActive = !v.isActive;
    await v.save();
    res.json({ success: true, isActive: v.isActive });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

router.delete('/promo-videos/:id', adminAuthJwt, async (req, res) => {
  try {
    await PromoVideo.findByIdAndDelete(req.params.id);
    res.json({ success: true });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

module.exports = router;
