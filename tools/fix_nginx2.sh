#!/bin/bash
cat > /etc/nginx/sites-enabled/yt-sub-exchange << 'NGINXEOF'
server {
    listen 80;
    server_name 80.211.207.252;

    location /admin/assets/ {
        alias /var/www/yt-sub-exchange/admin/assets/;
    }

    location = /admin/ {
        alias /var/www/yt-sub-exchange/admin/;
        try_files index.html =404;
    }

    location = /admin {
        return 301 /admin/;
    }

    location = /subadmin/ {
        alias /var/www/yt-sub-exchange/subadmin/;
        try_files index.html =404;
    }

    location = /subadmin {
        return 301 /subadmin/;
    }

    location / {
        proxy_pass http://localhost:5000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection upgrade;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_cache_bypass $http_upgrade;
    }
}
NGINXEOF

nginx -t && systemctl reload nginx && echo "NGINX OK"
