const AdminCode = require('./models/AdminCode');
const mongoose = require('mongoose');
require('dotenv').config();

mongoose.connect(process.env.MONGO_URI || process.env.MONGODB_URI).then(async () => {
  const codes = await AdminCode.find({});
  let count = 0;
  for (const c of codes) {
    if (!c.shortCode) {
      await c.save();
      const updated = await AdminCode.findById(c._id);
      console.log(c.code + ' -> ' + updated.shortCode);
      count++;
    } else {
      console.log(c.code + ' already has: ' + c.shortCode);
    }
  }
  console.log('Updated: ' + count);
  mongoose.disconnect();
});
