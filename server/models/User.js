const mongoose = require('mongoose');
const crypto = require('crypto');

const userSchema = new mongoose.Schema({
  youtubeId: { type: String, required: true, unique: true },
  channelName: { type: String, required: true },
  channelUrl: { type: String },
  profilePic: { type: String },
  accessToken: { type: String },
  refreshToken: { type: String },
  coins: { type: Number, default: 0 },
  totalEarned: { type: Number, default: 0 },
  totalSpent: { type: Number, default: 0 },
  subscribersGiven: { type: Number, default: 0 },
  subscribersReceived: { type: Number, default: 0 },
  isActive: { type: Boolean, default: true },
  isBanned: { type: Boolean, default: false },
  inQueue: { type: Boolean, default: false },
  lastDailyBonus: { type: Date, default: null },
  currentStreak: { type: Number, default: 0 },
  longestStreak: { type: Number, default: 0 },
  fcmToken: { type: String, default: null },
  referralCode: { type: String, default: null },       // internal 6-char code
  refToken: { type: String, default: null, unique: true, sparse: true }, // public encrypted token for links
  referredBy: { type: mongoose.Schema.Types.ObjectId, ref: 'User', default: null },
  referralCount: { type: Number, default: 0 },
  referralEarned: { type: Number, default: 0 },
  adminCodeUsed: { type: String, default: null },
  referralParent: { type: String, default: null },
  milestoneClaimed: { type: [Number], default: [] },
  createdAt: { type: Date, default: Date.now },
  lastSeen: { type: Date, default: Date.now },
  // Block system
  blockedUsers: [{ type: mongoose.Schema.Types.ObjectId, ref: 'User' }],
  // Anti-fraud fields
  isSuspicious: { type: Boolean, default: false },
  suspiciousReason: { type: String, default: null },
  lastIp: { type: String, default: null },
  deviceFingerprint: { type: String, default: null },
  registrationIp: { type: String, default: null }
});

// Auto-generate refToken before save if not set
userSchema.pre('save', function(next) {
  if (!this.refToken) {
    this.refToken = crypto.randomBytes(12).toString('hex'); // 24 char hex token
  }
  next();
});

module.exports = mongoose.model('User', userSchema);
