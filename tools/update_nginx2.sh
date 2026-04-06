#!/bin/bash
cat > /etc/nginx/sites-enabled/api.picrypto.in << 'EOF'
server {
    listen 80;
    server_name api.picrypto.in;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    server_name api.picrypto.in;

    ssl_certificate /etc/letsencrypt/live/api.picrypto.in/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.picrypto.in/privkey.pem;
    include /etc/letsencrypt/options-ssl-nginx.conf;
    ssl_dhparam /etc/letsencrypt/ssl-dhparams.pem;

    # Secret admin panel path
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

    # Secret subadmin panel path
    location = /subadm1n_m3r8q5/ {
        alias /var/www/yt-sub-exchange/subadmin/;
        try_files index.html =404;
    }

    location = /subadm1n_m3r8q5 {
        return 301 /subadm1n_m3r8q5/;
    }

    # Block old paths
    location = /admin { return 404; }
    location /admin/ { return 404; }
    location = /subadmin { return 404; }
    location /subadmin/ { return 404; }

    # Referral token links
    location /ref/ {
        rewrite ^/ref/(.*)$ /auth/ref/$1 break;
        proxy_pass http://localhost:5000;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # APK download
    location /download/ {
        alias /var/www/yt-sub-exchange/server/public/download/;
        add_header Content-Disposition attachment;
        add_header Cache-Control no-cache;
        sendfile on;
        tcp_nopush on;
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
EOF
nginx -t && systemctl reload nginx && echo "Nginx OK"
