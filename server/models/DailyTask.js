const mongoose = require('mongoose');

// Task template (admin creates these)
const dailyTaskSchema = new mongoose.Schema({
  title: { type: String, required: true },
  description: { type: String, default: '' },
  type: { 
    type: String, 
    enum: ['subscribe', 'community_message', 'refer', 'login', 'chat_message', 'custom'],
    default: 'custom'
  },
  targetCount: { type: Number, default: 1 }, // how many times to complete
  coinReward: { type: Number, default: 5 },
  isActive: { type: Boolean, default: true },
  icon: { type: String, default: '🎯' },
  createdBy: { type: String, default: 'admin' } // 'admin' or subAdminCode
}, { timestamps: true });

// User's daily task progress (resets daily)
const userTaskProgressSchema = new mongoose.Schema({
  userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
  taskId: { type: mongoose.Schema.Types.ObjectId, ref: 'DailyTask', required: true },
  date: { type: String, required: true }, // YYYY-MM-DD
  progress: { type: Number, default: 0 },
  completed: { type: Boolean, default: false },
  claimed: { type: Boolean, default: false },
  claimedAt: { type: Date, default: null }
}, { timestamps: true });

userTaskProgressSchema.index({ userId: 1, taskId: 1, date: 1 }, { unique: true });

module.exports = {
  DailyTask: mongoose.model('DailyTask', dailyTaskSchema),
  UserTaskProgress: mongoose.model('UserTaskProgress', userTaskProgressSchema)
};
