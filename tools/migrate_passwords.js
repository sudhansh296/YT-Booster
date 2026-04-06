// Migrate plain-text passwords to bcrypt hashes
require('dotenv').config({ path: '/var/www/yt-sub-exchange/server/.env' });
const mongoose = require('/var/www/yt-sub-exchange/server/node_modules/mongoose');
const bcrypt = require('/var/www/yt-sub-exchange/server/node_modules/bcrypt');

mongoose.connect(process.env.MONGO_URI).then(async () => {
  const AdminCode = require('/var/www/yt-sub-exchange/server/models/AdminCode');
  const codes = await AdminCode.find({ password: { $exists: true, $ne: null, $ne: '' } });

  for (const c of codes) {
    // Skip if no password or already hashed
    if (!c.password || c.password.startsWith('$2b$')) {
      console.log(c.code, '| skipping');
      continue;
    }
    const hashed = await bcrypt.hash(c.password, 10);
    await AdminCode.findByIdAndUpdate(c._id, { password: hashed });
    console.log(c.code, '| migrated:', c.password, '->', hashed.slice(0, 20) + '...');
  }

  console.log('\nDone! All passwords hashed.');
  process.exit();
});
