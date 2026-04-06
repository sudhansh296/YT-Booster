const http = require('http');

function loginAttempt(i) {
  const d = JSON.stringify({ secret: 'wrongpass' });
  const r = http.request({
    hostname: 'localhost', port: 5000,
    path: '/ytadm1n_x9k2p7/login', method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Content-Length': d.length }
  }, res => {
    let b = '';
    res.on('data', c => b += c);
    res.on('end', () => console.log('Attempt ' + i + ':', res.statusCode, b.substring(0, 60)));
  });
  r.write(d); r.end();
}

for (let i = 1; i <= 7; i++) {
  setTimeout(() => loginAttempt(i), i * 200);
}
