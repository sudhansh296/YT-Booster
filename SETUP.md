# Setup Guide

## 1. YouTube API Setup
- Go to https://console.cloud.google.com
- Create project → Enable "YouTube Data API v3"
- Create OAuth 2.0 credentials (Web Application)
- Add redirect URI: http://YOUR_SERVER_IP:5000/auth/youtube/callback
- Copy Client ID and Client Secret

## 2. Server Setup (VPS)
```bash
# Install Node.js and MongoDB
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt-get install -y nodejs mongodb
sudo systemctl start mongodb

# Clone and setup
cd /var/www
git clone <your-repo> yt-sub-exchange
cd yt-sub-exchange/server
cp .env.example .env
# Edit .env with your values
nano .env

npm install
npm install -g pm2
pm2 start ecosystem.config.js
pm2 save
pm2 startup
```

## 3. Admin Panel Deploy (on same VPS)
```bash
cd /var/www/yt-sub-exchange/admin
# Edit src/api.js - replace YOUR_SERVER_IP
npm install
npm run build
# Serve with nginx or: npx serve dist -p 3000
```

## 4. Android App
- Open android/ folder in Android Studio
- Edit app/src/main/java/com/ytsubexchange/network/RetrofitClient.kt
- Replace YOUR_SERVER_IP with your actual VPS IP
- Build → Generate Signed APK

## 5. .env values to fill
```
PORT=5000
MONGO_URI=mongodb://localhost:27017/ytsubexchange
JWT_SECRET=make_this_random_32chars
YOUTUBE_CLIENT_ID=from_google_console
YOUTUBE_CLIENT_SECRET=from_google_console
YOUTUBE_REDIRECT_URI=http://YOUR_VPS_IP:5000/auth/youtube/callback
ADMIN_SECRET=your_strong_admin_password
```
