const jwt = require('jsonwebtoken');
const User = require('../models/User');

module.exports = async (req, res, next) => {
  const token = req.headers.authorization?.split(' ')[1];
  if (!token) return res.status(401).json({ error: 'No token' });
  try {
    const decoded = jwt.verify(token, process.env.JWT_SECRET);
    req.user = await User.findById(decoded.userId);
    if (!req.user || req.user.isBanned) return res.status(403).json({ error: 'Banned or not found' });
    next();
  } catch {
    res.status(401).json({ error: 'Invalid token' });
  }
};
