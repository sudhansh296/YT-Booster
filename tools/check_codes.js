require("dotenv").config({path:"/var/www/yt-sub-exchange/server/.env"});
const mongoose=require("/var/www/yt-sub-exchange/server/node_modules/mongoose");
mongoose.connect(process.env.MONGO_URI).then(async()=>{
  const AdminCode=require("/var/www/yt-sub-exchange/server/models/AdminCode");
  const codes=await AdminCode.find({},{code:1,password:1,isActive:1,label:1});
  codes.forEach(c=>console.log(c.code,"|",c.label,"|",c.isActive?"active":"inactive","|",c.password?"HAS_PASS:"+c.password:"NO_PASS"));
  process.exit();
});
