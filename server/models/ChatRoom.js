const mongoose = require('mongoose');

const chatRoomSchema = new mongoose.Schema({
  name: { type: String, default: '' },
  pic: { type: String, default: '' },
  description: { type: String, default: '' },           // Group bio/description
  isGroup: { type: Boolean, default: false },
  members: [{ type: mongoose.Schema.Types.ObjectId, ref: 'User' }],
  admins: [{ type: mongoose.Schema.Types.ObjectId, ref: 'User' }],
  subAdmins: [{                                          // Group sub-admins with permissions
    userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User' },
    canDeleteMessages: { type: Boolean, default: true },
    canBanMembers: { type: Boolean, default: false },
    canInviteMembers: { type: Boolean, default: true },
    canPinMessages: { type: Boolean, default: false },
    canChangeGroupInfo: { type: Boolean, default: false }
  }],
  slowMode: { type: Number, default: 0 },               // seconds between messages (0 = off)
  inviteToken: { type: String, default: null },          // unique group invite link token
  pendingInvites: [{ type: mongoose.Schema.Types.ObjectId, ref: 'User' }],
  lastMessage: { type: String, default: '' },
  lastTime: { type: Date, default: Date.now },
  createdBy: { type: mongoose.Schema.Types.ObjectId, ref: 'User' },
  mutedBy: [{ type: mongoose.Schema.Types.ObjectId, ref: 'User' }],
  isBlocked: { type: Boolean, default: false },
  blockedBy: { type: String, default: null },
  adminCode: { type: String, default: null },
  disappearingSeconds: { type: Number, default: 0 }
}, { timestamps: true });

module.exports = mongoose.model('ChatRoom', chatRoomSchema);
