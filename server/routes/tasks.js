const express = require('express');
const router = express.Router();
const authMiddleware = require('../middleware/auth');
const { DailyTask, UserTaskProgress } = require('../models/DailyTask');
const User = require('../models/User');
const Transaction = require('../models/Transaction');

function todayStr() {
  return new Date().toISOString().split('T')[0];
}

// ── Get today's tasks with user progress ─────────────────────
router.get('/today', authMiddleware, async (req, res) => {
  try {
    const tasks = await DailyTask.find({ isActive: true }).lean();
    const today = todayStr();
    const progresses = await UserTaskProgress.find({
      userId: req.user._id,
      date: today
    }).lean();
    const progMap = Object.fromEntries(progresses.map(p => [p.taskId.toString(), p]));

    const result = tasks.map(task => {
      const prog = progMap[task._id.toString()];
      return {
        ...task,
        progress: prog?.progress || 0,
        completed: prog?.completed || false,
        claimed: prog?.claimed || false
      };
    });

    res.json({ tasks: result, date: today });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ── Claim reward for completed task ──────────────────────────
router.post('/:taskId/claim', authMiddleware, async (req, res) => {
  try {
    const today = todayStr();
    const task = await DailyTask.findById(req.params.taskId);
    if (!task) return res.status(404).json({ error: 'Task not found' });

    let prog = await UserTaskProgress.findOne({
      userId: req.user._id,
      taskId: task._id,
      date: today
    });

    if (!prog || !prog.completed) return res.status(400).json({ error: 'Task not completed yet' });
    if (prog.claimed) return res.status(400).json({ error: 'Already claimed' });

    // Give coins
    const user = await User.findByIdAndUpdate(
      req.user._id,
      { $inc: { coins: task.coinReward, totalEarned: task.coinReward } },
      { new: true }
    );

    prog.claimed = true;
    prog.claimedAt = new Date();
    await prog.save();

    await Transaction.create({
      userId: req.user._id,
      type: 'task_reward',
      coins: task.coinReward,
      description: `Daily task: ${task.title}`
    });

    res.json({ success: true, coins: user.coins, earned: task.coinReward });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ── Update task progress (called internally) ─────────────────
router.post('/progress', authMiddleware, async (req, res) => {
  try {
    const { type, count = 1 } = req.body;
    const today = todayStr();
    const tasks = await DailyTask.find({ isActive: true, type }).lean();

    for (const task of tasks) {
      let prog = await UserTaskProgress.findOne({
        userId: req.user._id, taskId: task._id, date: today
      });
      if (!prog) {
        prog = new UserTaskProgress({
          userId: req.user._id, taskId: task._id, date: today, progress: 0
        });
      }
      if (prog.completed) continue;
      prog.progress = Math.min(prog.progress + count, task.targetCount);
      if (prog.progress >= task.targetCount) prog.completed = true;
      await prog.save();
    }

    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ── Auto-complete login task on app open ─────────────────────
router.post('/login-task', authMiddleware, async (req, res) => {
  try {
    const today = todayStr();
    const loginTasks = await DailyTask.find({ isActive: true, type: 'login' }).lean();
    for (const task of loginTasks) {
      const existing = await UserTaskProgress.findOne({
        userId: req.user._id, taskId: task._id, date: today
      });
      if (!existing) {
        await UserTaskProgress.create({
          userId: req.user._id, taskId: task._id, date: today,
          progress: task.targetCount, completed: true
        });
      }
    }
    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

// ── Admin: Get all tasks ──────────────────────────────────────
const adminAuth = (req, res, next) => {
  const secret = req.headers['x-admin-secret'];
  if (secret !== process.env.ADMIN_SECRET) return res.status(403).json({ error: 'Forbidden' });
  next();
};

router.get('/admin/all', adminAuth, async (req, res) => {
  try {
    const tasks = await DailyTask.find().sort({ createdAt: -1 }).lean();
    // Get completion stats for today
    const today = todayStr();
    const stats = await UserTaskProgress.aggregate([
      { $match: { date: today } },
      { $group: { _id: '$taskId', completed: { $sum: { $cond: ['$completed', 1, 0] } }, claimed: { $sum: { $cond: ['$claimed', 1, 0] } } } }
    ]);
    const statsMap = Object.fromEntries(stats.map(s => [s._id.toString(), s]));
    res.json(tasks.map(t => ({ ...t, todayStats: statsMap[t._id.toString()] || { completed: 0, claimed: 0 } })));
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

router.post('/admin/create', adminAuth, async (req, res) => {
  try {
    const { title, description, type, targetCount, coinReward, icon } = req.body;
    if (!title) return res.status(400).json({ error: 'Title required' });
    const task = await DailyTask.create({ title, description, type, targetCount, coinReward, icon });
    res.json({ success: true, task });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

router.put('/admin/:id', adminAuth, async (req, res) => {
  try {
    const task = await DailyTask.findByIdAndUpdate(req.params.id, req.body, { new: true });
    res.json({ success: true, task });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

router.delete('/admin/:id', adminAuth, async (req, res) => {
  try {
    await DailyTask.findByIdAndDelete(req.params.id);
    res.json({ success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

module.exports = router;
