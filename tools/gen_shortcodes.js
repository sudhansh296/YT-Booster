const mongoose = require('mongoose');
const AdminCode = require('../server/models/AdminCode');
require('dotenv').config({ path: '../server/.env' });

mongoose.connect(process.env.MONGODB_URI).then(async () => {
  const codes = await AdminCode.find({ shortCode: { $in: [null, undefined, ''] } });
  console.log(`Found ${codes.length} codes without shortCode`);
  for (const c of codes) {
    await c.save(); // pre-save hook will generate shortCode
    const updated = await AdminCode.findById(c._id);
    console.log(`${c.code} -> shortCode: ${updated.shortCode}`);
  }
  mongoose.disconnect();
  console.log('DONE');
});
