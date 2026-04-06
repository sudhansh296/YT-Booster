#!/bin/bash
cat > /etc/nginx/sites-enabled/api.picrypto.in << 'NGINXEOF'
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

    # /ref/TOKEN → proxy to /auth/ref/TOKEN on backend
    location /ref/ {
        rewrite ^/ref/(.*)$ /auth/ref/$1 break;
        proxy_pass http://localhost:5000;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
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
