const express = require('express');
const router = express.Router();
const authMiddleware = require('../middleware/auth');
const ChatRoom = require('../models/ChatRoom');
const ChatMessage = require('../models/ChatMessage');
const User = require('../models/User');
const multer = require('multer');
const path = require('path');
const fs = require('fs');

// ── File Upload Setup ─────────────────────────────────────────
const uploadDir = path.join(__dirname, '../public/uploads/chat');
if (!fs.existsSync(uploadDir)) fs.mkdirSync(uploadDir, { recursive: true });

const storage = multer.diskStorage({
  destination: (_req, _file, cb) => cb(null, uploadDir),
  filename: (_req, file, cb) => {
    const ext = path.extname(file.originalname);
    cb(null, `${Date.now()}_${Math.random().toString(36).slice(2)}${ext}`);
  }
});

const upload = multer({
  storage,
  limits: { fileSize: 50 * 1024 * 1024 }, // 50MB max
  fileFilter: (_req, file, cb) => {
    const allowed = /jpeg|jpg|png|gif|webp|mp4|mkv|mov|avi|pdf|doc|docx|xls|xlsx|ppt|pptx|txt|zip|rar|m4a|mp3|ogg|wav|aac|opus|webm|3gp|amr/;
    const ext = path.extname(file.originalname).toLowerCase().slice(1);
    // Allow if no extension or mimetype is audio/video/image
    if (!ext || /audio|video|image/.test(file.mimetype)) {
      return cb(null, true);
    }
    cb(null, allowed.test(ext));
  }
});

// ── Get my chat rooms (DMs + Groups) ─────────────────────────
router.get('/rooms', authMiddleware, async (req, res) => {
  try {
    const rooms = await ChatRoom.find({ members: req.user._id })
      .sort({ lastTime: -1 })
      .lean();

    // Collect all other member IDs for DM rooms in one query
    const otherIds = rooms
      .filter(r => !r.isGroup && r.members.length === 2)
      .map(r => r.members.find(m => m.toString() !== req.user._id.toString()))
      .filter(Boolean);

    const [otherUsers, unreadCounts, myUser] = await Promise.all([
      User.find({ _id: { $in: otherIds } }).select('channelName profilePic blockedUsers').lean(),
      ChatMessage.aggregate([
        { $match: { roomId: { $in: rooms.map(r => r._id) }, senderId: { $ne: req.user._id }, read: false } },
        { $group: { _id: '$roomId', count: { $sum: 1 } } }
      ]),
      User.findById(req.user._id).select('blockedUsers').lean()
    ]);

    const userMap = Object.fromEntries(otherUsers.map(u => [u._id.toString(), u]));
    const unreadMap = Object.fromEntries(unreadCounts.map(u => [u._id.toString(), u.count]));

    const result = rooms.map(room => {
      let name = room.name;
      let pic = room.pic;
      let otherUserId = null;
      if (!room.isGroup && room.members.length === 2) {
        const otherId = room.members.find(m => m.toString() !== req.user._id.toString());
        const other = otherId && userMap[otherId.toString()];
        if (other) { name = other.channelName; pic = other.profilePic; }
        if (otherId) otherUserId = otherId.toString();
      }
      return {
        _id: room._id,
        name,
        pic,
        isGroup: room.isGroup,
        members: room.members,
        lastMessage: room.lastMessage,
        lastTime: room.lastTime,
        unread: unreadMap[room._id.toString()] || 0,
        otherUserId,
        isBlockedByMe: otherUserId ? (myUser?.blockedUsers?.some(id => id.toString() === otherUserId) || false) : false,
        isBlockedByThem: otherUserId ? (userMap[otherUserId]?.blockedUsers?.some(id => id.toString() === req.user._id.toString()) || false) : false
      };
    });

    res.json({ rooms: result });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ── Get messages for a room ───────────────────────────────────
router.get('/messages/:roomId', authMiddleware, async (req, res) => {
  try {
    const room = await ChatRoom.findById(req.params.roomId);
    if (!room) return res.status(404).json({ error: 'Room not found' });
    if (!room.members.some(m => m.toString() === req.user._id.toString())) {
      return res.status(403).json({ error: 'Not a member' });
    }
    if (room.isBlocked) return res.status(403).json({ error: 'This group has been blocked by admin' });

    const messages = await ChatMessage.find({ roomId: req.params.roomId })
      .sort({ createdAt: 1 })
      .limit(100)
      .lean();

    // Convert starred array to boolean for current user
    const userId = req.user._id.toString();
    const processedMessages = messages.map(msg => ({
      ...msg,
      starred: Array.isArray(msg.starred) ? msg.starred.some(id => id.toString() === userId) : !!msg.starred
    }));

    // Mark as read
    await ChatMessage.updateMany(
      { roomId: req.params.roomId, senderId: { $ne: req.user._id }, read: false },
      { read: true }
    );

    res.json({ messages: processedMessages });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ── Send message (REST fallback) ──────────────────────────────
router.post('/send', authMiddleware, async (req, res) => {
  try {
    const { roomId, text, replyTo } = req.body;
    if (!roomId || !text) return res.status(400).json({ error: 'roomId and text required' });

    const room = await ChatRoom.findById(roomId);
    if (!room) return res.status(404).json({ error: 'Room not found' });
    if (!room.members.some(m => m.toString() === req.user._id.toString())) {
      return res.status(403).json({ error: 'Not a member' });
    }

    // Block check — DM room mein
    if (!room.isGroup) {
      const otherId = room.members.find(m => m.toString() !== req.user._id.toString());
      if (otherId) {
        const [me, other] = await Promise.all([
          User.findById(req.user._id).select('blockedUsers').lean(),
          User.findById(otherId).select('blockedUsers').lean()
        ]);
        const iBlockedThem = me?.blockedUsers?.some(id => id.toString() === otherId.toString());
        const theyBlockedMe = other?.blockedUsers?.some(id => id.toString() === req.user._id.toString());
        if (iBlockedThem || theyBlockedMe) return res.status(403).json({ error: 'blocked' });
      }
    }

    let replyRef = null;
    if (replyTo) {
      const replyMsg = await ChatMessage.findById(replyTo);
      if (replyMsg) replyRef = { msgId: replyMsg._id, text: replyMsg.text, senderName: replyMsg.senderName };
    }

    const msg = await ChatMessage.create({
      roomId,
      senderId: req.user._id,
      senderName: req.user.channelName,
      senderPic: req.user.profilePic,
      text,
      replyTo: replyRef
    });

    await ChatRoom.findByIdAndUpdate(roomId, { lastMessage: text, lastTime: new Date() });

    res.json(msg);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ── Upload file + send as message ─────────────────────────────
router.post('/upload', authMiddleware, (req, res, next) => {
  upload.single('file')(req, res, (err) => {
    if (err) {
      if (err.code === 'LIMIT_FILE_SIZE') {
        return res.status(413).json({ error: 'File size limit 100MB only' });
      }
      return res.status(400).json({ error: err.message || 'Upload failed' });
    }
    next();
  });
}, async (req, res) => {
  try {
    if (!req.file) return res.status(400).json({ error: 'No file uploaded' });
    const { roomId } = req.body;
    if (!roomId) return res.status(400).json({ error: 'roomId required' });

    const room = await ChatRoom.findById(roomId);
    if (!room) return res.status(404).json({ error: 'Room not found' });
    if (!room.members.some(m => m.toString() === req.user._id.toString())) {
      return res.status(403).json({ error: 'Not a member' });
    }

    // Block check
    if (!room.isGroup) {
      const otherId = room.members.find(m => m.toString() !== req.user._id.toString());
      if (otherId) {
        const [me, other] = await Promise.all([
          User.findById(req.user._id).select('blockedUsers').lean(),
          User.findById(otherId).select('blockedUsers').lean()
        ]);
        if (me?.blockedUsers?.some(id => id.toString() === otherId.toString()) ||
            other?.blockedUsers?.some(id => id.toString() === req.user._id.toString())) {
          return res.status(403).json({ error: 'blocked' });
        }
      }
    }

    const ext = path.extname(req.file.originalname).toLowerCase().slice(1);
    const imageExts = ['jpg','jpeg','png','gif','webp'];
    const videoExts = ['mp4','mkv','mov','avi','webm','3gp'];
    const audioExts = ['m4a','mp3','ogg','wav','aac','opus','amr'];
    const fileType = imageExts.includes(ext) ? 'image' : videoExts.includes(ext) ? 'video' : audioExts.includes(ext) ? 'audio' : 'document';
    const fileUrl = `/uploads/chat/${req.file.filename}`;
    const fileName = req.file.originalname;
    const text = fileType === 'image' ? '📷 Photo' : fileType === 'video' ? '🎥 Video' : fileType === 'audio' ? '🎤 Voice' : `📄 ${fileName}`;

    const msg = await ChatMessage.create({
      roomId,
      senderId: req.user._id,
      senderName: req.user.channelName,
      senderPic: req.user.profilePic,
      text,
      fileUrl,
      fileType,
      fileName
    });

    await ChatRoom.findByIdAndUpdate(roomId, { lastMessage: text, lastTime: new Date() });

    // Socket broadcast — dono ko real-time dikhao
    const io = req.app.get('io');
    if (io) {
      const msgPayload = {
        _id: msg._id,
        roomId,
        senderId: msg.senderId,
        senderName: msg.senderName,
        senderPic: msg.senderPic,
        text: msg.text,
        fileUrl: msg.fileUrl,
        fileType: msg.fileType,
        fileName: msg.fileName,
        createdAt: msg.createdAt,
        replyTo: null
      };
      // Broadcast to chat room — sender ko exclude karo (usse REST response se already milega)
      const chatRoomSockets = io.sockets.adapter.rooms.get(`chat_${roomId}`) || new Set();
      const senderUserRoom = io.sockets.adapter.rooms.get(`user_${req.user._id.toString()}`) || new Set();
      for (const sid of chatRoomSockets) {
        if (!senderUserRoom.has(sid)) {
          io.to(sid).emit('chat_message', msgPayload);
        }
      }
      // Notify members not in chat room
      room.members.forEach(memberId => {
        const mId = memberId.toString();
        if (mId === req.user._id.toString()) return;
        const memberUserRoom = io.sockets.adapter.rooms.get(`user_${mId}`);
        if (!memberUserRoom) return;
        let isInChatRoom = false;
        for (const sid of memberUserRoom) {
          if (chatRoomSockets.has(sid)) { isInChatRoom = true; break; }
        }
        if (!isInChatRoom) {
          io.to(`user_${mId}`).emit('chat_message_notify', msgPayload);
        }
      });
    }

    res.json({
      _id: msg._id.toString(),
      senderId: msg.senderId ? msg.senderId.toString() : '',
      senderName: msg.senderName || '',
      senderPic: msg.senderPic || '',
      text: msg.text || '',
      createdAt: msg.createdAt,
      read: false,
      pinned: false,
      starred: false,
      reactions: {},
      replyTo: null,
      fileUrl: msg.fileUrl || null,
      fileType: msg.fileType || null,
      fileName: msg.fileName || null,
      fileSize: req.file.size || null
    });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ── Create DM room (or get existing) ─────────────────────────
router.post('/dm', authMiddleware, async (req, res) => {
  try {
    const { targetUserId } = req.body;
    if (!targetUserId) return res.status(400).json({ error: 'targetUserId required' });

    const target = await User.findById(targetUserId);
    if (!target) return res.status(404).json({ error: 'User not found' });

    // Check if DM room already exists
    const existing = await ChatRoom.findOne({
      isGroup: false,
      members: { $all: [req.user._id, target._id], $size: 2 }
    });
    if (existing) return res.json({ room: existing });

    const room = await ChatRoom.create({
      isGroup: false,
      members: [req.user._id, target._id],
      createdBy: req.user._id
    });

    res.json({ room });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ── Chat Request: Send ────────────────────────────────────────
router.post('/request/send', authMiddleware, async (req, res) => {
  try {
    const ChatRequest = require('../models/ChatRequest');
    const { targetUserId } = req.body;
    if (!targetUserId) return res.status(400).json({ error: 'targetUserId required' });
    if (targetUserId === req.user._id.toString()) return res.status(400).json({ error: 'Apne aap ko request nahi bhej sakte' });

    const target = await User.findById(targetUserId);
    if (!target) return res.status(404).json({ error: 'User not found' });

    // Agar DM room already exist karta hai toh direct return karo
    const existingRoom = await ChatRoom.findOne({
      isGroup: false,
      members: { $all: [req.user._id, target._id], $size: 2 }
    });
    if (existingRoom) return res.json({ alreadyConnected: true, room: existingRoom });

    // Upsert request (pending state mein)
    const request = await ChatRequest.findOneAndUpdate(
      { fromUserId: req.user._id, toUserId: target._id },
      { status: 'pending' },
      { upsert: true, new: true }
    );

    // Socket se receiver ko notify karo
    const io = req.app.get('io');
    if (io) {
      io.to(`user_${targetUserId}`).emit('chat_request', {
        requestId: request._id,
        fromUserId: req.user._id,
        fromName: req.user.channelName,
        fromPic: req.user.profilePic || '',
        createdAt: request.createdAt
      });
    }

    // FCM push if receiver is offline
    try {
      const admin = require('../firebase-admin');
      if (admin) {
        const receiverRoom = io?.sockets?.adapter?.rooms?.get(`user_${targetUserId}`);
        if (!receiverRoom || receiverRoom.size === 0) {
          const receiver = await User.findById(targetUserId).select('fcmToken').lean();
          if (receiver?.fcmToken) {
            await admin.messaging().send({
              token: receiver.fcmToken,
              notification: { title: `💬 ${req.user.channelName}`, body: 'Aapko chat request bheja hai' },
              data: { type: 'chat_request', fromUserId: req.user._id.toString() },
              android: { priority: 'high', notification: { channelId: 'ytbooster_channel', sound: 'default' } }
            }).catch(() => {});
          }
        }
      }
    } catch (e) {}

    res.json({ success: true, requestId: request._id });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ── Chat Request: Accept ──────────────────────────────────────
router.post('/request/accept', authMiddleware, async (req, res) => {
  try {
    const ChatRequest = require('../models/ChatRequest');
    const { requestId } = req.body;
    if (!requestId) return res.status(400).json({ error: 'requestId required' });

    const request = await ChatRequest.findById(requestId);
    if (!request) return res.status(404).json({ error: 'Request not found' });
    if (request.toUserId.toString() !== req.user._id.toString()) return res.status(403).json({ error: 'Forbidden' });

    request.status = 'accepted';
    await request.save();

    // DM room create karo
    let room = await ChatRoom.findOne({
      isGroup: false,
      members: { $all: [request.fromUserId, request.toUserId], $size: 2 }
    });
    if (!room) {
      room = await ChatRoom.create({
        isGroup: false,
        members: [request.fromUserId, request.toUserId],
        createdBy: req.user._id
      });
    }

    // Sender ko notify karo
    const io = req.app.get('io');
    if (io) {
      io.to(`user_${request.fromUserId.toString()}`).emit('chat_request_accepted', {
        requestId: request._id,
        room: { _id: room._id, name: req.user.channelName, pic: req.user.profilePic || '', isGroup: false, members: room.members, lastMessage: '', lastTime: '', unread: 0, otherUserId: req.user._id.toString() }
      });
    }

    res.json({ success: true, room });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ── Chat Request: Reject ──────────────────────────────────────
router.post('/request/reject', authMiddleware, async (req, res) => {
  try {
    const ChatRequest = require('../models/ChatRequest');
    const { requestId } = req.body;
    if (!requestId) return res.status(400).json({ error: 'requestId required' });

    const request = await ChatRequest.findById(requestId);
    if (!request) return res.status(404).json({ error: 'Request not found' });
    if (request.toUserId.toString() !== req.user._id.toString()) return res.status(403).json({ error: 'Forbidden' });

    await request.deleteOne();

    // Sender ko notify karo ki request reject ho gayi
    const io = req.app.get('io');
    if (io) {
      io.to(`user_${request.fromUserId.toString()}`).emit('chat_request_rejected', {
        requestId: requestId
      });
    }

    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ── Chat Request: Get Pending (mere liye aaye requests) ───────
router.get('/requests/pending', authMiddleware, async (req, res) => {
  try {
    const ChatRequest = require('../models/ChatRequest');
    const requests = await ChatRequest.find({ toUserId: req.user._id, status: 'pending' })
      .populate('fromUserId', 'channelName profilePic')
      .sort({ createdAt: -1 })
      .lean();

    const result = requests.map(r => ({
      requestId: r._id,
      fromUserId: r.fromUserId._id,
      fromName: r.fromUserId.channelName,
      fromPic: r.fromUserId.profilePic || '',
      createdAt: r.createdAt
    }));

    res.json({ requests: result });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ── Chat Request: Get Sent (maine bheje requests) ─────────────
router.get('/requests/sent', authMiddleware, async (req, res) => {
  try {
    const ChatRequest = require('../models/ChatRequest');
    const requests = await ChatRequest.find({ fromUserId: req.user._id, status: 'pending' })
      .populate('toUserId', 'channelName profilePic')
      .sort({ createdAt: -1 })
      .lean();

    const result = requests.map(r => ({
      requestId: r._id,
      toUserId: r.toUserId._id,
      toName: r.toUserId.channelName,
      toPic: r.toUserId.profilePic || '',
      createdAt: r.createdAt
    }));

    res.json({ requests: result });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ── Create Group ──────────────────────────────────────────────
router.post('/group/create', authMiddleware, async (req, res) => {
  try {
    const { name, memberIds } = req.body;
    if (!name) return res.status(400).json({ error: 'Group name required' });

    // Only creator is initial member, others get invited
    const room = await ChatRoom.create({
      name,
      isGroup: true,
      members: [req.user._id],
      admins: [req.user._id],
      createdBy: req.user._id,
      adminCode: req.user.adminCodeUsed || null,
      pendingInvites: []
    });

    // Send invites to all selected members
    if (memberIds && Array.isArray(memberIds)) {
      const io = req.app.get('io');
      for (const targetUserId of memberIds) {
        if (targetUserId === req.user._id.toString()) continue;
        // Add to pendingInvites
        await ChatRoom.findByIdAndUpdate(room._id, { $addToSet: { pendingInvites: targetUserId } });

        // Socket notification
        if (io) {
          io.to(`user_${targetUserId}`).emit('group_invite', {
            roomId: room._id,
            roomName: room.name,
            invitedBy: req.user.channelName,
            invitedByPic: req.user.profilePic || ''
          });
        }

        // FCM push if offline
        try {
          const admin = require('../firebase-admin');
          if (admin) {
            const targetRoom = io?.sockets?.adapter?.rooms?.get(`user_${targetUserId}`);
            if (!targetRoom || targetRoom.size === 0) {
              const targetUser = await User.findById(targetUserId).select('fcmToken').lean();
              if (targetUser?.fcmToken) {
                await admin.messaging().send({
                  token: targetUser.fcmToken,
                  notification: { title: `👥 Group Invite`, body: `${req.user.channelName} ne aapko "${room.name}" group mein invite kiya` },
                  data: { type: 'group_invite', roomId: room._id.toString() },
                  android: { priority: 'high', notification: { channelId: 'ytbooster_channel', sound: 'default' } }
                }).catch(() => {});
              }
            }
          }
        } catch (e) {}
      }
    }

    res.json({ room });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ── Send group invite (socket notification, not DM) ──────────
router.post('/group/invite', authMiddleware, async (req, res) => {
  try {
    const { roomId, targetUserId } = req.body;
    const room = await ChatRoom.findById(roomId);
    if (!room || !room.isGroup) return res.status(404).json({ error: 'Group not found' });
    if (!room.admins.some(a => a.toString() === req.user._id.toString())) {
      const subAdmin = room.subAdmins?.find(s => s.userId.toString() === req.user._id.toString());
      if (!subAdmin?.canInviteMembers) return res.status(403).json({ error: 'Permission denied' });
    }
    if (room.members.some(m => m.toString() === targetUserId)) {
      return res.status(400).json({ error: 'Already a member' });
    }

    // Add to pendingInvites
    await ChatRoom.findByIdAndUpdate(roomId, { $addToSet: { pendingInvites: targetUserId } });

    // Socket notification to target user
    const io = req.app.get('io');
    if (io) {
      io.to(`user_${targetUserId}`).emit('group_invite', {
        roomId: room._id,
        roomName: room.name,
        invitedBy: req.user.channelName,
        invitedByPic: req.user.profilePic || ''
      });
    }

    // FCM push if offline
    try {
      const admin = require('../firebase-admin');
      if (admin) {
        const targetRoom = io?.sockets?.adapter?.rooms?.get(`user_${targetUserId}`);
        if (!targetRoom || targetRoom.size === 0) {
          const targetUser = await User.findById(targetUserId).select('fcmToken').lean();
          if (targetUser?.fcmToken) {
            await admin.messaging().send({
              token: targetUser.fcmToken,
              notification: { title: `👥 Group Invite`, body: `${req.user.channelName} ne aapko "${room.name}" group mein invite kiya` },
              data: { type: 'group_invite', roomId: room._id.toString() },
              android: { priority: 'high', notification: { channelId: 'ytbooster_channel', sound: 'default' } }
            }).catch(() => {});
          }
        }
      }
    } catch (e) {}

    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ── Join group directly by roomId ────────────────────────────
router.post('/group/join', authMiddleware, async (req, res) => {
  try {
    const { roomId } = req.body;
    if (!roomId) return res.status(400).json({ error: 'roomId required' });

    const room = await ChatRoom.findById(roomId);
    if (!room || !room.isGroup) return res.status(404).json({ error: 'Group not found' });
    if (room.isBlocked) return res.status(403).json({ error: 'Group blocked' });

    const alreadyMember = room.members.some(m => m.toString() === req.user._id.toString());
    if (alreadyMember) return res.json({ success: true, alreadyMember: true, room });

    await ChatRoom.findByIdAndUpdate(roomId, { $addToSet: { members: req.user._id } });

    const sysMsg = await ChatMessage.create({
      roomId: room._id, senderId: req.user._id, senderName: 'System', senderPic: '',
      text: `${req.user.channelName} group mein join ho gaya! 🎉`
    });
    await ChatRoom.findByIdAndUpdate(roomId, { lastMessage: sysMsg.text, lastTime: new Date() });

    const io = req.app.get('io');
    if (io) {
      io.to(`chat_${roomId}`).emit('chat_message', {
        _id: sysMsg._id, roomId, senderId: req.user._id, senderName: 'System',
        senderPic: '', text: sysMsg.text, createdAt: sysMsg.createdAt, replyTo: null
      });
    }

    const updatedRoom = await ChatRoom.findById(roomId).lean();
    res.json({ success: true, alreadyMember: false, room: updatedRoom });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});


router.post('/group/accept', authMiddleware, async (req, res) => {
  try {
    const { roomId } = req.body;
    const room = await ChatRoom.findById(roomId);
    if (!room || !room.isGroup) return res.status(404).json({ error: 'Group not found' });

    // Remove from pending, add to members
    await ChatRoom.findByIdAndUpdate(roomId, {
      $pull: { pendingInvites: req.user._id },
      $addToSet: { members: req.user._id }
    });

    // System message
    const sysMsg = await ChatMessage.create({
      roomId: room._id, senderId: req.user._id, senderName: 'System', senderPic: '',
      text: `${req.user.channelName} group mein join ho gaya! 🎉`
    });
    await ChatRoom.findByIdAndUpdate(roomId, { lastMessage: sysMsg.text, lastTime: new Date() });

    // Notify all group members
    const io = req.app.get('io');
    if (io) {
      io.to(`chat_${roomId}`).emit('chat_message', {
        _id: sysMsg._id, roomId, senderId: req.user._id, senderName: 'System',
        senderPic: '', text: sysMsg.text, createdAt: sysMsg.createdAt, replyTo: null
      });
      // Notify group members to refresh room list
      room.members.forEach(mId => {
        io.to(`user_${mId.toString()}`).emit('group_member_joined', { roomId, memberName: req.user.channelName });
      });
    }

    const updatedRoom = await ChatRoom.findById(roomId).lean();
    res.json({ success: true, room: updatedRoom });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ── Reject group invite ───────────────────────────────────────
router.post('/group/reject', authMiddleware, async (req, res) => {
  try {
    const { roomId } = req.body;
    await ChatRoom.findByIdAndUpdate(roomId, { $pull: { pendingInvites: req.user._id } });
    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ── Get group info ────────────────────────────────────────────
router.get('/group/info/:roomId', authMiddleware, async (req, res) => {
  try {
    const room = await ChatRoom.findById(req.params.roomId).lean();
    if (!room || !room.isGroup) return res.status(404).json({ error: 'Group not found' });
    if (!room.members.some(m => m.toString() === req.user._id.toString())) {
      return res.status(403).json({ error: 'Not a member' });
    }
    const members = await User.find({ _id: { $in: room.members } }).select('channelName profilePic').lean();
    res.json({
      room: { ...room, adminId: room.admins?.[0]?.toString() },
      members: members.map(m => ({ _id: m._id, channelName: m.channelName, profilePic: m.profilePic || '' })),
      isAdmin: room.admins?.some(a => a.toString() === req.user._id.toString()) || false
    });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ── Remove member (admin or subadmin with canBanMembers) ─────
router.post('/group/remove', authMiddleware, async (req, res) => {
  try {
    const { roomId, targetUserId } = req.body;
    const room = await ChatRoom.findById(roomId);
    if (!room || !room.isGroup) return res.status(404).json({ error: 'Group not found' });
    const isAdmin = room.admins.some(a => a.toString() === req.user._id.toString());
    const subAdmin = room.subAdmins?.find(s => s.userId.toString() === req.user._id.toString());
    if (!isAdmin && !subAdmin?.canBanMembers) {
      return res.status(403).json({ error: 'Permission denied' });
    }
    await ChatRoom.findByIdAndUpdate(roomId, { $pull: { members: targetUserId } });
    const target = await User.findById(targetUserId).select('channelName').lean();
    const sysMsg = await ChatMessage.create({
      roomId, senderId: req.user._id, senderName: 'System', senderPic: '',
      text: `${target?.channelName || 'Member'} ko group se remove kar diya gaya`
    });
    await ChatRoom.findByIdAndUpdate(roomId, { lastMessage: sysMsg.text, lastTime: new Date() });
    const io = req.app.get('io');
    if (io) {
      io.to(`chat_${roomId}`).emit('chat_message', {
        _id: sysMsg._id, roomId, senderId: req.user._id, senderName: 'System',
        senderPic: '', text: sysMsg.text, createdAt: sysMsg.createdAt, replyTo: null
      });
      io.to(`user_${targetUserId}`).emit('group_removed', { roomId, roomName: room.name });
    }
    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ── Leave group ───────────────────────────────────────────────
router.post('/group/leave', authMiddleware, async (req, res) => {
  try {
    const { roomId } = req.body;
    const room = await ChatRoom.findById(roomId);
    if (!room || !room.isGroup) return res.status(404).json({ error: 'Group not found' });
    await ChatRoom.findByIdAndUpdate(roomId, { $pull: { members: req.user._id, admins: req.user._id } });
    const sysMsg = await ChatMessage.create({
      roomId, senderId: req.user._id, senderName: 'System', senderPic: '',
      text: `${req.user.channelName} group se chale gaye`
    });
    await ChatRoom.findByIdAndUpdate(roomId, { lastMessage: sysMsg.text, lastTime: new Date() });
    const io = req.app.get('io');
    if (io) {
      io.to(`chat_${roomId}`).emit('chat_message', {
        _id: sysMsg._id, roomId, senderId: req.user._id, senderName: 'System',
        senderPic: '', text: sysMsg.text, createdAt: sysMsg.createdAt, replyTo: null
      });
    }
    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ── Get users list (for new DM / group) ──────────────────────
router.get('/users', authMiddleware, async (req, res) => {
  try {
    const { q } = req.query;
    const filter = { _id: { $ne: req.user._id }, isBanned: false };
    if (q) filter.channelName = { $regex: q, $options: 'i' };

    const users = await User.find(filter)
      .select('channelName profilePic')
      .limit(20)
      .lean();

    res.json({ users });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ── Search public groups ──────────────────────────────────────
router.get('/groups/search', authMiddleware, async (req, res) => {
  try {
    const { q } = req.query;
    const filter = { isGroup: true, isBlocked: { $ne: true } };
    if (q) filter.name = { $regex: q, $options: 'i' };

    const groups = await ChatRoom.find(filter)
      .select('name pic members createdAt')
      .limit(20)
      .lean();

    const myId = req.user._id.toString();
    const result = groups.map(g => ({
      _id: g._id,
      name: g.name,
      pic: g.pic || '',
      memberCount: g.members?.length || 0,
      isMember: g.members?.some(m => m.toString() === myId) || false
    }));

    res.json({ groups: result });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});


router.post('/react', authMiddleware, async (req, res) => {
  try {
    const { msgId, emoji } = req.body;
    const msg = await ChatMessage.findById(msgId);
    if (!msg) return res.status(404).json({ error: 'Message not found' });

    const current = msg.reactions.get(emoji) || 0;
    msg.reactions.set(emoji, current + 1);
    await msg.save();

    res.json({ success: true, reactions: Object.fromEntries(msg.reactions) });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ── Star / unstar message ─────────────────────────────────────
router.post('/star', authMiddleware, async (req, res) => {
  try {
    const { msgId } = req.body;
    const msg = await ChatMessage.findById(msgId);
    if (!msg) return res.status(404).json({ error: 'Message not found' });

    const idx = msg.starred.indexOf(req.user._id);
    if (idx === -1) msg.starred.push(req.user._id);
    else msg.starred.splice(idx, 1);
    await msg.save();

    res.json({ success: true, starred: idx === -1 });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ── Get starred messages ──────────────────────────────────────
router.get('/starred/:roomId', authMiddleware, async (req, res) => {
  try {
    const userId = req.user._id.toString();
    const msgs = await ChatMessage.find({ roomId: req.params.roomId, starred: req.user._id }).lean();
    const processed = msgs.map(msg => ({
      ...msg,
      starred: true // these are all starred by this user
    }));
    res.json({ messages: processed });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ── Pin message ───────────────────────────────────────────────
router.post('/pin', authMiddleware, async (req, res) => {
  try {
    const { msgId, roomId } = req.body;
    const room = await ChatRoom.findById(roomId);
    if (!room) return res.status(404).json({ error: 'Room not found' });

    // Permission check: admin or subadmin with canPinMessages
    if (room.isGroup) {
      const isAdmin = room.admins.some(a => a.toString() === req.user._id.toString());
      const subAdmin = room.subAdmins?.find(s => s.userId.toString() === req.user._id.toString());
      if (!isAdmin && !subAdmin?.canPinMessages) return res.status(403).json({ error: 'Permission denied' });
    }

    await ChatMessage.updateMany({ roomId }, { pinned: false });
    if (msgId) {
      await ChatMessage.findByIdAndUpdate(msgId, { pinned: true });
    }
    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ── Delete message ────────────────────────────────────────────
router.delete('/message/:msgId', authMiddleware, async (req, res) => {
  try {
    const msg = await ChatMessage.findById(req.params.msgId);
    if (!msg) return res.status(404).json({ error: 'Not found' });

    const isSender = msg.senderId.toString() === req.user._id.toString();
    if (!isSender) {
      // Check if admin or subadmin with canDeleteMessages in group
      const room = await ChatRoom.findById(msg.roomId);
      if (room?.isGroup) {
        const isAdmin = room.admins.some(a => a.toString() === req.user._id.toString());
        const subAdmin = room.subAdmins?.find(s => s.userId.toString() === req.user._id.toString());
        if (!isAdmin && !subAdmin?.canDeleteMessages) return res.status(403).json({ error: 'Permission denied' });
      } else {
        return res.status(403).json({ error: 'Only sender can delete' });
      }
    }

    await msg.deleteOne();
    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ── Community Chat ────────────────────────────────────────────
const COMMUNITY_ROOM_ID = 'community_global';

router.get('/community', authMiddleware, async (req, res) => {
  try {
    const messages = await ChatMessage.find({ roomId: COMMUNITY_ROOM_ID })
      .sort({ createdAt: -1 })
      .limit(100)
      .lean();
    res.json({ messages: messages.reverse() });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

router.post('/community/send', authMiddleware, async (req, res) => {
  try {
    const { text } = req.body;
    if (!text) return res.status(400).json({ error: 'text required' });

    const msg = await ChatMessage.create({
      roomId: COMMUNITY_ROOM_ID,
      senderId: req.user._id,
      senderName: req.user.channelName,
      senderPic: req.user.profilePic,
      text
    });

    // Broadcast to all community members via socket
    const io = req.app.get('io');
    if (io) {
      io.to(COMMUNITY_ROOM_ID).emit('community_message', {
        _id: msg._id,
        senderId: msg.senderId,
        senderName: msg.senderName,
        senderPic: msg.senderPic,
        text: msg.text,
        createdAt: msg.createdAt
      });
    }

    res.json(msg);
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ── Clear Chat (sirf apne liye) ──────────────────────────────
router.post('/clear', authMiddleware, async (req, res) => {
  try {
    const { roomId } = req.body;
    if (!roomId) return res.status(400).json({ error: 'roomId required' });
    const room = await ChatRoom.findById(roomId);
    if (!room || !room.members.some(m => m.toString() === req.user._id.toString())) {
      return res.status(403).json({ error: 'Not a member' });
    }
    await ChatMessage.deleteMany({ roomId });
    await ChatRoom.findByIdAndUpdate(roomId, { lastMessage: '', lastTime: new Date() });
    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ── Mute / Unmute room ────────────────────────────────────────
router.post('/mute', authMiddleware, async (req, res) => {
  try {
    const { roomId } = req.body;
    if (!roomId) return res.status(400).json({ error: 'roomId required' });
    const room = await ChatRoom.findById(roomId);
    if (!room || !room.members.some(m => m.toString() === req.user._id.toString())) {
      return res.status(403).json({ error: 'Not a member' });
    }
    const mutedList = room.mutedBy || [];
    const idx = mutedList.findIndex(id => id.toString() === req.user._id.toString());
    if (idx === -1) mutedList.push(req.user._id);
    else mutedList.splice(idx, 1);
    room.mutedBy = mutedList;
    await room.save();
    res.json({ success: true, muted: idx === -1 });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ── Block User ────────────────────────────────────────────────
router.post('/block', authMiddleware, async (req, res) => {
  try {
    const { roomId } = req.body;
    if (!roomId) return res.status(400).json({ error: 'roomId required' });
    const room = await ChatRoom.findById(roomId);
    if (!room) return res.status(404).json({ error: 'Room not found' });
    const otherId = room.members.find(m => m.toString() !== req.user._id.toString());
    if (!otherId) return res.status(400).json({ error: 'No other member' });

    await User.findByIdAndUpdate(req.user._id, { $addToSet: { blockedUsers: otherId } });

    // Socket se dono ko notify karo
    const io = req.app.get('io');
    if (io) {
      io.to(`user_${req.user._id.toString()}`).emit('user_blocked', { roomId, blockedBy: req.user._id.toString(), blockedUser: otherId.toString() });
      io.to(`user_${otherId.toString()}`).emit('user_blocked', { roomId, blockedBy: req.user._id.toString(), blockedUser: otherId.toString() });
    }

    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ── Unblock User ──────────────────────────────────────────────
router.post('/unblock', authMiddleware, async (req, res) => {
  try {
    const { roomId } = req.body;
    if (!roomId) return res.status(400).json({ error: 'roomId required' });
    const room = await ChatRoom.findById(roomId);
    if (!room) return res.status(404).json({ error: 'Room not found' });
    const otherId = room.members.find(m => m.toString() !== req.user._id.toString());
    if (!otherId) return res.status(400).json({ error: 'No other member' });

    await User.findByIdAndUpdate(req.user._id, { $pull: { blockedUsers: otherId } });

    // Socket se dono ko notify karo
    const io = req.app.get('io');
    if (io) {
      io.to(`user_${req.user._id.toString()}`).emit('user_unblocked', { roomId, unblockedBy: req.user._id.toString() });
      io.to(`user_${otherId.toString()}`).emit('user_unblocked', { roomId, unblockedBy: req.user._id.toString() });
    }

    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ── Get online status of DM partners ─────────────────────────
router.post('/online-status', authMiddleware, async (req, res) => {
  try {
    const { userIds } = req.body;
    if (!Array.isArray(userIds) || userIds.length === 0) return res.json({ onlineUsers: [] });

    const io = req.app.get('io');
    if (!io) return res.json({ onlineUsers: [] });

    const onlineUsers = [];
    for (const uid of userIds) {
      const room = io.sockets.adapter.rooms.get(`user_${uid}`);
      if (room && room.size > 0) onlineUsers.push(uid);
    }

    res.json({ onlineUsers });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ── Get blocked users list ────────────────────────────────────
router.get('/blocked-users', authMiddleware, async (req, res) => {
  try {
    const me = await User.findById(req.user._id).select('blockedUsers').lean();
    if (!me?.blockedUsers?.length) return res.json({ blockedUsers: [] });

    const blockedUserIds = me.blockedUsers;
    const blockedUsers = await User.find({ _id: { $in: blockedUserIds } })
      .select('channelName profilePic').lean();

    // Find DM rooms for each blocked user
    const result = await Promise.all(blockedUsers.map(async (u) => {
      const room = await ChatRoom.findOne({
        isGroup: false,
        members: { $all: [req.user._id, u._id], $size: 2 }
      }).select('_id').lean();
      return {
        userId: u._id.toString(),
        name: u.channelName,
        pic: u.profilePic || '',
        roomId: room?._id?.toString() || ''
      };
    }));

    res.json({ blockedUsers: result });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ── Group Settings (admin only) ───────────────────────────────

// Update group info (name, description, pic)
router.post('/group/settings', authMiddleware, (req, res, next) => {
  upload.single('pic')(req, res, (err) => {
    if (err) return res.status(400).json({ error: err.message });
    next();
  });
}, async (req, res) => {
  try {
    const { roomId, name, description } = req.body;
    if (!roomId) return res.status(400).json({ error: 'roomId required' });
    const room = await ChatRoom.findById(roomId);
    if (!room || !room.isGroup) return res.status(404).json({ error: 'Group not found' });

    // Check admin or subadmin with changeGroupInfo permission
    const isAdmin = room.admins.some(a => a.toString() === req.user._id.toString());
    const subAdmin = room.subAdmins?.find(s => s.userId.toString() === req.user._id.toString());
    if (!isAdmin && !subAdmin?.canChangeGroupInfo) return res.status(403).json({ error: 'Only admins can change group info' });

    if (name) room.name = name;
    if (description !== undefined) room.description = description;
    if (req.file) {
      room.pic = `/uploads/chat/${req.file.filename}`;
    }
    await room.save();

    const io = req.app.get('io');
    if (io) io.to(`chat_${roomId}`).emit('group_info_updated', { roomId, name: room.name, pic: room.pic, description: room.description });

    res.json({ success: true, name: room.name, pic: room.pic, description: room.description });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// Set slow mode
router.post('/group/slow-mode', authMiddleware, async (req, res) => {
  try {
    const { roomId, seconds } = req.body;
    const room = await ChatRoom.findById(roomId);
    if (!room || !room.isGroup) return res.status(404).json({ error: 'Not found' });
    const isAdmin = room.admins.some(a => a.toString() === req.user._id.toString());
    if (!isAdmin) return res.status(403).json({ error: 'Only admins' });
    room.slowMode = Math.max(0, parseInt(seconds) || 0);
    await room.save();
    const io = req.app.get('io');
    if (io) io.to(`chat_${roomId}`).emit('group_slow_mode', { roomId, seconds: room.slowMode });
    res.json({ success: true, slowMode: room.slowMode });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// Add/remove sub-admin
router.post('/group/subadmin', authMiddleware, async (req, res) => {
  try {
    const { roomId, targetUserId, action, permissions } = req.body;
    // action: 'add' | 'remove' | 'update'
    const room = await ChatRoom.findById(roomId);
    if (!room || !room.isGroup) return res.status(404).json({ error: 'Not found' });
    const isAdmin = room.admins.some(a => a.toString() === req.user._id.toString());
    if (!isAdmin) return res.status(403).json({ error: 'Only admins' });
    if (!room.members.some(m => m.toString() === targetUserId)) return res.status(400).json({ error: 'User not in group' });

    if (action === 'remove') {
      room.subAdmins = (room.subAdmins || []).filter(s => s.userId.toString() !== targetUserId);
    } else if (action === 'add') {
      const existing = (room.subAdmins || []).find(s => s.userId.toString() === targetUserId);
      if (!existing) {
        room.subAdmins = [...(room.subAdmins || []), {
          userId: targetUserId,
          canDeleteMessages: permissions?.canDeleteMessages ?? true,
          canBanMembers: permissions?.canBanMembers ?? false,
          canInviteMembers: permissions?.canInviteMembers ?? true,
          canPinMessages: permissions?.canPinMessages ?? false,
          canChangeGroupInfo: permissions?.canChangeGroupInfo ?? false,
          canStartVoiceChat: permissions?.canStartVoiceChat ?? false
        }];
      }
    } else if (action === 'update') {
      const idx = (room.subAdmins || []).findIndex(s => s.userId.toString() === targetUserId);
      if (idx !== -1 && permissions) Object.assign(room.subAdmins[idx], permissions);
    }
    await room.save();

    const target = await User.findById(targetUserId).select('channelName').lean();
    const io = req.app.get('io');
    if (io) {
      const sysText = action === 'add' ? `${target?.channelName} ko sub-admin banaya gaya ⭐` : action === 'remove' ? `${target?.channelName} ka sub-admin role hataya gaya` : null;
      if (sysText) {
        const sysMsg = await ChatMessage.create({ roomId, senderId: req.user._id, senderName: 'System', senderPic: '', text: sysText });
        io.to(`chat_${roomId}`).emit('chat_message', { _id: sysMsg._id, roomId, senderId: req.user._id, senderName: 'System', senderPic: '', text: sysText, createdAt: sysMsg.createdAt });
      }
      io.to(`user_${targetUserId}`).emit('group_subadmin_changed', { roomId, action, permissions });
    }
    res.json({ success: true, subAdmins: room.subAdmins });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// Generate/get group invite link
router.post('/group/invite-link', authMiddleware, async (req, res) => {
  try {
    const { roomId, reset } = req.body;
    const room = await ChatRoom.findById(roomId);
    if (!room || !room.isGroup) return res.status(404).json({ error: 'Not found' });
    const isAdmin = room.admins.some(a => a.toString() === req.user._id.toString());
    const subAdmin = room.subAdmins?.find(s => s.userId.toString() === req.user._id.toString());
    if (!isAdmin && !subAdmin?.canInviteMembers) return res.status(403).json({ error: 'No permission' });

    if (!room.inviteToken || reset) {
      room.inviteToken = require('crypto').randomBytes(12).toString('hex');
      await room.save();
    }
    const baseUrl = process.env.BASE_URL || 'https://api.picrypto.in';
    res.json({ success: true, inviteLink: `${baseUrl}/join-group/${room.inviteToken}`, token: room.inviteToken });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// Join group via invite link
router.post('/group/join-by-link', authMiddleware, async (req, res) => {
  try {
    const { token } = req.body;
    if (!token) return res.status(400).json({ error: 'token required' });
    const room = await ChatRoom.findOne({ inviteToken: token, isGroup: true });
    if (!room) return res.status(404).json({ error: 'Invalid or expired invite link' });
    if (room.isBlocked) return res.status(403).json({ error: 'Group is blocked' });
    if (room.members.some(m => m.toString() === req.user._id.toString())) {
      return res.json({ success: true, alreadyMember: true, room });
    }
    await ChatRoom.findByIdAndUpdate(room._id, { $addToSet: { members: req.user._id } });
    const sysMsg = await ChatMessage.create({ roomId: room._id, senderId: req.user._id, senderName: 'System', senderPic: '', text: `${req.user.channelName} invite link se join hua! 🎉` });
    await ChatRoom.findByIdAndUpdate(room._id, { lastMessage: sysMsg.text, lastTime: new Date() });
    const io = req.app.get('io');
    if (io) {
      io.to(`chat_${room._id}`).emit('chat_message', { _id: sysMsg._id, roomId: room._id, senderId: req.user._id, senderName: 'System', senderPic: '', text: sysMsg.text, createdAt: sysMsg.createdAt });
      room.members.forEach(mId => io.to(`user_${mId.toString()}`).emit('group_member_joined', { roomId: room._id, memberName: req.user.channelName }));
    }
    const updatedRoom = await ChatRoom.findById(room._id).lean();
    res.json({ success: true, room: updatedRoom });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// ── Forward Message ───────────────────────────────────────────
router.post('/forward', authMiddleware, async (req, res) => {
  try {
    const { msgId, targetRoomIds } = req.body;
    if (!msgId || !Array.isArray(targetRoomIds) || targetRoomIds.length === 0) {
      return res.status(400).json({ error: 'msgId and targetRoomIds required' });
    }
    const original = await ChatMessage.findById(msgId).lean();
    if (!original) return res.status(404).json({ error: 'Message not found' });

    const results = [];
    const io = req.app.get('io');

    for (const roomId of targetRoomIds.slice(0, 5)) {
      const room = await ChatRoom.findById(roomId);
      if (!room || !room.members.some(m => m.toString() === req.user._id.toString())) continue;

      const fwdMsg = await ChatMessage.create({
        roomId, senderId: req.user._id.toString(), senderName: req.user.channelName,
        senderPic: req.user.profilePic || '', text: original.text,
        fileUrl: original.fileUrl, fileType: original.fileType, fileName: original.fileName,
        forwardedFrom: original.senderName
      });
      await ChatRoom.findByIdAndUpdate(roomId, { lastMessage: original.text || '📎 Forwarded', lastTime: new Date() });

      const payload = {
        _id: fwdMsg._id, roomId, senderId: fwdMsg.senderId, senderName: fwdMsg.senderName,
        senderPic: fwdMsg.senderPic, text: fwdMsg.text, createdAt: fwdMsg.createdAt,
        fileUrl: fwdMsg.fileUrl, fileType: fwdMsg.fileType, fileName: fwdMsg.fileName,
        forwardedFrom: fwdMsg.forwardedFrom, replyTo: null
      };
      if (io) io.to(`chat_${roomId}`).emit('chat_message', payload);
      results.push(fwdMsg._id);
    }
    res.json({ success: true, forwarded: results.length });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// ── Disappearing Messages ─────────────────────────────────────
router.post('/disappearing', authMiddleware, async (req, res) => {
  try {
    const { roomId, seconds } = req.body;
    const room = await ChatRoom.findById(roomId);
    if (!room || !room.members.some(m => m.toString() === req.user._id.toString())) return res.status(403).json({ error: 'Not a member' });

    await ChatRoom.findByIdAndUpdate(roomId, { disappearingSeconds: parseInt(seconds) || 0 });
    const io = req.app.get('io');
    if (io) io.to(`chat_${roomId}`).emit('disappearing_changed', { roomId, seconds: parseInt(seconds) || 0 });

    const sysText = seconds > 0 
      ? (seconds === 1 ? 'Messages after viewing delete honge' 
        : seconds === 86400 ? 'Messages 24 hours baad delete honge'
        : seconds === 604800 ? 'Messages 7 days baad delete honge'
        : `Disappearing messages on`)
      : 'Disappearing messages off';
    const sysMsg = await ChatMessage.create({ roomId, senderId: req.user._id.toString(), senderName: 'System', senderPic: '', text: sysText });
    if (io) io.to(`chat_${roomId}`).emit('chat_message', { _id: sysMsg._id, roomId, senderId: req.user._id, senderName: 'System', senderPic: '', text: sysText, createdAt: sysMsg.createdAt });

    res.json({ success: true, seconds: parseInt(seconds) || 0 });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// ── Read Receipts ─────────────────────────────────────────────
router.post('/read', authMiddleware, async (req, res) => {
  try {
    const { roomId, msgIds } = req.body;
    if (!roomId) return res.status(400).json({ error: 'roomId required' });
    const userId = req.user._id.toString();

    if (msgIds && Array.isArray(msgIds) && msgIds.length > 0) {
      await ChatMessage.updateMany({ _id: { $in: msgIds }, roomId, senderId: { $ne: userId } }, { $addToSet: { readBy: userId }, read: true });
    } else {
      await ChatMessage.updateMany({ roomId, senderId: { $ne: userId }, read: false }, { $addToSet: { readBy: userId }, read: true });
    }

    const io = req.app.get('io');
    if (io) io.to(`chat_${roomId}`).emit('messages_read', { roomId, readBy: userId });
    res.json({ success: true });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// Search groups by name (public search)
router.get('/groups/search', authMiddleware, async (req, res) => {
  try {
    const { q } = req.query;
    if (!q || q.length < 2) return res.json({ groups: [] });
    const groups = await ChatRoom.find({
      isGroup: true,
      isBlocked: false,
      name: { $regex: q, $options: 'i' }
    }).select('name pic members description inviteToken').limit(20).lean();
    res.json({ groups: groups.map(g => ({
      _id: g._id,
      name: g.name,
      pic: g.pic || '',
      description: g.description || '',
      memberCount: g.members.length,
      hasInviteLink: !!g.inviteToken
    })) });
  } catch (e) { res.status(500).json({ error: e.message }); }
});

// ── Get offline messages for user ────────────────────────
router.get('/offline-messages', authMiddleware, async (req, res) => {
  try {
    const { since } = req.query;
    const sinceDate = since ? new Date(since) : new Date(Date.now() - 7 * 24 * 60 * 60 * 1000); // Last 7 days
    
    // Get all rooms user is member of
    const rooms = await ChatRoom.find({ members: req.user._id }).select('_id').lean();
    const roomIds = rooms.map(r => r._id.toString());
    
    // Get messages from those rooms since the specified time
    const messages = await ChatMessage.find({
      roomId: { $in: roomIds },
      senderId: { $ne: req.user._id }, // Don't include user's own messages
      createdAt: { $gte: sinceDate }
    })
    .sort({ createdAt: -1 })
    .limit(100)
    .lean();
    
    res.json({ messages, count: messages.length });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ── Get call history for user ─────────────────────────────────
router.get('/call-history', authMiddleware, async (req, res) => {
  try {
    const { limit = 50 } = req.query;
    const CallLog = require('../models/CallLog');
    
    const calls = await CallLog.find({
      participants: req.user._id
    })
    .sort({ startTime: -1 })
    .limit(parseInt(limit))
    .lean();
    
    // Populate caller names
    const callerIds = [...new Set(calls.map(c => c.callerId))];
    const callers = await User.find({ _id: { $in: callerIds } }).select('channelName').lean();
    const callerMap = Object.fromEntries(callers.map(u => [u._id.toString(), u.channelName]));
    
    const callHistory = calls.map(call => ({
      ...call,
      callerName: callerMap[call.callerId] || call.callerName || 'Unknown',
      isMissed: call.status === 'missed' || (call.status === 'ringing' && call.missedBy?.includes(req.user._id.toString())),
      isDeclined: call.status === 'declined' || call.declinedBy?.includes(req.user._id.toString())
    }));
    
    res.json({ calls: callHistory, count: callHistory.length });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ── Mark messages as read ─────────────────────────────────────
router.post('/mark-read', authMiddleware, async (req, res) => {
  try {
    const { roomId, messageIds } = req.body;
    
    if (roomId) {
      // Mark all messages in room as read
      await ChatMessage.updateMany(
        { roomId, senderId: { $ne: req.user._id }, read: false },
        { read: true, $addToSet: { readBy: req.user._id.toString() } }
      );
    } else if (messageIds && Array.isArray(messageIds)) {
      // Mark specific messages as read
      await ChatMessage.updateMany(
        { _id: { $in: messageIds }, senderId: { $ne: req.user._id } },
        { read: true, $addToSet: { readBy: req.user._id.toString() } }
      );
    }
    
    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

module.exports = router;
