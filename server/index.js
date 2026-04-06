require('dotenv').config();
const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const mongoose = require('mongoose');
const cors = require('cors');
const axios = require('axios');
const rateLimit = require('express-rate-limit');
const helmet = require('helmet');
const hpp = require('hpp');
const mongoSanitize = require('express-mongo-sanitize');

const app = express();
const server = http.createServer(app);
const io = new Server(server, { cors: { origin: '*' } });

// ── Security Headers ──────────────────────────────────────────
app.use(helmet({
  contentSecurityPolicy: false, // API server hai, CSP disable
  crossOriginEmbedderPolicy: false
}));
app.use(hpp()); // HTTP Parameter Pollution prevent
app.use(mongoSanitize()); // NoSQL injection prevent

app.use(cors());
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true, limit: '10mb' }));
app.set('trust proxy', 1);

// ── Global Rate Limiter (DDoS protection) ────────────────────
const globalLimiter = rateLimit({
  windowMs: 1 * 60 * 1000, // 1 minute
  max: 200, // 200 requests per minute per IP
  message: { error: 'Too many requests, slow down.' },
  standardHeaders: true,
  legacyHeaders: false,
  skip: (req) => req.path.startsWith('/download') // APK download skip
});
app.use(globalLimiter);

// Rate limiter for login endpoints - 5 attempts per 15 minutes
const loginLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 5,
  message: { error: 'Too many login attempts. Try again after 15 minutes.' },
  standardHeaders: true,
  legacyHeaders: false,
});

// Static files - APK download + landing page assets
const path = require('path');
app.use('/download', (req, res, next) => {
  res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate, proxy-revalidate');
  res.setHeader('Pragma', 'no-cache');
  res.setHeader('Expires', '0');
  next();
}, express.static(path.join(__dirname, 'public/download')));
app.use('/demo', express.static(path.join(__dirname, 'public/demo')));
app.use('/admin-assets', express.static(path.join(__dirname, 'public/admin-assets')));
app.use('/uploads', express.static(path.join(__dirname, 'public/uploads')));

// Admin & Subadmin panel HTML + assets
app.use('/admin-assets', express.static(path.join(__dirname, 'public/admin-assets')));

// Legal pages
app.get('/privacy', (_req, res) => res.sendFile(path.join(__dirname, 'public/privacy.html')));
app.get('/terms', (_req, res) => res.sendFile(path.join(__dirname, 'public/terms.html')));

mongoose.connect(process.env.MONGO_URI).then(async () => {
  console.log('MongoDB connected');
  // Auto-create SUB006 for organic users if not exists
  const AdminCode = require('./models/AdminCode');
  await AdminCode.findOneAndUpdate(
    { code: 'SUB006' },
    { $setOnInsert: { code: 'SUB006', label: 'Organic (No Referral)', role: 'sub_admin', parentCode: null, isActive: true } },
    { upsert: true }
  );
});

app.use('/auth', require('./routes/auth'));
// Referral short links - /ref/TOKEN → landing page
app.get('/ref/:token', (req, res) => {
  const path = require('path');
  res.sendFile(path.join(__dirname, 'public/landing.html'));
});

// Group invite links - /join-group/TOKEN → redirect to app or show join page
app.get('/join-group/:token', async (req, res) => {
  try {
    const { token } = req.params;
    const ChatRoom = require('./models/ChatRoom');
    const room = await ChatRoom.findOne({ inviteToken: token, isGroup: true }).select('name pic description members').lean();
    
    if (!room) {
      return res.status(404).send(`
        <html><head><title>Invalid Group Link</title></head>
        <body style="font-family: Arial; text-align: center; padding: 50px;">
          <h2>❌ Invalid Group Link</h2>
          <p>This group invite link is invalid or expired.</p>
          <a href="https://api.picrypto.in/download/YT-Booster.apk">Download YT-Booster App</a>
        </body></html>
      `);
    }

    // Try to open in app first, fallback to web page
    const appLink = `ytsubexchange://join-group/${token}`;
    const downloadLink = 'https://api.picrypto.in/download/YT-Booster.apk';
    
    res.send(`
      <html>
        <head>
          <title>Join ${room.name} - YT-Booster</title>
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <style>
            body { font-family: Arial; text-align: center; padding: 20px; background: #0f0f0f; color: white; }
            .card { background: #1a1a2e; padding: 30px; border-radius: 15px; max-width: 400px; margin: 50px auto; }
            .group-pic { width: 80px; height: 80px; border-radius: 50%; margin: 0 auto 20px; background: #333; }
            .btn { display: inline-block; padding: 12px 24px; margin: 10px; border-radius: 8px; text-decoration: none; font-weight: bold; }
            .btn-primary { background: #7b2ff7; color: white; }
            .btn-secondary { background: #333; color: white; }
          </style>
        </head>
        <body>
          <div class="card">
            <div class="group-pic" style="background-image: url('${room.pic || ''}'); background-size: cover;"></div>
            <h2>👥 ${room.name}</h2>
            <p>${room.description || 'Join this group to chat with members!'}</p>
            <p><strong>${room.members.length} members</strong></p>
            <br>
            <a href="${appLink}" class="btn btn-primary" onclick="setTimeout(() => window.location='${downloadLink}', 2000)">
              📱 Open in YT-Booster App
            </a>
            <br>
            <a href="${downloadLink}" class="btn btn-secondary">
              📥 Download App
            </a>
          </div>
          <script>
            // Auto-redirect to app
            setTimeout(() => {
              window.location = '${appLink}';
            }, 1000);
          </script>
        </body>
      </html>
    `);
  } catch (e) {
    res.status(500).send('Server error');
  }
});

app.use('/user', require('./routes/user'));
app.use('/chat', require('./routes/chat'));
app.use('/ai', require('./routes/ai'));

// Admin & Subadmin panels - secret URL paths from .env
const ADMIN_PATH = process.env.ADMIN_PATH || 'admin';
const SUBADMIN_PATH = process.env.SUBADMIN_PATH || 'subadmin';

// Serve panel HTML on GET / of secret path
app.get(`/${ADMIN_PATH}`, (_req, res) => res.sendFile(path.join(__dirname, 'public/admin/index.html')));
app.get(`/${ADMIN_PATH}/`, (_req, res) => res.sendFile(path.join(__dirname, 'public/admin/index.html')));
app.use(`/${ADMIN_PATH}/assets`, express.static(path.join(__dirname, 'public/admin/assets')));

app.get(`/${SUBADMIN_PATH}`, (_req, res) => res.sendFile(path.join(__dirname, 'public/subadmin/index.html')));
app.get(`/${SUBADMIN_PATH}/`, (_req, res) => res.sendFile(path.join(__dirname, 'public/subadmin/index.html')));

// API routes (must come after HTML serving)
app.use(`/${ADMIN_PATH}`, require('./routes/admin'));
app.use(`/${SUBADMIN_PATH}`, require('./routes/subadmin'));

// Block old /admin and /subadmin paths if secret paths are different
if (ADMIN_PATH !== 'admin') {
  app.use('/admin', (_req, res) => res.status(404).json({ error: 'Not found' }));
}
if (SUBADMIN_PATH !== 'subadmin') {
  app.use('/subadmin', (_req, res) => res.status(404).json({ error: 'Not found' }));
}

const { addToQueue, removeFromQueue, getQueueSize, getQueue } = require('./queue');
const User = require('./models/User');
const Transaction = require('./models/Transaction');

// My channel - always in queue, gives 2 coins
const OWNER_CHANNEL = {
  userId: 'owner_promoted',
  channelId: process.env.OWNER_CHANNEL_ID || 'coder_lobby',
  channelName: process.env.OWNER_CHANNEL_NAME || 'Coder Lobby',
  channelUrl: process.env.OWNER_CHANNEL_URL || 'https://youtube.com/@coder_lobby',
  profilePic: process.env.OWNER_CHANNEL_PIC || '',
};

// Auto-fetch owner channel profile pic if not set or placeholder
async function fetchOwnerProfilePic() {
  if (OWNER_CHANNEL.profilePic && !OWNER_CHANNEL.profilePic.includes('YOUR_PROFILE_PIC')) return;
  try {
    const res = await axios.get(`https://www.googleapis.com/youtube/v3/channels`, {
      params: {
        part: 'snippet',
        id: OWNER_CHANNEL.channelId,
        key: process.env.YOUTUBE_API_KEY || ''
      }
    });
    const pic = res.data?.items?.[0]?.snippet?.thumbnails?.default?.url;
    if (pic) {
      OWNER_CHANNEL.profilePic = pic;
      console.log('Owner profile pic fetched:', pic);
    }
  } catch (e) {
    // YouTube API key not set or quota exceeded - use fallback
    console.log('Could not fetch owner profile pic:', e.message);
  }
}
fetchOwnerProfilePic();

// Expose owner channel info to app
app.get('/promoted-channel', (_req, res) => {
  res.json({ ...OWNER_CHANNEL, coinsReward: 5 });
});

// App version check endpoint
app.get('/version', (_req, res) => {
  res.json({
    latestVersion: 10,
    versionName: "2.0",
    forceUpdate: false,
    downloadUrl: "https://api.picrypto.in/download/YT-Booster.apk",
    changelog: "Bug fixes aur performance improvements"
  });
});

app.set('io', io);

// Auto-delete disappearing messages every minute
setInterval(async () => {
  try {
    const ChatMessage = require('./models/ChatMessage');
    const now = new Date();
    const expired = await ChatMessage.find({ disappearsAt: { $ne: null, $lte: now } }).select('_id roomId').lean();
    if (expired.length > 0) {
      const ids = expired.map(m => m._id);
      await ChatMessage.deleteMany({ _id: { $in: ids } });
      // Notify rooms
      const roomIds = [...new Set(expired.map(m => m.roomId.toString()))];
      roomIds.forEach(roomId => {
        io.to(`chat_${roomId}`).emit('messages_disappeared', { roomId, msgIds: expired.filter(m => m.roomId.toString() === roomId).map(m => m._id.toString()) });
      });
    }
  } catch (e) { /* silent */ }
}, 60 * 1000);

io.on('connection', (socket) => {
  console.log('Socket connected:', socket.id);

  // User apna personal room join kare (notifications ke liye)
  socket.on('join_user_room', async ({ token }) => {
    try {
      const jwt = require('jsonwebtoken');
      const decoded = jwt.verify(token, process.env.JWT_SECRET);
      socket.join(`user_${decoded.userId}`);
      socket.userId = decoded.userId;
      console.log('[join_user_room] userId:', decoded.userId, 'socketId:', socket.id);

      // Sirf DM partners ko notify karo (not broadcast to all)
      const ChatRoom = require('./models/ChatRoom');
      const dmRooms = await ChatRoom.find({
        isGroup: false,
        members: decoded.userId
      }).select('members').lean();

      const partnerIds = new Set();
      dmRooms.forEach(room => {
        room.members.forEach(m => {
          const mid = m.toString();
          if (mid !== decoded.userId) partnerIds.add(mid);
        });
      });

      // Notify each partner that this user is online
      partnerIds.forEach(partnerId => {
        io.to(`user_${partnerId}`).emit('user_status', { userId: decoded.userId, online: true });
      });

      // Also tell this user which of their partners are currently online
      const onlinePartners = [];
      partnerIds.forEach(partnerId => {
        const room = io.sockets.adapter.rooms.get(`user_${partnerId}`);
        if (room && room.size > 0) onlinePartners.push(partnerId);
      });
      if (onlinePartners.length > 0) {
        socket.emit('online_partners', { onlineUsers: onlinePartners });
      }
    } catch (e) { console.log('[join_user_room] error:', e.message); }
  });

  socket.on('join_queue', async ({ token }) => {
    try {
      const jwt = require('jsonwebtoken');
      const decoded = jwt.verify(token, process.env.JWT_SECRET);
      const user = await User.findById(decoded.userId);
      if (!user || user.isBanned) return;

      user.inQueue = true;
      await user.save();

      addToQueue(user._id.toString(), socket.id, user.youtubeId, user.accessToken);
      socket.userId = user._id.toString();

      // Get users this person already subscribed to (last 2 days)
      const recentSubs = await Transaction.find({
        userId: user._id,
        type: { $in: ['earn', 'earn_owner'] },
        createdAt: { $gte: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000) }
      }).select('relatedUserId description');
      const subscribedIds = new Set(recentSubs.map(t => t.relatedUserId?.toString()).filter(Boolean));

      // Build list of up to 9 other users from queue - exclude already subscribed
      const queueList = getQueue()
        .filter(u => u.userId !== user._id.toString() && !subscribedIds.has(u.userId))
        .slice(0, 9);

      // Fetch user details for each queue entry
      const channelList = await Promise.all(
        queueList.map(async (u) => {
          const u2 = await User.findById(u.userId, 'channelName channelUrl profilePic youtubeId');
          if (!u2) return null;
          return {
            channelId: u2.youtubeId,
            channelName: u2.channelName,
            channelUrl: u2.channelUrl,
            profilePic: u2.profilePic,
            matchId: u.userId,
            coinsReward: 1
          };
        })
      );

      // Always add owner channel (2 coins) - sirf tab dikhao jab 24h mein claim nahi kiya
      const ownerAlreadyClaimed = await Transaction.findOne({
        userId: user._id,
        type: 'earn_owner',
        createdAt: { $gte: new Date(Date.now() - 24 * 60 * 60 * 1000) }
      });

      const ownerEntry = ownerAlreadyClaimed ? null : {
        channelId: OWNER_CHANNEL.channelId,
        channelName: OWNER_CHANNEL.channelName,
        channelUrl: OWNER_CHANNEL.channelUrl,
        profilePic: OWNER_CHANNEL.profilePic,
        matchId: 'owner_promoted',
        coinsReward: 5
      };

      // Shuffle + mix owner channel randomly in list
      const allEntries = [...(ownerEntry ? [ownerEntry] : []), ...channelList.filter(Boolean)];
      for (let i = allEntries.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1));
        [allEntries[i], allEntries[j]] = [allEntries[j], allEntries[i]];
      }

      socket.emit('queue_list', { channels: allEntries, queueSize: getQueueSize() });

      // Also notify existing queue users to refresh their list
      io.emit('queue_update', { queueSize: getQueueSize() });

    } catch (e) {
      console.error(e.message);
    }
  });

  socket.on('confirm_subscribe', async ({ token, matchId }) => {
    try {
      const jwt = require('jsonwebtoken');
      const decoded = jwt.verify(token, process.env.JWT_SECRET);
      const user = await User.findById(decoded.userId);
      if (!user) return;

      // Owner channel - 2 coins, once per day
      if (matchId === 'owner_promoted') {
        const existing = await Transaction.findOne({
          userId: user._id,
          type: 'earn_owner',
          createdAt: { $gte: new Date(Date.now() - 24 * 60 * 60 * 1000) }
        });
        if (existing) {
          socket.emit('coins_earned', { coins: user.coins, earned: 0 });
          return;
        }
        user.coins += 5;
        user.totalEarned += 5;
        user.subscribersGiven += 1;
        user.inQueue = false;
        await user.save();
        await Transaction.create({
          userId: user._id,
          type: 'earn_owner',
          coins: 5,
          description: `Subscribed ${OWNER_CHANNEL.channelName} (+5 coins)`
        });
        socket.emit('coins_earned', { coins: user.coins, earned: 5 });
        return;
      }

      // Normal match
      const matchedUser = await User.findById(matchId);
      if (!matchedUser) return;

      // Check already claimed recently (last 2 days - prevent duplicate)
      const existing = await Transaction.findOne({
        userId: user._id,
        relatedUserId: matchedUser._id,
        type: 'earn',
        createdAt: { $gte: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000) }
      });
      if (existing) {
        socket.emit('already_subscribed', { channelName: matchedUser.channelName });
        return;
      }

      // Give coin directly - trust user (YouTube API unreliable with OAuth tokens)
      user.coins += 1;
      user.totalEarned += 1;
      user.subscribersGiven += 1;
      user.inQueue = false;
      await user.save();

      // Anti-fraud: check rapid subscription (10+ in last hour)
      const oneHourAgo = new Date(Date.now() - 60 * 60 * 1000);
      const recentCount = await Transaction.countDocuments({
        userId: user._id,
        type: 'earn',
        createdAt: { $gte: oneHourAgo }
      });
      if (recentCount >= 10 && !user.isSuspicious) {
        await User.findByIdAndUpdate(user._id, { isSuspicious: true, suspiciousReason: `Rapid subs: ${recentCount} in 1 hour` });
        console.warn(`[FRAUD] Rapid subscription: ${user.channelName} - ${recentCount} subs in 1 hour`);
      }

      await User.findByIdAndUpdate(matchId, { $inc: { subscribersReceived: 1 } });
      await Transaction.create({
        userId: user._id,
        type: 'earn',
        coins: 1,
        description: `Subscribed ${matchedUser.channelName}`,
        relatedUserId: matchedUser._id
      });

      socket.emit('coins_earned', { coins: user.coins, earned: 1 });
      const matchedSocket = [...io.sockets.sockets.values()].find(s => s.userId === matchId);
      if (matchedSocket) matchedSocket.emit('partner_confirmed', { channelName: user.channelName });
    } catch (e) {
      console.error('confirm_subscribe error:', e.message);
    }
  });

  socket.on('leave_queue', async () => {
    if (socket.userId) {
      removeFromQueue(socket.userId);
      await User.findByIdAndUpdate(socket.userId, { inQueue: false });
    }
  });

  socket.on('disconnect', async () => {
    if (socket.userId) {
      removeFromQueue(socket.userId);
      await User.findByIdAndUpdate(socket.userId, { inQueue: false });

      // Voice chat cleanup — remove from all voice rooms
      if (io.voiceChats) {
        for (const [roomId, participants] of io.voiceChats.entries()) {
          if (participants.has(socket.userId)) {
            participants.delete(socket.userId);
            io.to(`voice_${roomId}`).emit('voice_chat_user_left', { roomId, userId: socket.userId, socketId: socket.id });
            if (participants.size === 0) io.voiceChats.delete(roomId);
          }
        }
      }

      // Check if user has any other active sockets — agar nahi toh offline broadcast karo
      const userRoom = io.sockets.adapter.rooms.get(`user_${socket.userId}`);
      if (!userRoom || userRoom.size === 0) {
        // Sirf DM partners ko notify karo
        const ChatRoom = require('./models/ChatRoom');
        try {
          const dmRooms = await ChatRoom.find({
            isGroup: false,
            members: socket.userId
          }).select('members').lean();

          const partnerIds = new Set();
          dmRooms.forEach(room => {
            room.members.forEach(m => {
              const mid = m.toString();
              if (mid !== socket.userId) partnerIds.add(mid);
            });
          });

          partnerIds.forEach(partnerId => {
            io.to(`user_${partnerId}`).emit('user_status', { userId: socket.userId, online: false });
          });
        } catch (e) { /* silent */ }
      }
    }
    // Update community online count if user was in community
    const communityRoom = io.sockets.adapter.rooms.get('community_global');
    const communityCount = communityRoom?.size || 0;
    io.to('community_global').emit('community_online_count', { count: communityCount });
  });

  // ── Chat Socket Events ────────────────────────────────────
  socket.on('join_chat_room', ({ roomId, token }) => {
    if (roomId) socket.join(`chat_${roomId}`);
    console.log('[join_chat_room] roomId:', roomId, 'hasToken:', !!token, 'socketId:', socket.id);
    // token se userId set karo agar abhi tak set nahi hua
    if (!socket.userId && token) {
      try {
        const jwt = require('jsonwebtoken');
        const decoded = jwt.verify(token, process.env.JWT_SECRET);
        socket.userId = decoded.userId;
        socket.join(`user_${decoded.userId}`);
        console.log('[join_chat_room] userId set:', decoded.userId);
      } catch (e) { console.log('[join_chat_room] token error:', e.message); }
    }
  });

  socket.on('leave_chat_room', ({ roomId }) => {
    if (roomId) socket.leave(`chat_${roomId}`);
  });

  // ── Enhanced Message Storage with Offline Support ────────
  socket.on('chat_message', async ({ roomId, text, replyTo, token }) => {
    try {
      if (!roomId || !text) { console.log('[chat_message] missing roomId or text'); return; }

      // userId resolve karo — socket se ya token se
      let userId = socket.userId;
      if (!userId && token) {
        try {
          const jwt = require('jsonwebtoken');
          const decoded = jwt.verify(token, process.env.JWT_SECRET);
          userId = decoded.userId;
          socket.userId = userId;
        } catch (e) { console.log('[chat_message] invalid token:', e.message); return; }
      }
      if (!userId) { console.log('[chat_message] no userId, socketId:', socket.id); return; }

      console.log('[chat_message] userId:', userId, 'roomId:', roomId, 'text:', text.substring(0,30));

      const ChatRoom = require('./models/ChatRoom');
      const ChatMessage = require('./models/ChatMessage');

      const [room, user] = await Promise.all([
        ChatRoom.findById(roomId).select('members isGroup').lean(),
        User.findById(userId).select('channelName profilePic').lean()
      ]);
      if (!room || !user) return;
      if (!room.members.some(m => m.toString() === userId)) return;

      // Block check — DM room mein message block karo
      if (room.members.length === 2) {
        const otherId = room.members.find(m => m.toString() !== userId)?.toString();
        if (otherId) {
          const [me, other] = await Promise.all([
            User.findById(userId).select('blockedUsers').lean(),
            User.findById(otherId).select('blockedUsers').lean()
          ]);
          const iBlockedThem = me?.blockedUsers?.some(id => id.toString() === otherId);
          const theyBlockedMe = other?.blockedUsers?.some(id => id.toString() === userId);
          if (iBlockedThem || theyBlockedMe) {
            socket.emit('message_blocked', { roomId });
            return;
          }
        }
      }

      // Slow mode check — group mein
      if (room.isGroup && room.slowMode > 0) {
        const lastMsg = await ChatMessage.findOne({ roomId, senderId: userId }).sort({ createdAt: -1 }).select('createdAt').lean();
        if (lastMsg) {
          const elapsed = (Date.now() - new Date(lastMsg.createdAt).getTime()) / 1000;
          if (elapsed < room.slowMode) {
            const wait = Math.ceil(room.slowMode - elapsed);
            socket.emit('slow_mode_wait', { roomId, waitSeconds: wait });
            return;
          }
        }
      }

      const msgId = new (require('mongoose').Types.ObjectId)();
      const now = new Date();

      const msgPayload = {
        _id: msgId,
        roomId,
        senderId: userId,
        senderName: user.channelName,
        senderPic: user.profilePic,
        text,
        createdAt: now,
        replyTo: null
      };

      // Resolve replyTo before broadcast so clients get it immediately
      if (replyTo) {
        try {
          const ChatMessage = require('./models/ChatMessage');
          const replyMsg = await ChatMessage.findById(replyTo).select('text senderName').lean();
          if (replyMsg) msgPayload.replyTo = { msgId: replyMsg._id, text: replyMsg.text, senderName: replyMsg.senderName };
        } catch (e) { /* skip if not found */ }
      }

      // 1. ALWAYS save to database first (for offline users)
      const disappearsAt = room.disappearingSeconds > 0 ? new Date(Date.now() + room.disappearingSeconds * 1000) : null;
      await ChatMessage.create({ 
        _id: msgId, roomId, senderId: userId, senderName: user.channelName, 
        senderPic: user.profilePic, text, replyTo: msgPayload.replyTo, 
        createdAt: now, disappearsAt 
      });
      if (disappearsAt) msgPayload.disappearsAt = disappearsAt;
      await ChatRoom.findByIdAndUpdate(roomId, { lastMessage: text, lastTime: now });

      // 2. Broadcast to chat room (jo log room join kiye hain)
      io.to(`chat_${roomId}`).emit('chat_message', msgPayload);

      // 3. Har member ke personal user room pe bhejo — SIRF unhe jo chat_room mein nahi hain
      const chatRoomSockets = io.sockets.adapter.rooms.get(`chat_${roomId}`) || new Set();
      const onlineMembers = new Set();
      const offlineMembers = [];

      for (const memberId of room.members) {
        const mId = memberId.toString();
        if (mId === userId) continue; // sender ko mat bhejo
        
        // Check if member is online
        const memberUserRoom = io.sockets.adapter.rooms.get(`user_${mId}`);
        if (memberUserRoom && memberUserRoom.size > 0) {
          onlineMembers.add(mId);
          // Check if member is in chat room
          let isInChatRoom = false;
          for (const sid of memberUserRoom) {
            if (chatRoomSockets.has(sid)) { isInChatRoom = true; break; }
          }
          if (!isInChatRoom) {
            io.to(`user_${mId}`).emit('chat_message_notify', msgPayload);
          }
        } else {
          offlineMembers.push(mId);
        }
      }

      // 4. FCM push for ALL offline users (guaranteed delivery)
      if (offlineMembers.length > 0) {
        try {
          const admin = require('./firebase-admin');
          if (admin) {
            const offlineUsers = await User.find({
              _id: { $in: offlineMembers },
              fcmToken: { $exists: true, $ne: null, $ne: '' }
            }).select('fcmToken _id').lean();

            for (const member of offlineUsers) {
              await admin.messaging().send({
                token: member.fcmToken,
                notification: { 
                  title: user.channelName, 
                  body: text.length > 100 ? text.substring(0, 100) + '...' : text 
                },
                data: { 
                  roomId, senderId: userId, type: 'chat_message',
                  messageId: msgId.toString(), timestamp: now.toISOString()
                },
                android: { 
                  priority: 'high', 
                  notification: { 
                    channelId: 'ytbooster_channel', 
                    sound: 'default',
                    tag: `chat_${roomId}` // Group notifications by room
                  } 
                }
              }).catch(async e => {
                console.log('[FCM] send error:', e.message);
                // Invalid/expired token — DB se clean karo
                if (e.message && (e.message.includes('not found') || e.message.includes('invalid') || e.message.includes('Unregistered'))) {
                  await User.findByIdAndUpdate(member._id, { $unset: { fcmToken: 1 } });
                  console.log('[FCM] Cleaned invalid token for user:', member._id);
                }
              });
            }
          }
        } catch (fcmErr) {
          console.error('[FCM] Error sending notifications:', fcmErr.message);
        }
      }

    } catch (e) {
      console.error('chat_message error:', e.message);
    }
  });

  socket.on('chat_typing', ({ roomId, typing }) => {
    if (!socket.userId || !roomId) return;
    socket.to(`chat_${roomId}`).emit('chat_typing', { roomId, typing, userId: socket.userId });
  });

  // ── Enhanced Call System with Offline Notifications ──────
  socket.on('call_start', async ({ roomId, callType, token }) => {
    if (!socket.userId || !roomId) return;
    try {
      const ChatRoom = require('./models/ChatRoom');
      const room = await ChatRoom.findById(roomId).select('members isGroup name').lean();
      if (!room) return;

      // Caller ka naam fetch karo
      const caller = await User.findById(socket.userId).select('channelName').lean();
      const callerName = caller?.channelName || '';

      const payload = {
        roomId, callType,
        callerId: socket.userId,
        callerSocketId: socket.id,
        callerName
      };

      // Store call in database for offline users
      const CallLog = require('./models/CallLog');
      const callLogId = new (require('mongoose').Types.ObjectId)();
      await CallLog.create({
        _id: callLogId,
        roomId,
        callerId: socket.userId,
        callerName,
        callType,
        participants: room.members,
        status: 'ringing',
        startTime: new Date()
      });

      if (room.isGroup) {
        // Group call - notify all members
        socket.to(`chat_${roomId}`).emit('call_incoming', payload);
        
        // FCM for ALL group members (online + offline)
        try {
          const admin = require('./firebase-admin');
          if (admin) {
            const allMembers = await User.find({
              _id: { $in: room.members.filter(m => m.toString() !== socket.userId) },
              fcmToken: { $exists: true, $ne: null, $ne: '' }
            }).select('fcmToken _id').lean();
            
            for (const member of allMembers) {
              const userRoom = io.sockets.adapter.rooms.get(`user_${member._id.toString()}`);
              const isOnline = userRoom && userRoom.size > 0;
              
              // Send FCM to both online and offline users for calls
              await admin.messaging().send({
                token: member.fcmToken,
                notification: { 
                  title: `📞 ${callerName}`, 
                  body: `${callType === 'video' ? 'Video' : 'Voice'} call aa raha hai - ${room.name || 'Group'}` 
                },
                data: { 
                  roomId, callerId: socket.userId, type: 'incoming_call', callType,
                  callLogId: callLogId.toString(), roomName: room.name || 'Group',
                  isOnline: isOnline.toString()
                },
                android: { 
                  priority: 'high', 
                  notification: { 
                    channelId: 'ytbooster_call_channel', 
                    sound: 'default',
                    tag: `call_${roomId}`,
                    actions: [
                      { title: 'Accept', action: 'ACCEPT_CALL' },
                      { title: 'Decline', action: 'DECLINE_CALL' }
                    ]
                  } 
                }
              }).catch(() => {});
            }
          }
        } catch (e) {
          console.error('[Call FCM] Group call notification error:', e.message);
        }
      } else {
        // DM call - notify partner
        const partnerId = room.members.find(m => m.toString() !== socket.userId)?.toString();
        if (partnerId) {
          io.to(`user_${partnerId}`).emit('call_incoming', payload);
          
          // FCM for partner (online + offline)
          try {
            const admin = require('./firebase-admin');
            if (admin) {
              const partnerUser = await User.findById(partnerId).select('fcmToken').lean();
              if (partnerUser?.fcmToken) {
                const partnerRoom = io.sockets.adapter.rooms.get(`user_${partnerId}`);
                const isOnline = partnerRoom && partnerRoom.size > 0;
                
                await admin.messaging().send({
                  token: partnerUser.fcmToken,
                  notification: { 
                    title: `📞 ${callerName}`, 
                    body: `${callType === 'video' ? 'Video' : 'Voice'} call aa raha hai` 
                  },
                  data: { 
                    roomId, callerId: socket.userId, type: 'incoming_call', callType,
                    callLogId: callLogId.toString(), roomName: callerName,
                    isOnline: isOnline.toString()
                  },
                  android: { 
                    priority: 'high', 
                    notification: { 
                      channelId: 'ytbooster_call_channel', 
                      sound: 'default',
                      tag: `call_${roomId}`,
                      actions: [
                        { title: 'Accept', action: 'ACCEPT_CALL' },
                        { title: 'Decline', action: 'DECLINE_CALL' }
                      ]
                    } 
                  }
                }).catch(() => {});
              }
            }
          } catch (e) {
            console.error('[Call FCM] DM call notification error:', e.message);
          }
        }
      }
    } catch (e) {
      console.error('call_start error:', e.message);
    }
  });

  socket.on('call_join', ({ roomId, callerId }) => {
    // Update call log
    const CallLog = require('./models/CallLog');
    CallLog.findOneAndUpdate(
      { roomId, callerId, status: 'ringing' },
      { status: 'connected', connectTime: new Date() }
    ).catch(() => {});
    
    // Notify caller that someone joined
    socket.to(`chat_${roomId}`).emit('call_user_joined', {
      userId: socket.userId, socketId: socket.id
    });
  });

  socket.on('call_end', ({ roomId }) => {
    // Update call log
    const CallLog = require('./models/CallLog');
    CallLog.findOneAndUpdate(
      { roomId, status: { $in: ['ringing', 'connected'] } },
      { status: 'ended', endTime: new Date() }
    ).catch(() => {});
    
    socket.to(`chat_${roomId}`).emit('call_ended', { endedBy: socket.userId });
  });

  // Voice → Video upgrade request
  socket.on('call_upgrade', async ({ roomId, newType }) => {
    if (!socket.userId || !roomId) return;
    try {
      const ChatRoom = require('./models/ChatRoom');
      const room = await ChatRoom.findById(roomId).select('members isGroup').lean();
      if (!room) return;
      if (room.isGroup) {
        socket.to(`chat_${roomId}`).emit('call_upgrade', { roomId, newType, fromSocketId: socket.id });
      } else {
        const partnerId = room.members.find(m => m.toString() !== socket.userId)?.toString();
        if (partnerId) io.to(`user_${partnerId}`).emit('call_upgrade', { roomId, newType, fromSocketId: socket.id });
      }
    } catch (e) { }
  });

  // WebRTC offer/answer/ice exchange
  socket.on('webrtc_offer', ({ targetSocketId, offer, roomId }) => {
    socket.to(targetSocketId).emit('webrtc_offer', { offer, fromSocketId: socket.id, roomId });
  });

  socket.on('webrtc_answer', ({ targetSocketId, answer }) => {
    socket.to(targetSocketId).emit('webrtc_answer', { answer, fromSocketId: socket.id });
  });

  socket.on('webrtc_ice', ({ targetSocketId, candidate }) => {
    socket.to(targetSocketId).emit('webrtc_ice', { candidate, fromSocketId: socket.id });
  });

  // ── Group Voice Chat (Telegram style) ───────────────────
  // Active voice chat rooms: roomId -> Map<userId, {socketId, name, pic, muted}>
  // (stored on io object so all sockets can access)
  if (!io.voiceChats) io.voiceChats = new Map();

  socket.on('voice_chat_join', async ({ roomId, token }) => {
    try {
      if (!roomId) return;
      let userId = socket.userId;
      if (!userId && token) {
        const jwt = require('jsonwebtoken');
        const decoded = jwt.verify(token, process.env.JWT_SECRET);
        userId = decoded.userId;
        socket.userId = userId;
      }
      if (!userId) return;

      const ChatRoom = require('./models/ChatRoom');
      const room = await ChatRoom.findById(roomId).select('members isGroup admins subAdmins').lean();
      if (!room || !room.members.some(m => m.toString() === userId)) return;

      // Permission check: only owner/admin or subadmin with canStartVoiceChat
      if (room.isGroup) {
        const isAdmin = room.admins?.some(a => a.toString() === userId);
        const subAdmin = room.subAdmins?.find(s => s.userId.toString() === userId);
        const canStart = isAdmin || subAdmin?.canStartVoiceChat;
        if (!canStart) {
          socket.emit('voice_chat_error', { message: 'Sirf Owner ya permitted Admin voice chat start kar sakte hain' });
          return;
        }
      }

      const user = await User.findById(userId).select('channelName profilePic').lean();
      if (!user) return;

      socket.join(`voice_${roomId}`);

      if (!io.voiceChats.has(roomId)) io.voiceChats.set(roomId, new Map());
      const participants = io.voiceChats.get(roomId);

      const myInfo = { socketId: socket.id, userId, name: user.channelName, pic: user.profilePic || '', muted: false };
      participants.set(userId, myInfo);

      // Tell joiner about existing participants
      const existing = Array.from(participants.values()).filter(p => p.userId !== userId);
      socket.emit('voice_chat_participants', { roomId, participants: existing });

      // Tell existing participants about new joiner
      socket.to(`voice_${roomId}`).emit('voice_chat_user_joined', { roomId, ...myInfo });

      console.log(`[voice_chat] ${user.channelName} joined room ${roomId}, total: ${participants.size}`);
    } catch (e) { console.log('[voice_chat_join] error:', e.message); }
  });

  socket.on('voice_chat_leave', ({ roomId }) => {
    if (!roomId || !socket.userId) return;
    socket.leave(`voice_${roomId}`);
    if (io.voiceChats.has(roomId)) {
      io.voiceChats.get(roomId).delete(socket.userId);
      if (io.voiceChats.get(roomId).size === 0) io.voiceChats.delete(roomId);
    }
    io.to(`voice_${roomId}`).emit('voice_chat_user_left', { roomId, userId: socket.userId, socketId: socket.id });
    console.log(`[voice_chat] ${socket.userId} left room ${roomId}`);
  });

  socket.on('voice_chat_mute', ({ roomId, muted }) => {
    if (!roomId || !socket.userId) return;
    if (io.voiceChats.has(roomId) && io.voiceChats.get(roomId).has(socket.userId)) {
      io.voiceChats.get(roomId).get(socket.userId).muted = muted;
    }
    io.to(`voice_${roomId}`).emit('voice_chat_mute_changed', { roomId, userId: socket.userId, socketId: socket.id, muted });
  });

  // WebRTC signaling for voice chat (peer-to-peer mesh)
  socket.on('voice_chat_offer', ({ targetSocketId, offer, roomId }) => {
    socket.to(targetSocketId).emit('voice_chat_offer', { offer, fromSocketId: socket.id, fromUserId: socket.userId, roomId });
  });

  socket.on('voice_chat_answer', ({ targetSocketId, answer, roomId }) => {
    socket.to(targetSocketId).emit('voice_chat_answer', { answer, fromSocketId: socket.id, roomId });
  });

  socket.on('voice_chat_ice', ({ targetSocketId, candidate, roomId }) => {
    socket.to(targetSocketId).emit('voice_chat_ice', { candidate, fromSocketId: socket.id, roomId });
  });

  // Admin voice chat controls
  socket.on('voice_chat_admin_mute', async ({ roomId, targetUserId, muted }) => {
    if (!roomId || !socket.userId || !targetUserId) return;
    try {
      const ChatRoom = require('./models/ChatRoom');
      const room = await ChatRoom.findById(roomId).select('admins').lean();
      if (!room || !room.admins?.some(a => a.toString() === socket.userId)) return; // only admin
      if (io.voiceChats.has(roomId) && io.voiceChats.get(roomId).has(targetUserId)) {
        io.voiceChats.get(roomId).get(targetUserId).muted = muted;
      }
      // Tell target user they were muted by admin
      const targetEntry = io.voiceChats.get(roomId)?.get(targetUserId);
      if (targetEntry) {
        io.to(targetEntry.socketId).emit('voice_chat_admin_muted_you', { roomId, muted, byAdmin: socket.userId });
      }
      // Broadcast mute change to all
      io.to(`voice_${roomId}`).emit('voice_chat_mute_changed', { roomId, userId: targetUserId, socketId: targetEntry?.socketId || '', muted });
    } catch (e) { }
  });

  socket.on('voice_chat_admin_kick', async ({ roomId, targetUserId }) => {
    if (!roomId || !socket.userId || !targetUserId) return;
    try {
      const ChatRoom = require('./models/ChatRoom');
      const room = await ChatRoom.findById(roomId).select('admins').lean();
      if (!room || !room.admins?.some(a => a.toString() === socket.userId)) return;
      const targetEntry = io.voiceChats.get(roomId)?.get(targetUserId);
      if (targetEntry) {
        io.to(targetEntry.socketId).emit('voice_chat_kicked', { roomId });
        io.voiceChats.get(roomId)?.delete(targetUserId);
        io.to(`voice_${roomId}`).emit('voice_chat_user_left', { roomId, userId: targetUserId, socketId: targetEntry.socketId });
      }
    } catch (e) { }
  });

  socket.on('voice_chat_admin_end', async ({ roomId }) => {
    if (!roomId || !socket.userId) return;
    try {
      const ChatRoom = require('./models/ChatRoom');
      const room = await ChatRoom.findById(roomId).select('admins').lean();
      if (!room || !room.admins?.some(a => a.toString() === socket.userId)) return;
      io.to(`voice_${roomId}`).emit('voice_chat_ended_by_admin', { roomId });
      io.voiceChats.delete(roomId);
    } catch (e) { }
  });

  socket.on('voice_chat_get_participants', ({ roomId }) => {
    if (!roomId) return;
    const participants = io.voiceChats.has(roomId) ? Array.from(io.voiceChats.get(roomId).values()) : [];
    socket.emit('voice_chat_participants', { roomId, participants });
  });

  // Raise hand in voice chat
  socket.on('voice_chat_raise_hand', ({ roomId, userId, raised }) => {
    if (!roomId) return;
    io.to(`voice_${roomId}`).emit('voice_chat_hand_raised', { roomId, userId, raised });
  });

  // Screen share in voice chat
  socket.on('voice_chat_screen_share', ({ roomId, userId, sharing }) => {
    if (!roomId) return;
    socket.to(`voice_${roomId}`).emit('voice_chat_screen_share', { roomId, userId, sharing });
  });

  // ── Community Chat Socket ─────────────────────────────────
  socket.on('join_community', async () => {
    socket.join('community_global');
    // Send current status to joiner
    try {
      const Settings = require('./models/Settings');
      const isOpen = (await Settings.findOne({ key: 'COMMUNITY_OPEN' }))?.value !== 'false';
      const pinned = (await Settings.findOne({ key: 'COMMUNITY_PINNED' }))?.value || '';
      const onlineCount = io.sockets.adapter.rooms.get('community_global')?.size || 0;
      socket.emit('community_status', { isOpen, pinned, onlineCount });
      // Broadcast updated online count to all
      io.to('community_global').emit('community_online_count', { count: onlineCount });
    } catch (e) { /* silent */ }
  });

  socket.on('community_message', async ({ text }) => {
    try {
      if (!socket.userId || !text) return;
      const User = require('./models/User');
      const ChatMessage = require('./models/ChatMessage');
      const Settings = require('./models/Settings');

      // Check if community is open
      const isOpen = (await Settings.findOne({ key: 'COMMUNITY_OPEN' }))?.value !== 'false';
      if (!isOpen) { socket.emit('community_error', { message: 'Community chat abhi band hai' }); return; }

      const user = await User.findById(socket.userId).select('channelName profilePic');
      if (!user) return;

      const msg = await ChatMessage.create({
        roomId: 'community_global',
        senderId: socket.userId,
        senderName: user.channelName,
        senderPic: user.profilePic,
        text
      });

      io.to('community_global').emit('community_message', {
        _id: msg._id,
        senderId: msg.senderId,
        senderName: msg.senderName,
        senderPic: msg.senderPic,
        text: msg.text,
        createdAt: msg.createdAt
      });
    } catch (e) { /* silent */ }
  });

}); // end io.on('connection')

// ── 48hr Auto-delete uploaded chat files ─────────────────────
const uploadDir = require('path').join(__dirname, 'public/uploads/chat');

function deleteOldFiles() {
  try {
    const fs = require('fs');
    if (!fs.existsSync(uploadDir)) return;
    const now = Date.now();
    const limit = 48 * 60 * 60 * 1000; // 48 hours in ms
    fs.readdirSync(uploadDir).forEach(file => {
      const filePath = require('path').join(uploadDir, file);
      try {
        const stat = fs.statSync(filePath);
        if (now - stat.mtimeMs > limit) {
          fs.unlinkSync(filePath);
          console.log(`Auto-deleted: ${file}`);
        }
      } catch (e) {}
    });
  } catch (e) {}
}

// ── 7-day auto-delete all chat messages ──────────────────────
async function deleteOldMessages() {
  try {
    const ChatMessage = require('./models/ChatMessage');
    const sevenDaysAgo = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000);
    const result = await ChatMessage.deleteMany({ createdAt: { $lt: sevenDaysAgo } });
    if (result.deletedCount > 0) console.log(`Auto-deleted ${result.deletedCount} old messages`);
  } catch (e) {}
}

// Run every hour
setInterval(deleteOldFiles, 60 * 60 * 1000);
setInterval(deleteOldMessages, 60 * 60 * 1000);
deleteOldFiles(); // Run on startup too
deleteOldMessages();

const PORT = process.env.PORT || 5000;
server.listen(PORT, () => console.log(`Server running on port ${PORT}`));
