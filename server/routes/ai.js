const express = require('express');
const router = express.Router();
const authMiddleware = require('../middleware/auth');
const axios = require('axios');

const SYSTEM_PROMPT = `You are "YT Buddy" - a friendly AI assistant inside YT Booster app.
Speak in Hindi, English, or Hinglish based on what the user uses.
Be warm, helpful, and concise. Help with: YouTube tips, coins, subscribers, referral, streaks, and general questions.
Keep responses short and friendly. Max 3-4 sentences.`;

// Try Groq first (fast + free), fallback to Gemini
async function callGroq(message, history) {
  const GROQ_API_KEY = process.env.GROQ_API_KEY;
  if (!GROQ_API_KEY) throw new Error('No Groq key');

  const messages = [{ role: 'system', content: SYSTEM_PROMPT }];
  
  // Add history (last 8 messages)
  history.slice(-8).forEach(msg => {
    messages.push({ role: msg.role === 'user' ? 'user' : 'assistant', content: msg.text });
  });
  messages.push({ role: 'user', content: message });

  const response = await axios.post('https://api.groq.com/openai/v1/chat/completions', {
    model: 'llama-3.3-70b-versatile',
    messages,
    max_tokens: 512,
    temperature: 0.8
  }, {
    headers: { 'Authorization': `Bearer ${GROQ_API_KEY}`, 'Content-Type': 'application/json' },
    timeout: 15000
  });

  return response.data?.choices?.[0]?.message?.content || null;
}

async function callGemini(message, history) {
  const GEMINI_API_KEY = process.env.GEMINI_API_KEY;
  if (!GEMINI_API_KEY) throw new Error('No Gemini key');

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

  return response.data?.candidates?.[0]?.content?.parts?.[0]?.text || null;
}

router.post('/chat', authMiddleware, async (req, res) => {
  try {
    const { message, history = [] } = req.body;
    if (!message) return res.status(400).json({ error: 'message required' });

    let reply = null;

    // Try Groq first
    try {
      reply = await callGroq(message, history);
      if (reply) {
        console.log('[AI] Groq responded OK');
        return res.json({ reply, success: true, model: 'groq' });
      }
    } catch (groqErr) {
      console.warn('[AI] Groq failed:', groqErr.response?.data?.error?.message || groqErr.message);
    }

    // Fallback to Gemini
    try {
      reply = await callGemini(message, history);
      if (reply) {
        console.log('[AI] Gemini responded OK');
        return res.json({ reply, success: true, model: 'gemini' });
      }
    } catch (geminiErr) {
      console.warn('[AI] Gemini failed:', geminiErr.response?.data?.error?.message || geminiErr.message);
    }

    // Both failed
    res.status(500).json({ error: 'AI se connect nahi ho pa raha, thodi der baad try karo!' });

  } catch (e) {
    console.error('[AI] Unexpected error:', e.message);
    res.status(500).json({ error: 'Kuch gadbad ho gayi, dobara try karo!' });
  }
});

module.exports = router;
