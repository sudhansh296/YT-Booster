const express = require('express');
const router = express.Router();
const authMiddleware = require('../middleware/auth');
const axios = require('axios');

const SYSTEM_PROMPT = `You are "YT Buddy" - a friendly AI assistant inside YT Booster app.
Speak in Hindi, English, or Hinglish based on what the user uses.
Be warm, helpful, and concise. Help with: YouTube tips, coins, subscribers, referral, streaks, and general questions.
Keep responses short and friendly.`;

router.post('/chat', authMiddleware, async (req, res) => {
  try {
    const { message, history = [] } = req.body;
    if (!message) return res.status(400).json({ error: 'message required' });

    const GEMINI_API_KEY = process.env.GEMINI_API_KEY;
    if (!GEMINI_API_KEY) {
      return res.status(500).json({ error: 'AI not configured' });
    }

    const GEMINI_URL = `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=${GEMINI_API_KEY}`;

    const contents = [];
    if (history.length === 0) {
      contents.push({ role: 'user', parts: [{ text: SYSTEM_PROMPT + '\n\nUser: ' + message }] });
    } else {
      history.slice(-8).forEach(msg => {
        contents.push({ role: msg.role === 'user' ? 'user' : 'model', parts: [{ text: msg.text }] });
      });
      contents.push({ role: 'user', parts: [{ text: message }] });
    }

    const response = await axios.post(GEMINI_URL, {
      contents,
      generationConfig: { temperature: 0.8, maxOutputTokens: 512 }
    }, { timeout: 12000 });

    const reply = response.data?.candidates?.[0]?.content?.parts?.[0]?.text || 'Kuch samajh nahi aaya, dobara try karo!';
    res.json({ reply, success: true });
  } catch (e) {
    console.error('[AI] Error:', e.response?.data || e.message);
    res.status(500).json({ error: 'AI se connect nahi ho pa raha' });
  }
});

module.exports = router;
