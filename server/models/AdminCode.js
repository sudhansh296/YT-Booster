const mongoose = require('mongoose');
const crypto = require('crypto');

const adminCodeSchema = new mongoose.Schema({
  code: { type: String, required: true, unique: true, uppercase: true },
  shortCode: { type: String, unique: true, sparse: true, uppercase: true }, // 6-char code for app entry
  refToken: { type: String, unique: true, sparse: true }, // encrypted public token for referral links
  label: { type: String, required: true },
  role: { type: String, enum: ['owner', 'sub_admin'], default: 'sub_admin' },
  parentCode: { type: String, default: null },
  password: { type: String, default: null },
  smmApiUrl: { type: String, default: null },
  smmApiKey: { type: String, default: null },
  smmServiceId: { type: String, default: null },
  totalClicks: { type: Number, default: 0 },
  totalJoined: { type: Number, default: 0 },
  totalCoinsGiven: { type: Number, default: 0 },
  isActive: { type: Boolean, default: true },
  createdAt: { type: Date, default: Date.now }
});

// Auto-generate refToken before save if not set
adminCodeSchema.pre('save', async function(next) {
  if (!this.refToken) {
    this.refToken = crypto.randomBytes(16).toString('hex'); // 32 char hex token
  }
  // Auto-generate 6-char shortCode if not set
  if (!this.shortCode) {
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    let code;
    const User = require('./User');
    do {
      code = Array.from({ length: 6 }, () => chars[Math.floor(Math.random() * chars.length)]).join('');
    } while (
      await mongoose.model('AdminCode').findOne({ shortCode: code }) ||
      await User.findOne({ referralCode: code })
    );
    this.shortCode = code;
  }
  next();
});

module.exports = mongoose.model('AdminCode', adminCodeSchema);
