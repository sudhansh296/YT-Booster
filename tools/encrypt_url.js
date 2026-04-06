// Run: node encrypt_url.js "http://YOUR_VPS_IP:5000/"
const url = process.argv[2];
if (!url) { console.log('Usage: node encrypt_url.js "http://YOUR_IP:5000/"'); process.exit(1); }

const KEY = 0x5A;
const encrypted = Buffer.from(url).map(b => b ^ KEY);
const kotlinArray = Array.from(encrypted).map(b => '0x' + b.toString(16).padStart(2, '0')).join(',');

console.log('\nPaste this in RetrofitClient.kt:\n');
console.log(`private val encryptedUrl = byteArrayOf(\n    ${kotlinArray}\n)`);
