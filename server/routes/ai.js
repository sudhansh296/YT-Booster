const express = require('express');
const router = express.Router();
const authMiddleware = require('../middleware/auth');

// YT Buddy — smart rule-based AI assistant
// No external API needed — fast & reliable

const RESPONSES = {
  greet: [
    "Namaste! 😊 Kaise ho? Main YT Buddy hoon — YouTube aur app ke baare mein kuch bhi pucho!",
    "Hey! 👋 Kya haal hai? Coins, subscribers, ya kuch aur — sab batao!",
    "Hello! 🤖 Main yahan hoon. Kya help chahiye?"
  ],
  coins: [
    "Coins earn karne ke 3 tarike hain:\n1️⃣ Exchange tab mein doosron ko subscribe karo\n2️⃣ Roz daily bonus claim karo\n3️⃣ Friends ko refer karo — 20 coins per referral! 💰",
    "Coins fast earn karne ke liye Exchange tab use karo. Jitna zyada subscribe karoge, utne zyada coins milenge! 🚀"
  ],
  subscribers: [
    "Subscribers kharidne ke liye:\n1. Home screen pe jao\n2. 'Buy Subscribers' button tap karo\n3. Coins se subscribers milenge!\n\nYaad raho — pehle coins earn karo, phir subscribers lo. 📈",
    "Subscribers exchange system simple hai — aap doosron ko subscribe karo, woh aapko subscribe karenge. Fair deal! ✅"
  ],
  referral: [
    "Referral system:\n🎁 Apna code share karo\n💰 Dost join kare to 20 coins milenge\n📊 Refer tab mein stats dekho\n\nJitne zyada dost, utne zyada coins!",
    "Referral code Refer tab mein milega. Share karo aur coins kamao! 🎉"
  ],
  streak: [
    "Streak maintain karne ke liye roz app open karo aur daily bonus claim karo! 🔥\n\nLonger streak = more bonus coins. Miss mat karna!",
    "Daily bonus roz milta hai. Streak tooti to bonus reset ho jaata hai — isliye roz aao! ⚡"
  ],
  youtube: [
    "YouTube channel grow karne ke tips:\n📌 Consistent upload schedule rakho\n🎯 Thumbnail aur title catchy banao\n💬 Comments ka reply karo\n📊 Analytics dekho aur improve karo",
    "YouTube pe success ke liye quality content + consistency = growth! 🎬"
  ],
  help: [
    "Main in topics pe help kar sakta hoon:\n💰 Coins earn karna\n📈 Subscribers lena\n🎁 Referral system\n🔥 Daily streak\n🎬 YouTube tips\n\nKya jaanna chahte ho?",
    "Pucho kuch bhi! Coins, subscribers, referral, YouTube tips — sab bataunga. 😊"
  ],
  thanks: [
    "Koi baat nahi! 😊 Aur kuch chahiye to batao.",
    "Welcome! 🙌 Koi aur sawaal ho to zaroor pucho.",
    "Khushi hui help karke! 🤖✨"
  ],
  default: [
    "Interesting sawaal! 🤔 Coins, subscribers, referral ya YouTube tips ke baare mein kuch specific pucho — main detail mein bataunga!",
    "Samajh gaya! Agar app ke baare mein kuch jaanna ho — coins, subscribers, streak — to batao. 😊",
    "Acha sawaal hai! Main YT Booster app ka assistant hoon. App se related kuch bhi pucho! 🤖"
  ]
};

function getReply(message, history = []) {
  const msg = message.toLowerCase().trim();
  
  // Greetings
  if (/^(hi|hello|hey|namaste|hii|helo|hlo|sup|yo|kya haal|kaise ho|good morning|good evening|gm|ge)/.test(msg)) {
    return pick(RESPONSES.greet);
  }
  
  // Thanks
  if (/(thank|thanks|shukriya|dhanyawad|thx|ty\b)/.test(msg)) {
    return pick(RESPONSES.thanks);
  }
  
  // Coins
  if (/(coin|paise|earn|kamao|money|balance|wallet)/.test(msg)) {
    return pick(RESPONSES.coins);
  }
  
  // Subscribers
  if (/(subscriber|subscribe|sub\b|channel grow|views|watch)/.test(msg)) {
    return pick(RESPONSES.subscribers);
  }
  
  // Referral
  if (/(refer|referral|code|invite|friend|dost)/.test(msg)) {
    return pick(RESPONSES.referral);
  }
  
  // Streak
  if (/(streak|daily|bonus|roz|everyday|login)/.test(msg)) {
    return pick(RESPONSES.streak);
  }
  
  // YouTube tips
  if (/(youtube|yt|video|thumbnail|title|upload|content|creator)/.test(msg)) {
    return pick(RESPONSES.youtube);
  }
  
  // Help
  if (/(help|kya kar|kaise|how|what|kya hai|explain|batao|bata)/.test(msg)) {
    return pick(RESPONSES.help);
  }
  
  // Default
  return pick(RESPONSES.default);
}

function pick(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

router.post('/chat', authMiddleware, async (req, res) => {
  try {
    const { message, history = [] } = req.body;
    if (!message) return res.status(400).json({ error: 'message required' });
    
    const reply = getReply(message, history);
    res.json({ reply, success: true });
  } catch (e) {
    res.status(500).json({ error: e.message });
  }
});

module.exports = router;
