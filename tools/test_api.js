const http = require('http');

function testPath(path, body, label) {
  const d = JSON.stringify(body);
  const r = http.request({
    hostname: 'localhost', port: 5000, path, method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Content-Length': d.length }
  }, res => {
    let b = '';
    res.on('data', c => b += c);
    res.on('end', () => console.log(label + ':', res.statusCode, b));
  });
  r.write(d); r.end();
}

testPath('/admin/login', { secret: 'test' }, 'OLD /admin/login');
testPath('/subadmin/login', { code: 'TEST', password: 'test' }, 'OLD /subadmin/login');
testPath('/ytadm1n_x9k2p7/login', { secret: 'wrongpass' }, 'NEW admin login (wrong pass)');
testPath('/subadm1n_m3r8q5/login', { code: 'TEST', password: 'wrong' }, 'NEW subadmin login (wrong pass)');
