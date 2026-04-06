const mongoose = require('mongoose');

const subscriberOrderSchema = new mongoose.Schema({
  userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
  channelUrl: { type: String, required: true },
  channelName: { type: String },
  quantity: { type: Number, required: true },
  coinsSpent: { type: Number, required: true },
  status: { type: String, enum: ['pending', 'processing', 'completed', 'failed'], default: 'pending' },
  adminNote: { type: String },
  createdAt: { type: Date, default: Date.now },
  updatedAt: { type: Date, default: Date.now }
});

module.exports = mongoose.model('SubscriberOrder', subscriberOrderSchema);
