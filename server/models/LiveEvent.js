const mongoose = require('mongoose');

const liveEventSchema = new mongoose.Schema({
  title: { type: String, required: true },
  description: { type: String, default: '' },
  type: { type: String, enum: ['double_coins', 'tournament', 'bonus_hour', 'custom'], default: 'custom' },
  startTime: { type: Date, required: true },
  endTime: { type: Date, required: true },
  multiplier: { type: Number, default: 2 }, // for double_coins
  prizePool: { type: Number, default: 0 }, // for tournament
  isActive: { type: Boolean, default: true },
  icon: { type: String, default: '🔥' },
  createdBy: { type: String, default: 'admin' }
}, { timestamps: true });

module.exports = mongoose.model('LiveEvent', liveEventSchema);
