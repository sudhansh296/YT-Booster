// Generate refToken for all AdminCodes that don't have one
require('dotenv').config({ path: '/var/www/yt-sub-exchange/server/.env' });
const mongoose = require('/var/www/yt-sub-exchange/server/node_modules/mongoose');
const crypto = require('crypto');

mongoose.connect(process.env.MONGO_URI).then(async () => {
  const AdminCode = require('/var/www/yt-sub-exchange/server/models/AdminCode');
  const codes = await AdminCode.find({});
  for (const c of codes) {
    if (!c.refToken) {
      c.refToken = crypto.randomBytes(16).toString('hex');
      await c.save();
      console.log(c.code, '-> token:', c.refToken);
    } else {
      console.log(c.code, '-> already has token:', c.refToken);
    }
  }
  console.log('Done!');
  process.exit();
});
