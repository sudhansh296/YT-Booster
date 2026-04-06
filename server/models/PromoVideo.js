const mongoose = require('mongoose');

const promoVideoSchema = new mongoose.Schema({
  title: { type: String, default: '' },
  youtubeUrl: { type: String, required: true },
  channelName: { type: String, default: '' },
  type: { type: String, enum: ['short', 'long'], default: 'short' }, // short=60s, long=150s
  coinsReward: { type: Number, default: 5 },
  watchSeconds: { type: Number, default: 60 },
  isActive: { type: Boolean, default: true },
  totalViews: { type: Number, default: 0 },
  totalCoinsGiven: { type: Number, default: 0 },
  addedBy: { type: String, default: 'admin' }, // admin or userId
  priority: { type: Number, default: 0 } // higher = shown first
}, { timestamps: true });

module.exports = mongoose.model('PromoVideo', promoVideoSchema);
