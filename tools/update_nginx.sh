#!/bin/bash
cat > /etc/nginx/sites-enabled/yt-sub-exchange << 'EOF'
server {
    listen 80;
    server_name 80.211.207.252 api.picrypto.in;

    location /ytadm1n_x9k2p7/assets/ {
        alias /var/www/yt-sub-exchange/admin/assets/;
    }

    location = /ytadm1n_x9k2p7/ {
        alias /var/www/yt-sub-exchange/admin/;
        try_files index.html =404;
    }

    location = /ytadm1n_x9k2p7 {
        return 301 /ytadm1n_x9k2p7/;
    }

    location = /subadm1n_m3r8q5/ {
        alias /var/www/yt-sub-exchange/subadmin/;
        try_files index.html =404;
    }

    location = /subadm1n_m3r8q5 {
        return 301 /subadm1n_m3r8q5/;
    }

    location = /admin { return 404; }
    location /admin/ { return 404; }
    location = /subadmin { return 404; }
    location /subadmin/ { return 404; }

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
EOF
nginx -t && systemctl reload nginx && echo "Nginx OK"
