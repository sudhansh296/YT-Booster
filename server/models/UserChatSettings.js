const mongoose = require('mongoose');

// Per-user, per-room settings — delete for me, clear for me
const userChatSettingsSchema = new mongoose.Schema({
  userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
  roomId: { type: mongoose.Schema.Types.ObjectId, ref: 'ChatRoom', required: true },
  clearedAt: { type: Date, default: null },           // Clear chat for me timestamp
  hiddenMsgIds: [{ type: mongoose.Schema.Types.ObjectId }]  // Delete for me message IDs
}, { timestamps: true });

userChatSettingsSchema.index({ userId: 1, roomId: 1 }, { unique: true });

module.exports = mongoose.model('UserChatSettings', userChatSettingsSchema);
