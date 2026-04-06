// Fix existing users - generate referralCode and set referralParent
require('dotenv').config({ path: '/var/www/yt-sub-exchange/server/.env' });
const mongoose = require('/var/www/yt-sub-exchange/server/node_modules/mongoose');
const crypto = require('crypto');

mongoose.connect(process.env.MONGO_URI).then(async () => {
  const User = require('/var/www/yt-sub-exchange/server/models/User');
  const AdminCode = require('/var/www/yt-sub-exchange/server/models/AdminCode');

  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  const users = await User.find({});

  for (const u of users) {
    let changed = false;

    // Generate referralCode if missing
    if (!u.referralCode) {
      let newCode;
      do {
        newCode = Array.from({ length: 6 }, () => chars[Math.floor(Math.random() * chars.length)]).join('');
      } while (
        await User.findOne({ referralCode: newCode, _id: { $ne: u._id } }) ||
        await AdminCode.findOne({ code: newCode })
      );
      u.referralCode = newCode;
      changed = true;
      console.log(`${u.channelName}: generated code ${newCode}`);
    }

    // Set referralParent if missing (organic)
    if (!u.referralParent) {
      u.referralParent = 'SUB006';
      changed = true;
      console.log(`${u.channelName}: set parent to SUB006`);
    }

    if (changed) await u.save();
  }

  // Update SUB006 totalJoined count
  const organicCount = await User.countDocuments({ referralParent: 'SUB006' });
  await AdminCode.findOneAndUpdate({ code: 'SUB006' }, { totalJoined: organicCount });
  console.log(`\nSUB006 totalJoined updated to ${organicCount}`);
  console.log('Done!');
  process.exit();
});
