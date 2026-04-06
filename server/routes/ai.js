const express = require('express');
const router = express.Router();
const authMiddleware = require('../middleware/auth');
const axios = require('axios');

const GEMINI_API_KEY = process.env.GEMINI_API_KEY;
const GEMINI_URL = `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=${GEMINI_API_KEY}`;

const SYSTEM_PROMPT = `You are "YT Buddy" - a friendly AI companion inside YT Booster app. 
You are designed to be a personal friend and confidant to users.

Your personality:
- Warm, empathetic, and genuinely interested in the user
- Conversational and natural - like talking to a close friend
- Supportive and encouraging
- Funny and witty when appropriate
- Non-judgmental and open-minded

You help users with:
- Personal conversations and life advice
- Emotional support and motivation
- YouTube tips and strategies
- Coding and technical help
- General knowledge and learning
- Fun conversations and jokes
- Career guidance
- Relationship advice
- Mental health support (basic)
- Anything else they want to talk about

Guidelines:
- Be concise but complete in your responses
- Speak in Hindi, English, or Hinglish based on what the user uses
- Remember context from the conversation
- Show genuine interest in what they're saying
- Ask follow-up questions when appropriate
- Be authentic and avoid robotic responses
- Treat personal topics with sensitivity and care
- You are part of YT Booster - a YouTube subscriber exchange platform, but that's secondary to being a good friend`;

router.post('/chat', authMiddleware, async (req, res) => {
  try {
    const { message, history = [] } = req.body;
    if (!message) return res.status(400).json({ error: 'message required' });

    // Build conversation history for Gemini
    const contents = [];
    
    // Add system context as first user message
    if (history.length === 0) {
      contents.push({
        role: 'user',
        parts: [{ text: SYSTEM_PROMPT + '\n\nUser: ' + message }]
      });
    } else {
      // Add history
      history.slice(-10).forEach(msg => {
        contents.push({
          role: msg.role === 'user' ? 'user' : 'model',
          parts: [{ text: msg.text }]
        });
      });
      contents.push({ role: 'user', parts: [{ text: message }] });
    }

    const response = await axios.post(GEMINI_URL, {
      contents,
      generationConfig: {
        temperature: 0.8,
        topK: 40,
        topP: 0.95,
        maxOutputTokens: 2048,
      },
      safetySettings: [
        { category: 'HARM_CATEGORY_HARASSMENT', threshold: 'BLOCK_MEDIUM_AND_ABOVE' },
        { category: 'HARM_CATEGORY_HATE_SPEECH', threshold: 'BLOCK_MEDIUM_AND_ABOVE' },
      ]
    }, { timeout: 15000 });

    const reply = response.data?.candidates?.[0]?.content?.parts?.[0]?.text || 'Kuch samajh nahi aaya, dobara try karo!';
    res.json({ reply, success: true });
  } catch (e) {
    console.error('[AI] Error:', e.response?.data || e.message);
    const errMsg = e.response?.status === 429 ? 'AI busy hai, thodi der baad try karo' :
                   e.response?.status === 400 ? 'Message send nahi ho saka' :
                   'AI se connect nahi ho pa raha';
    res.status(500).json({ error: errMsg });
  }
});

module.exports = router;
