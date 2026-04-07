const mongoose = require('mongoose');

const chatMessageSchema = new mongoose.Schema({
  roomId: { type: String, required: true, index: true },
  senderId: { type: String, default: '' },
  senderName: { type: String, default: '' },
  senderPic: { type: String, default: '' },
  text: { type: String, default: '' },
  read: { type: Boolean, default: false },
  readBy: [{ type: String }],                            // userIds who read this message
  reactions: { type: Map, of: [String], default: {} },
  replyTo: { msgId: String, text: String, senderName: String },
  forwardedFrom: { type: String, default: null },        // original sender name if forwarded
  disappearsAt: { type: Date, default: null },           // auto-delete time
  pinned: { type: Boolean, default: false },
  edited: { type: Boolean, default: false },
  starred: [{ type: String }],
  fileUrl: { type: String, default: null },
  fileType: { type: String, default: null },
  fileName: { type: String, default: null },
  isAdmin: { type: Boolean, default: false }
}, { timestamps: true });

module.exports = mongoose.model('ChatMessage', chatMessageSchema);
