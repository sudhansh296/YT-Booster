// In-memory queue for matchmaking
const queue = [];
const activeMatches = new Map(); // userId -> matchedUserId

function addToQueue(userId, socketId, channelId, accessToken) {
  // Remove if already in queue
  removeFromQueue(userId);
  queue.push({ userId, socketId, channelId, accessToken, joinedAt: Date.now() });
}

function removeFromQueue(userId) {
  const idx = queue.findIndex(u => u.userId === userId);
  if (idx !== -1) queue.splice(idx, 1);
}

function findMatch(userId) {
  return queue.find(u => u.userId !== userId);
}

function getQueueSize() {
  return queue.length;
}

function getQueue() {
  return [...queue];
}

module.exports = { addToQueue, removeFromQueue, findMatch, getQueueSize, getQueue, activeMatches };
