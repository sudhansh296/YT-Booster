with open('yt-sub-exchange/prototype/full-preview.html', 'r', encoding='utf-8') as f:
    content = f.read()

old_start = "function renderDMMsg(from, text, color, isGroup) {"
old_end = "function initUcChat() { initChatList(); }"

s = content.index(old_start)
e = content.index(old_end)
before = content[:s]
after = content[e:]

new_funcs = """let msgIdCounter = 0;
let selectedMsgEl = null;
let replyTo = null;
let currentIsGroup = false;

function renderDMMsg(from, text, color, isGroup, replyRef) {
  const msgs = document.getElementById('dm-msgs');
  const isMe = from === 'me';
  const u = ucUsers.find(x => x.name === from);
  const avColor = isMe ? '#e63946' : (u ? u.color : color);
  const msgId = 'msg-' + (++msgIdCounter);
  const wrapper = document.createElement('div');
  wrapper.id = msgId;
  wrapper.style.cssText = 'display:flex;gap:6px;align-items:flex-end;margin-bottom:4px;' + (isMe ? 'flex-direction:row-reverse;' : '');
  const avHtml = !isMe ? '<div style="width:26px;height:26px;border-radius:50%;background:linear-gradient(135deg,' + avColor + ',#111);display:flex;align-items:center;justify-content:center;font-size:11px;flex-shrink:0;align-self:flex-end">&#128100;</div>' : '<div style="width:26px;flex-shrink:0"></div>';
  const nameTag = (!isMe && isGroup) ? '<div style="font-size:9px;color:' + avColor + ';font-weight:600;margin-bottom:2px">' + from + '</div>' : '';
  const replyHtml = replyRef ? '<div style="background:rgba(255,255,255,0.05);border-left:2px solid #e63946;padding:3px 7px;border-radius:5px;margin-bottom:4px;font-size:9px;color:#aaa;max-width:180px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">' + replyRef + '</div>' : '';
  const bubbleStyle = isMe ? 'background:linear-gradient(135deg,#c0392b,#e63946);color:#fff;border-radius:16px 16px 4px 16px;' : 'background:#1e1e2e;color:#ddd;border-radius:16px 16px 16px 4px;';
  const ticks = isMe ? '<span class="msg-tick" style="font-size:9px;color:rgba(255,255,255,0.5);margin-left:3px">&#10003;&#10003;</span>' : '';
  const timeStr = new Date().toLocaleTimeString([],{hour:'2-digit',minute:'2-digit'});
  wrapper.innerHTML = avHtml + '<div style="max-width:210px">' + nameTag + '<div style="padding:8px 11px 5px;' + bubbleStyle + 'box-shadow:0 1px 4px rgba(0,0,0,0.3)">' + replyHtml + '<div style="font-size:11px;line-height:1.5;word-break:break-word">' + text + '</div><div style="display:flex;align-items:center;justify-content:flex-end;gap:2px;margin-top:1px"><span style="font-size:9px;color:rgba(255,255,255,0.35)">' + timeStr + '</span>' + ticks + '</div></div><div id="react-' + msgId + '" style="display:flex;gap:3px;flex-wrap:wrap;margin-top:2px"></div></div>';
  wrapper.addEventListener('contextmenu', function(e) { e.preventDefault(); showCtxMenu(e, msgId, text, isMe); });
  wrapper.addEventListener('touchstart', function(e) { const t = setTimeout(() => showCtxMenu(e.touches[0], msgId, text, isMe), 500); wrapper.addEventListener('touchend', () => clearTimeout(t), {once:true}); });
  msgs.appendChild(wrapper);
  msgs.scrollTop = msgs.scrollHeight;
  if (isMe) { setTimeout(() => { const tick = wrapper.querySelector('.msg-tick'); if (tick) tick.style.color = '#29b6f6'; }, 1200); }
}

function dmSend() {
  const inp = document.getElementById('dm-inp');
  const txt = inp.value.trim();
  if (!txt || !currentDM) return;
  inp.value = '';
  toggleVoiceBtn();
  const replyRef = replyTo;
  cancelReply();
  if (!dmHistory[currentDM]) dmHistory[currentDM] = [];
  dmHistory[currentDM].push({from:'me', text:txt});
  renderDMMsg('me', txt, '#e63946', currentIsGroup, replyRef);
  const typing = document.getElementById('dm-typing');
  typing.style.display = 'block';
  const u = ucUsers.find(x => x.id === currentDM);
  setTimeout(() => {
    typing.style.display = 'none';
    const replies = ['Haan bhai bilkul!','Sahi keh rahe ho yaar','Main bhi try karunga','Mera channel bhi dekho','Subscribe kar diya','Nice! Keep it up','Aaj kitne coins mile tumhe?'];
    const reply = u ? u.msgs[Math.floor(Math.random()*u.msgs.length)] : replies[Math.floor(Math.random()*replies.length)];
    dmHistory[currentDM].push({from: u?u.name:'User', text:reply});
    renderDMMsg(u?u.name:'User', reply, u?u.color:'#555', currentIsGroup, null);
  }, 800 + Math.random()*700);
}

function toggleVoiceBtn() {
  const val = document.getElementById('dm-inp') && document.getElementById('dm-inp').value.trim();
  const vb = document.getElementById('voice-btn');
  const sb = document.getElementById('send-btn');
  if (vb) vb.style.display = val ? 'none' : 'flex';
  if (sb) sb.style.display = val ? 'flex' : 'none';
}

function showCtxMenu(e, msgId, text, isMe) {
  selectedMsgEl = {id: msgId, text, isMe};
  const menu = document.getElementById('dm-ctx-menu');
  menu.style.display = 'block';
  const phone = document.querySelector('.phone').getBoundingClientRect();
  let x = Math.min((e.clientX||e.pageX), phone.right - 175);
  let y = Math.min((e.clientY||e.pageY), phone.bottom - 210);
  x = Math.max(x, phone.left + 5);
  menu.style.left = x + 'px';
  menu.style.top = y + 'px';
  setTimeout(() => document.addEventListener('click', closeCtxMenu, {once:true}), 100);
}
function closeCtxMenu() {
  const m = document.getElementById('dm-ctx-menu');
  const r = document.getElementById('react-picker');
  if (m) m.style.display = 'none';
  if (r) r.style.display = 'none';
}

function ctxReply() {
  closeCtxMenu();
  if (!selectedMsgEl) return;
  replyTo = selectedMsgEl.text.substring(0, 40) + (selectedMsgEl.text.length > 40 ? '...' : '');
  document.getElementById('reply-bar').style.display = 'flex';
  document.getElementById('reply-text').textContent = replyTo;
  document.getElementById('dm-inp').focus();
}
function cancelReply() {
  replyTo = null;
  const rb = document.getElementById('reply-bar');
  if (rb) rb.style.display = 'none';
}

function ctxReact() {
  closeCtxMenu();
  const picker = document.getElementById('react-picker');
  picker.style.display = 'flex';
  const phone = document.querySelector('.phone').getBoundingClientRect();
  picker.style.left = (phone.left + 15) + 'px';
  picker.style.top = (phone.top + 320) + 'px';
  setTimeout(() => document.addEventListener('click', () => { picker.style.display = 'none'; }, {once:true}), 100);
}
function addReaction(emoji) {
  document.getElementById('react-picker').style.display = 'none';
  if (!selectedMsgEl) return;
  const reactDiv = document.getElementById('react-' + selectedMsgEl.id);
  if (!reactDiv) return;
  const existing = Array.from(reactDiv.children).find(c => c.dataset.emoji === emoji);
  if (existing) { const s = existing.querySelector('span'); s.textContent = parseInt(s.textContent) + 1; return; }
  const pill = document.createElement('div');
  pill.dataset.emoji = emoji;
  pill.style.cssText = 'background:#1a1a2e;border:1px solid #2a2a3e;border-radius:10px;padding:2px 6px;font-size:11px;display:flex;align-items:center;gap:3px;cursor:pointer;';
  pill.innerHTML = emoji + '<span style="font-size:9px;color:#aaa">1</span>';
  pill.onclick = () => { const s = pill.querySelector('span'); s.textContent = parseInt(s.textContent) + 1; };
  reactDiv.appendChild(pill);
}

function ctxPin() {
  closeCtxMenu();
  if (!selectedMsgEl) return;
  const bar = document.getElementById('dm-pinned');
  bar.style.display = 'flex';
  document.getElementById('dm-pinned-text').textContent = selectedMsgEl.text;
  toast('Message pinned!');
}
function ctxCopy() {
  closeCtxMenu();
  if (selectedMsgEl) toast('Copied!');
}
function ctxDelete() {
  closeCtxMenu();
  if (!selectedMsgEl) return;
  const el = document.getElementById(selectedMsgEl.id);
  if (el) { el.style.opacity = '0.3'; el.style.transition = 'opacity 0.3s'; setTimeout(() => el.remove(), 300); }
  toast('Message deleted');
}
function showDMInfo() { toast('Profile info — coming soon!'); }
function showDMMenu() { toast('More options — coming soon!'); }

"""

content = before + new_funcs + after
with open('yt-sub-exchange/prototype/full-preview.html', 'w', encoding='utf-8') as f:
    f.write(content)
print("Done, length:", len(content))
