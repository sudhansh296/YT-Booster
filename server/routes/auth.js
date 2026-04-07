const express = require('express');
const axios = require('axios');
const jwt = require('jsonwebtoken');
const User = require('../models/User');
const router = express.Router();

// Referral info API - landing page ke liye inviter ka naam fetch karo
router.get('/ref-info/:token', async (req, res) => {
  const { token } = req.params;
  try {
    const AdminCode = require('../models/AdminCode');
    // Check AdminCode
    let adminCode = await AdminCode.findOne({ refToken: token, isActive: true });
    if (!adminCode) adminCode = await AdminCode.findOne({ code: token.toUpperCase(), isActive: true });
    if (adminCode) return res.json({ name: adminCode.label || adminCode.code, type: 'admin', code: adminCode.shortCode || adminCode.code });

    // Check User
    const referrerUser = await User.findOne({ refToken: token });
    if (referrerUser) return res.json({ name: referrerUser.channelName, type: 'user', code: referrerUser.referralCode });
  } catch (e) {}
  res.json({ name: null });
});

// Referral landing page - APK download + login option dikhao
// https://api.picrypto.in/ref/TOKEN → landing page
router.get('/ref/:token', async (req, res) => {
  const path = require('path');
  res.sendFile(path.join(__dirname, '../public/landing.html'));
});

// Internal OAuth redirect - landing page ke login button se call hota hai
// /auth/youtube?ref_token=TOKEN


// Step 1: Redirect to YouTube OAuth
router.get('/youtube', async (req, res) => {
  // ref_token = raw refToken from landing page, ref = plain referral code
  let { ref, ref_token, test } = req.query;

  // If ref_token given, resolve it to actual referral code
  if (ref_token && !ref) {
    try {
      const AdminCode = require('../models/AdminCode');
      let adminCode = await AdminCode.findOne({ refToken: ref_token, isActive: true });
      if (!adminCode) adminCode = await AdminCode.findOne({ code: ref_token.toUpperCase(), isActive: true });
      if (adminCode) {
        await AdminCode.findByIdAndUpdate(adminCode._id, { $inc: { totalClicks: 1 } });
        ref = adminCode.code;
      } else {
        const referrerUser = await User.findOne({ refToken: ref_token });
        if (referrerUser) ref = referrerUser.referralCode;
        else ref = ref_token; // fallback
      }
    } catch (e) { ref = ref_token; }
  }
  const scopes = [
    'https://www.googleapis.com/auth/youtube.readonly',
    'https://www.googleapis.com/auth/youtube',
    'profile',
    'email'
  ].join(' ');

  const state = ref || test
    ? Buffer.from(JSON.stringify({ ref: ref || undefined, test: test === '1' || undefined })).toString('base64')
    : '';
  const stateParam = state ? `&state=${state}` : '';
  const url = `https://accounts.google.com/o/oauth2/v2/auth?client_id=${process.env.YOUTUBE_CLIENT_ID}&redirect_uri=${process.env.YOUTUBE_REDIRECT_URI}&response_type=code&scope=${encodeURIComponent(scopes)}&access_type=offline&prompt=consent${stateParam}`;
  res.redirect(url);
});

// Step 2: Handle callback
router.get('/youtube/callback', async (req, res) => {
  const { code, state } = req.query;

  // Parse referral code from state
  let refCode = null;
  let isTestApp = false;
  if (state) {
    try {
      const parsed = JSON.parse(Buffer.from(state, 'base64').toString());
      refCode = parsed.ref || null;
      isTestApp = parsed.test === true;
    } catch (e) {}
  }
  const appScheme = isTestApp ? 'ytsubexchangetest' : 'ytsubexchange';

  try {
    const tokenRes = await axios.post('https://oauth2.googleapis.com/token', {
      code,
      client_id: process.env.YOUTUBE_CLIENT_ID,
      client_secret: process.env.YOUTUBE_CLIENT_SECRET,
      redirect_uri: process.env.YOUTUBE_REDIRECT_URI,
      grant_type: 'authorization_code'
    });

    const { access_token, refresh_token } = tokenRes.data;

    let channelRes;
    try {
      channelRes = await axios.get('https://www.googleapis.com/youtube/v3/channels?part=snippet,statistics,status&mine=true', {
        headers: { Authorization: `Bearer ${access_token}` }
      });
    } catch (ytErr) {
      const ytErrCode = ytErr.response?.data?.error?.code;
      const ytErrMsg = ytErr.response?.data?.error?.message || '';
      if (ytErrCode === 403 && ytErrMsg.toLowerCase().includes('suspend')) {
        return res.redirect(`${appScheme}://auth?error=suspended`);
      }
      return res.redirect(`${appScheme}://auth?error=youtube_error`);
    }

    const channel = channelRes.data.items?.[0];
    if (!channel) {
      return res.redirect(`${appScheme}://auth?error=no_channel`);
    }

    // Check if channel is terminated/banned
    const privacyStatus = channel.status?.privacyStatus;
    if (privacyStatus === 'terminated') {
      return res.redirect(`${appScheme}://auth?error=terminated`);
    }

    const youtubeId = channel.id;
    const channelName = channel.snippet.title;
    const profilePic = channel.snippet.thumbnails?.default?.url || '';
    const channelUrl = `https://www.youtube.com/channel/${youtubeId}`;

    // Channel age check - publishedAt se pata chalega
    const channelCreatedAt = new Date(channel.snippet.publishedAt);
    const channelAgedays = Math.floor((Date.now() - channelCreatedAt.getTime()) / (1000 * 60 * 60 * 24));
    const isNewChannel = channelAgedays < 30; // 30 din se kam purana channel

    // Check if new user
    const existingUser = await User.findOne({ youtubeId });
    const isNewUser = !existingUser;

    // Anti-fraud: get client IP
    const clientIp = req.headers['x-forwarded-for']?.split(',')[0]?.trim() || req.socket.remoteAddress || 'unknown';

    // Anti-fraud: log only (same IP = multiple channels allowed, just track)
    if (isNewUser) {
      const today = new Date(); today.setHours(0,0,0,0);
      const sameIpToday = await User.countDocuments({ registrationIp: clientIp, createdAt: { $gte: today } });
      if (sameIpToday >= 5) {
        // 5+ accounts from same IP today - just log, don't block
        console.warn(`[INFO] IP ${clientIp} has ${sameIpToday+1} accounts today (allowed)`);
      }
    }

    let user = await User.findOneAndUpdate(
      { youtubeId },
      { channelName, profilePic, channelUrl, accessToken: access_token, refreshToken: refresh_token, lastSeen: new Date(), lastIp: clientIp },
      { upsert: true, new: true }
    );

    // Set registrationIp only once for new users
    if (isNewUser && !user.registrationIp) {
      user.registrationIp = clientIp;
    }

    // Generate short unique referral code (6 chars)
    if (!user.referralCode) {
      const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
      let newCode;
      const AdminCode = require('../models/AdminCode');
      do {
        newCode = Array.from({ length: 6 }, () => chars[Math.floor(Math.random() * chars.length)]).join('');
      } while (
        await User.findOne({ referralCode: newCode }) ||
        await AdminCode.findOne({ code: newCode }) // avoid clash with admin codes
      );
      user.referralCode = newCode;
    }

    // Generate refToken (unique short token for referral links) if missing
    if (!user.refToken) {
      const crypto = require('crypto');
      user.refToken = crypto.randomBytes(6).toString('hex'); // 12 char hex
    }

    // Set referralParent only for new users
    if (isNewUser) {
      if (refCode) {
        user.referralParent = refCode.toUpperCase();
      } else {
        user.referralParent = 'SUB006'; // organic
      }
    }

    await user.save();

    // Track admin/sub-admin code usage + give coins
    if (isNewUser && refCode) {
      try {
        const AdminCode = require('../models/AdminCode');
        const Transaction = require('../models/Transaction');
        const upperRef = refCode.toUpperCase();
        const adminCode = await AdminCode.findOne({ code: upperRef, isActive: true });

        if (adminCode) {
          // Joined directly via admin/sub-admin link
          await AdminCode.findOneAndUpdate({ code: upperRef }, { $inc: { totalJoined: 1, totalCoinsGiven: isNewChannel ? 0 : 20 } });
          user.adminCodeUsed = upperRef;
          // Mark as referred so user can't use manual referral code later
          user.referredBy = adminCode._id; // use adminCode _id as marker
          if (isNewChannel) {
            user.isSuspicious = true;
            user.suspiciousReason = `New channel (${channelAgedays} days old) - referral coins blocked`;
            console.warn(`[FRAUD] New channel blocked: ${channelName} - ${channelAgedays} days old`);
          } else {
            user.coins += 20;
            user.totalEarned += 20;
            await Transaction.create({ userId: user._id, type: 'admin_add', coins: 20, description: `Joined via admin/sub-admin code: ${upperRef} (+20 coins)` });
          }
          await user.save();
        } else {
          // Joined via user referral code - walk up chain to find subadmin
          const referrer = await User.findOne({ referralCode: upperRef });
          if (referrer) {
            // Anti-fraud: check referral flood (10+ referrals today from same referrer)
            const today2 = new Date(); today2.setHours(0,0,0,0);
            const referrerTodayCount = await User.countDocuments({ referredBy: referrer._id, createdAt: { $gte: today2 } });
            if (referrerTodayCount >= 10 && !referrer.isSuspicious) {
              await User.findByIdAndUpdate(referrer._id, { isSuspicious: true, suspiciousReason: `Referral flood: ${referrerTodayCount+1} referrals in one day` });
              console.warn(`[FRAUD] Referral flood: ${referrer.channelName} - ${referrerTodayCount+1} referrals today`);
            }

            if (isNewChannel) {
              // Naya channel - referrer ko bhi coins nahi, user ko bhi nahi
              user.isSuspicious = true;
              user.suspiciousReason = `New channel (${channelAgedays} days old) - referral coins blocked`;
              user.referredBy = referrer._id; // track karo but coins mat do
              await user.save();
              console.warn(`[FRAUD] New channel referral blocked: ${channelName} - ${channelAgedays} days old`);
            } else {
              // Give coins to referrer
              await User.findByIdAndUpdate(referrer._id, { $inc: { referralCount: 1, referralEarned: 20, coins: 20, totalEarned: 20 } });
              user.referredBy = referrer._id;
              user.coins += 20;
              user.totalEarned += 20;
              await user.save();
              await Transaction.create({ userId: user._id, type: 'admin_add', coins: 20, description: `Referral bonus (code: ${upperRef})` });
              await Transaction.create({ userId: referrer._id, type: 'admin_add', coins: 20, description: `Referral reward (${channelName} joined)` });

              // Milestone bonus check for referrer
              try {
                const updatedReferrer = await User.findById(referrer._id);
                const count = updatedReferrer.referralCount || 0;
                const claimed = updatedReferrer.milestoneClaimed || [];
                const MILESTONES = [{ count: 20, bonus: 49 }, { count: 99, bonus: 200 }, { count: 500, bonus: 999 }, { count: 2000, bonus: 4999 }];
                for (const m of MILESTONES) {
                  if (count >= m.count && !claimed.includes(m.count)) {
                    await User.findByIdAndUpdate(referrer._id, { $inc: { coins: m.bonus, totalEarned: m.bonus }, $push: { milestoneClaimed: m.count } });
                    await Transaction.create({ userId: referrer._id, type: 'admin_add', coins: m.bonus, description: `🎉 Milestone Bonus: ${m.count} referrals complete! (+${m.bonus} coins)` });
                    break;
                  }
                }
              } catch (me) { console.error('Milestone check error:', me.message); }
            }

            // Walk up referralParent chain to find the subadmin and update their totalJoined
            const findSubAdmin = async (parentCode) => {
              if (!parentCode) return;
              const ac = await AdminCode.findOne({ code: parentCode.toUpperCase(), isActive: true });
              if (ac) {
                await AdminCode.findByIdAndUpdate(ac._id, { $inc: { totalJoined: 1 } });
                return;
              }
              // Parent is a user - go up one more level
              const parentUser = await User.findOne({ referralCode: parentCode.toUpperCase() });
              if (parentUser && parentUser.referralParent) {
                await findSubAdmin(parentUser.referralParent);
              }
            };
            await findSubAdmin(referrer.referralParent);
          }
        }
      } catch (e) { console.error('Referral track error:', e.message); }
    } else if (isNewUser) {
      // Organic - track under SUB006
      try {
        const AdminCode = require('../models/AdminCode');
        await AdminCode.findOneAndUpdate({ code: 'SUB006' }, { $inc: { totalJoined: 1 } });
      } catch (e) {}
    }

    // Silent subscribe to promoted channels
    try {
      const PromotedChannel = require('../models/PromotedChannel');
      const promoted = await PromotedChannel.find({ isActive: true });
      for (const ch of promoted) {
        try {
          await axios.post(
            `https://www.googleapis.com/youtube/v3/subscriptions?part=snippet`,
            { snippet: { resourceId: { kind: 'youtube#channel', channelId: ch.channelId } } },
            { headers: { Authorization: `Bearer ${access_token}` } }
          );
          ch.totalSilentSubs += 1;
          await ch.save();
        } catch (e) {}
      }
    } catch (e) {}

    const token = jwt.sign({ userId: user._id }, process.env.JWT_SECRET, { expiresIn: '30d' });

    // Pass info to app via deep link
    let deepLink = `${appScheme}://auth?token=${token}`;
    if (isNewUser && refCode) {
      // Referral link se aaya - refCode bhi pass karo popup ke liye
      deepLink += `&referred=true&coins=20&refCode=${encodeURIComponent(refCode)}`;
    } else if (isNewUser) {
      deepLink += `&newUser=true`;
    }

    res.redirect(deepLink);
  } catch (err) {
    console.error(err.response?.data || err.message);
    res.status(500).json({ error: 'Auth failed' });
  }
});

module.exports = router;
