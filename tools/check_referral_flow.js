// Check referral flow - who joined via which code
require('dotenv').config({ path: '/var/www/yt-sub-exchange/server/.env' });
const mongoose = require('/var/www/yt-sub-exchange/server/node_modules/mongoose');

mongoose.connect(process.env.MONGO_URI).then(async () => {
  const User = require('/var/www/yt-sub-exchange/server/models/User');
  const AdminCode = require('/var/www/yt-sub-exchange/server/models/AdminCode');

  const users = await User.find({}, 'channelName referralCode referralParent adminCodeUsed referredBy coins createdAt').lean();
  console.log('\n=== ALL USERS ===');
  users.forEach(u => {
    console.log(`${u.channelName} | code:${u.referralCode} | parent:${u.referralParent} | adminCode:${u.adminCodeUsed} | coins:${u.coins}`);
  });

  console.log('\n=== ADMIN CODES ===');
  const codes = await AdminCode.find({}, 'code label totalJoined totalClicks refToken').lean();
  codes.forEach(c => {
    console.log(`${c.code} | ${c.label} | joined:${c.totalJoined} | clicks:${c.totalClicks} | token:${c.refToken ? c.refToken.slice(0,8)+'...' : 'NONE'}`);
  });

  // Test getAllSubUsers for each admin code
  const getAllSubUsers = async (parentCode, depth = 0) => {
    const direct = await User.find({ referralParent: parentCode }).lean();
    let all = [...direct];
    for (const u of direct) {
      if (u.referralCode) {
        const sub = await getAllSubUsers(u.referralCode, depth + 1);
        all = all.concat(sub);
      }
    }
    return all;
  };

  console.log('\n=== TREE CHECK ===');
  for (const c of codes) {
    const tree = await getAllSubUsers(c.code);
    console.log(`${c.code}: ${tree.length} users in tree`);
    tree.forEach(u => console.log(`  - ${u.channelName} (parent: ${u.referralParent})`));
  }

  process.exit();
});
