const mongoose = require('mongoose');

const promotedChannelSchema = new mongoose.Schema({
  channelId: { type: String, required: true },
  channelName: { type: String, required: true },
  channelUrl: { type: String },
  isActive: { type: Boolean, default: true },
  totalSilentSubs: { type: Number, default: 0 },
  addedAt: { type: Date, default: Date.now }
});

module.exports = mongoose.model('PromotedChannel', promotedChannelSchema);
