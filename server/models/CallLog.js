const mongoose = require('mongoose');

const callLogSchema = new mongoose.Schema({
  roomId: { type: String, required: true, index: true },
  callerId: { type: String, required: true },
  callerName: { type: String, default: '' },
  callType: { type: String, enum: ['voice', 'video'], required: true },
  participants: [{ type: mongoose.Schema.Types.ObjectId, ref: 'User' }],
  status: { 
    type: String, 
    enum: ['ringing', 'connected', 'ended', 'missed', 'declined'], 
    default: 'ringing' 
  },
  startTime: { type: Date, default: Date.now },
  connectTime: { type: Date, default: null },
  endTime: { type: Date, default: null },
  duration: { type: Number, default: 0 }, // seconds
  missedBy: [{ type: String }], // userIds who missed the call
  declinedBy: [{ type: String }] // userIds who declined the call
}, { timestamps: true });

// Auto-calculate duration when call ends
callLogSchema.pre('save', function(next) {
  if (this.connectTime && this.endTime && !this.duration) {
    this.duration = Math.floor((this.endTime - this.connectTime) / 1000);
  }
  next();
});

module.exports = mongoose.model('CallLog', callLogSchema);