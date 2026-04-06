const express = require('express');
const router = express.Router();
const bcrypt = require('bcryptjs');
const rateLimit = require('express-rate-limit');
const AdminCode = require('../models/AdminCode');
const User = require('../models/User');
const Notice = require('../models/Notice');
const SubscriberOrder = require('../models/SubscriberOrder');

// Rate limiter - 5 login attempts per 15 min
const loginLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 5,
  message: { error: 'Too many login attempts. Try again after 15 minutes.' },
  standardHeaders: true,
  legacyHeaders: false,
});

// Auth middleware - verify JWT session token
const subAdminAuth = async (req, res, next) => {
  const token = req.headers['x-subadmin-token'];
  if (!token) return res.status(401).json({ error: 'Session token required' });

  try {
    const jwt = require('jsonwebtoken');
    const decoded = jwt.verify(token, process.env.JWT_SECRET);
    const adminCode = await AdminCode.findById(decoded.saId);
    if (!adminCode || !adminCode.isActive) return res.status(401).json({ error: 'Invalid session' });
    req.subAdmin = adminCode;
    next();
  } catch (e) {
    return res.status(401).json({ error: 'Session expired, please login again' });
  }
};

// Login - verify credentials, return JWT session token
router.post('/login', loginLimiter, async (req, res) => {
  const { code, password } = req.body;
  if (!code || !password) return res.status(400).json({ error: 'Code and password required' });

  const adminCode = await AdminCode.findOne({ code: code.toUpperCase(), isActive: true });
  if (!adminCode) return res.status(401).json({ error: 'Invalid code' });
  if (!adminCode.password) return res.status(401).json({ error: 'Password not set. Contact main admin.' });

  const valid = await bcrypt.compare(password, adminCode.password);
  if (!valid) return res.status(401).json({ error: 'Wrong password' });

  // Return JWT session token - password never stored client side
  const jwt = require('jsonwebtoken');
  const sessionToken = jwt.sign(
    { saCode: adminCode.code, saId: adminCode._id.toString() },
    process.env.JWT_SECRET,
    { expiresIn: '7d' }
  );

  const baseUrl = process.env.BASE_URL || 'https://api.picrypto.in';
  res.json({
    success: true,
    sessionToken,
    label: adminCode.label,
    role: adminCode.role,
    referralLink: `${baseUrl}/ref/${adminCode.refToken}`
  });
});

// Get all users under this sub-admin (direct + tree)
const getAllSubUsers = async (parentCode) => {
  const direct = await User.find({ referralParent: parentCode }).lean();
  let all = [...direct];
  for (const u of direct) {
    if (u.referralCode) {
      const sub = await getAllSubUsers(u.referralCode);
      all = all.concat(sub);
    }
  }
  return all;
};

// Dashboard stats
router.get('/stats', subAdminAuth, async (req, res) => {
  const code = req.subAdmin.code;
  const allUsers = await getAllSubUsers(code);
  const userIds = allUsers.map(u => u._id);

  const now = new Date();
  const startOfDay = new Date(now); startOfDay.setHours(0, 0, 0, 0);
  const startOfWeek = new Date(now); startOfWeek.setDate(now.getDate() - 6); startOfWeek.setHours(0, 0, 0, 0);
  const startOfMonth = new Date(now); startOfMonth.setDate(1); startOfMonth.setHours(0, 0, 0, 0);

  const dailyJoined = allUsers.filter(u => new Date(u.createdAt) >= startOfDay).length;
  const weeklyJoined = allUsers.filter(u => new Date(u.createdAt) >= startOfWeek).length;
  const monthlyJoined = allUsers.filter(u => new Date(u.createdAt) >= startOfMonth).length;

  const totalCoins = allUsers.reduce((s, u) => s + (u.coins || 0), 0);
  const bannedCount = allUsers.filter(u => u.isBanned).length;

  // Last 7 days signups
  const days = [];
  const signupMap = {};
  for (let i = 6; i >= 0; i--) {
    const d = new Date(); d.setDate(d.getDate() - i);
    const key = d.toISOString().split('T')[0];
    days.push(key);
    signupMap[key] = allUsers.filter(u => u.createdAt?.toISOString?.()?.startsWith(key) || new Date(u.createdAt).toISOString().startsWith(key)).length;
  }

  const baseUrl = process.env.BASE_URL || 'https://api.picrypto.in';
  res.json({
    totalUsers: allUsers.length,
    directUsers: req.subAdmin.totalJoined,
    dailyJoined, weeklyJoined, monthlyJoined,
    totalCoins, bannedCount,
    referralLink: `${baseUrl}/ref/${req.subAdmin.refToken}`,
    shortCode: req.subAdmin.shortCode || null, // 6-char code for app entry
    label: req.subAdmin.label,
    chart: { days, signups: days.map(d => signupMap[d] || 0) }
  });
});

// Get users list
router.get('/users', subAdminAuth, async (req, res) => {
  const allUsers = await getAllSubUsers(req.subAdmin.code);
  res.json(allUsers.map(u => ({
    _id: u._id,
    channelName: u.channelName,
    profilePic: u.profilePic,
    coins: u.coins,
    totalEarned: u.totalEarned,
    referralParent: u.referralParent,
    referralCode: u.referralCode,
    isBanned: u.isBanned,
    createdAt: u.createdAt
  })));
});

// Get orders of sub-users
router.get('/orders', subAdminAuth, async (req, res) => {
  const allUsers = await getAllSubUsers(req.subAdmin.code);
  const userIds = allUsers.map(u => u._id);
  const orders = await SubscriberOrder.find({ userId: { $in: userIds } })
    .populate('userId', 'channelName profilePic')
    .sort({ createdAt: -1 })
    .limit(100);
  res.json(orders);
});

// Send notice to own users only
router.post('/notices', subAdminAuth, async (req, res) => {
  const { title, message } = req.body;
  if (!title || !message) return res.status(400).json({ error: 'title and message required' });

  const allUsers = await getAllSubUsers(req.subAdmin.code);
  // Create one global notice tagged with subadmin code
  const notice = await Notice.create({
    title: `[${req.subAdmin.label}] ${title}`,
    message,
    targetUserId: null,
    subAdminCode: req.subAdmin.code,
    isActive: true
  });
  res.json({ success: true, notice, sentTo: allUsers.length });
});

// Get notices sent by this sub-admin
router.get('/notices', subAdminAuth, async (req, res) => {
  const notices = await Notice.find({ subAdminCode: req.subAdmin.code }).sort({ createdAt: -1 });
  res.json(notices);
});

// ── Groups under this sub-admin ───────────────────────────────
router.get('/groups', subAdminAuth, async (req, res) => {
  try {
    const ChatRoom = require('../models/ChatRoom');
    const code = req.subAdmin.code;

    // Get all users under this sub-admin
    const allUsers = await getAllSubUsers(code);
    const userIds = allUsers.map(u => u._id.toString());

    // Groups where adminCode matches OR creator is one of sub-admin's users
    const groups = await ChatRoom.find({
      isGroup: true,
      $or: [
        { adminCode: code },
        { createdBy: { $in: allUsers.map(u => u._id) } }
      ]
    }).populate('createdBy', 'channelName profilePic').lean();

    const result = groups.map(g => ({
      _id: g._id,
      name: g.name,
      memberCount: g.members.length,
      createdBy: g.createdBy ? { name: g.createdBy.channelName, pic: g.createdBy.profilePic } : null,
      lastMessage: g.lastMessage,
      lastTime: g.lastTime,
      isBlocked: g.isBlocked || false,
      createdAt: g.createdAt
    }));

    res.json(result);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Block / unblock a group
router.post('/groups/:id/block', subAdminAuth, async (req, res) => {
  try {
    const ChatRoom = require('../models/ChatRoom');
    const code = req.subAdmin.code;
    const allUsers = await getAllSubUsers(code);

    const group = await ChatRoom.findById(req.params.id);
    if (!group || !group.isGroup) return res.status(404).json({ error: 'Group not found' });

    // Verify this group belongs to sub-admin's network
    const creatorId = group.createdBy?.toString();
    const isOwned = group.adminCode === code || allUsers.some(u => u._id.toString() === creatorId);
    if (!isOwned) return res.status(403).json({ error: 'Ye group aapke network ka nahi hai' });

    group.isBlocked = !group.isBlocked;
    group.blockedBy = group.isBlocked ? code : null;
    await group.save();

    res.json({ success: true, isBlocked: group.isBlocked });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Get messages of a group (read-only view for sub-admin)
router.get('/groups/:id/messages', subAdminAuth, async (req, res) => {
  try {
    const ChatRoom = require('../models/ChatRoom');
    const ChatMessage = require('../models/ChatMessage');
    const code = req.subAdmin.code;
    const allUsers = await getAllSubUsers(code);

    const group = await ChatRoom.findById(req.params.id);
    if (!group || !group.isGroup) return res.status(404).json({ error: 'Group not found' });

    const creatorId = group.createdBy?.toString();
    const isOwned = group.adminCode === code || allUsers.some(u => u._id.toString() === creatorId);
    if (!isOwned) return res.status(403).json({ error: 'Access denied' });

    const messages = await ChatMessage.find({ roomId: req.params.id })
      .sort({ createdAt: -1 }).limit(50).lean();

    res.json(messages.reverse());
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// Get SMM settings (read-only for sub-admin)
router.get('/settings', subAdminAuth, async (req, res) => {  res.json({
    SMM_API_URL: req.subAdmin.smmApiUrl || '',
    SMM_API_KEY: req.subAdmin.smmApiKey || '',
    SMM_SERVICE_ID: req.subAdmin.smmServiceId || ''
  });
});

// Update SMM settings - saves to this sub-admin's own record
router.post('/settings', subAdminAuth, async (req, res) => {
  const { SMM_API_URL, SMM_API_KEY, SMM_SERVICE_ID } = req.body;
  await require('../models/AdminCode').findByIdAndUpdate(req.subAdmin._id, {
    smmApiUrl: SMM_API_URL || req.subAdmin.smmApiUrl,
    smmApiKey: SMM_API_KEY || req.subAdmin.smmApiKey,
    smmServiceId: SMM_SERVICE_ID || req.subAdmin.smmServiceId
  });
  res.json({ success: true });
});

module.exports = router;
