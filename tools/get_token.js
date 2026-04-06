require('dotenv').config();
const mongoose = require('mongoose');
mongoose.connect(process.env.MONGO_URI).then(async () => {
  const User = require('../server/models/User');
  const u = await User.findOne({}).lean();
  if (u) {
    const jwt = require('jsonwebtoken');
    const t = jwt.sign({ userId: u._id }, process.env.JWT_SECRET);
    console.log('TOKEN:' + t);
    console.log('USER:' + u.channelName);
  } else {
    console.log('NO_USER');
  }
  mongoose.disconnect();
}).catch(e => { console.error(e.message); process.exit(1); });
