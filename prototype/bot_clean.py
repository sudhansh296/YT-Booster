import logging
import aiohttp
import json
import time
from telegram import Update, InlineKeyboardButton, InlineKeyboardMarkup, ReplyKeyboardMarkup, KeyboardButton
from telegram.ext import Application, CommandHandler, CallbackQueryHandler, ContextTypes, MessageHandler, filters

BOT_TOKEN = "8165227891:AAEGxlXkBi2lw39-HSiTy4h1-nFKv3kBdMM"
ADMIN_IDS = [1209978813, 5051402373]
REQUIRED_CHANNEL_ID = -1001929598735
BOT_USERNAME = "MULTI_USAGES_BOT"
FREE_USES = 5
REFER_BONUS = 5

CHANNELS = [
    {"name": "Join", "url": "https://t.me/+wlcGViIxPPUwNzM1"},
    {"name": "Join", "url": "https://t.me/bgmi_aimbot_loader_config_free"},
    {"name": "Join", "url": "https://t.me/+nfLOhXBf5W83Nzk1"}
]

user_data_db = {}
logging.basicConfig(format="%(asctime)s - %(name)s - %(levelname)s - %(message)s", level=logging.INFO)
logger = logging.getLogger(__name__)

def load_user_data():
    try:
        with open("users.json", "r") as f:
            return json.load(f)
    except FileNotFoundError:
        return {}

def save_user_data():
    with open("users.json", "w") as f:
        json.dump(user_data_db, f, indent=2)

def get_user(user_id):
    uid = str(user_id)
    if uid not in user_data_db:
        user_data_db[uid] = {"points": FREE_USES, "referred_by": None, "referrals": [],
                              "joined_date": str(int(time.time())), "premium_until": None, "name": "", "username": ""}
        save_user_data()
    return user_data_db[uid]

def is_premium(user_id):
    u = get_user(user_id)
    pt = u.get("premium_until")
    if pt == "lifetime": return True
    if pt and str(pt).isdigit() and int(pt) > int(time.time()): return True
    return False

def has_points(user_id):
    if is_premium(user_id): return True
    return get_user(user_id).get("points", 0) > 0

def deduct_point(user_id):
    if is_premium(user_id): return
    u = get_user(user_id)
    u["points"] = max(0, u.get("points", 0) - 1)
    save_user_data()

def is_admin(user_id):
    return user_id in ADMIN_IDS

# ── Admin Commands ──────────────────────────────────────────────────────────

async def cmd_addpoints(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_admin(update.effective_user.id):
        await update.message.reply_text("❌ Access Denied."); return
    args = context.args
    if len(args) < 2:
        await update.message.reply_text("Usage: /addpoints <user_id> <points>"); return
    try:
        target_id, pts = int(args[0]), int(args[1])
    except:
        await update.message.reply_text("❌ Invalid args."); return
    u = get_user(target_id)
    u["points"] = u.get("points", 0) + pts
    save_user_data()
    await update.message.reply_text(f"✅ {pts} points added to {target_id}. Balance: {u['points']}")
    try:
        await context.bot.send_message(chat_id=target_id,
            text=f"🎁 Admin ne aapko {pts} points diye!\n\n💰 Total Points: {u['points']}\n\n▫️ Bot: @{BOT_USERNAME}")
    except Exception as e:
        logger.error(f"Notify failed: {e}")

async def cmd_removepoints(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_admin(update.effective_user.id):
        await update.message.reply_text("❌ Access Denied."); return
    args = context.args
    if len(args) < 2:
        await update.message.reply_text("Usage: /removepoints <user_id> <points>"); return
    try:
        target_id, pts = int(args[0]), int(args[1])
    except:
        await update.message.reply_text("❌ Invalid args."); return
    u = get_user(target_id)
    u["points"] = max(0, u.get("points", 0) - pts)
    save_user_data()
    await update.message.reply_text(f"✅ {pts} points removed from {target_id}. Balance: {u['points']}")

async def cmd_setpremium(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_admin(update.effective_user.id):
        await update.message.reply_text("❌ Access Denied."); return
    args = context.args
    if len(args) < 2:
        await update.message.reply_text("Usage: /setpremium <user_id> <1month|3month|lifetime>"); return
    try:
        target_id = int(args[0])
    except:
        await update.message.reply_text("❌ Invalid user_id."); return
    plan = args[1].lower()
    u = get_user(target_id)
    if plan == "lifetime":
        u["premium_until"] = "lifetime"; label = "Lifetime"
    elif plan == "3month":
        u["premium_until"] = str(int(time.time()) + 90*86400); label = "3 Months"
    else:
        u["premium_until"] = str(int(time.time()) + 30*86400); label = "1 Month"
    save_user_data()
    await update.message.reply_text(f"✅ User {target_id} ko {label} premium diya.")
    try:
        await context.bot.send_message(chat_id=target_id,
            text=f"👑 Congratulations! Aapko {label} Premium access mil gaya!\nUnlimited searches.\n\n▫️ Owner: @GODCHEATOFFICIAL")
    except: pass

async def cmd_userinfo(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_admin(update.effective_user.id):
        await update.message.reply_text("❌ Access Denied."); return
    args = context.args
    if not args:
        await update.message.reply_text("Usage: /userinfo <user_id>"); return
    try:
        target_id = int(args[0])
    except:
        await update.message.reply_text("❌ Invalid user_id."); return
    u = get_user(target_id)
    pt = u.get("premium_until")
    if pt == "lifetime": prem_str = "Lifetime"
    elif pt and str(pt).isdigit():
        days = max(0, (int(pt) - int(time.time())) // 86400)
        prem_str = f"{days} days remaining"
    else: prem_str = "No"
    text = (f"👤 User: {target_id}\n━━━━━━━━━━━━━━━━━━\n"
            f"💰 Points: {u.get('points',0)}\n👑 Premium: {prem_str}\n"
            f"👥 Referrals: {len(u.get('referrals',[]))}\n"
            f"🔗 Referred By: {u.get('referred_by','None')}\n"
            f"📅 Joined: {u.get('joined_date','N/A')}\n━━━━━━━━━━━━━━━━━━")
    await update.message.reply_text(text)

async def cmd_stats(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_admin(update.effective_user.id):
        await update.message.reply_text("❌ Access Denied."); return
    total = len(user_data_db)
    premium_count = sum(1 for uid in user_data_db if is_premium(int(uid)))
    total_refs = sum(len(u.get("referrals",[])) for u in user_data_db.values())
    top_refs = sorted(user_data_db.items(), key=lambda x: len(x[1].get("referrals",[])), reverse=True)[:5]
    text = (f"📊 Bot Stats\n━━━━━━━━━━━━━━━━━━\n"
            f"👥 Total Users: {total}\n👑 Premium: {premium_count}\n🔗 Total Referrals: {total_refs}\n"
            f"━━━━━━━━━━━━━━━━━━\n🏆 Top Referrers:\n")
    for uid, u in top_refs:
        refs = len(u.get("referrals",[]))
        if refs > 0: text += f"  • {uid} → {refs} referrals\n"
    await update.message.reply_text(text)

async def cmd_broadcast(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not is_admin(update.effective_user.id):
        await update.message.reply_text("❌ Access Denied."); return
    if not context.args:
        await update.message.reply_text("Usage: /broadcast <message>"); return
    msg = " ".join(context.args)
    sent, failed = 0, 0
    for uid in user_data_db:
        try:
            await context.bot.send_message(chat_id=int(uid),
                text=f"📢 Broadcast:\n\n{msg}\n\n▫️ Owner: @GODCHEATOFFICIAL")
            sent += 1
        except: failed += 1
    await update.message.reply_text(f"✅ Broadcast done! Sent: {sent} | Failed: {failed}")

# ── User Commands ───────────────────────────────────────────────────────────

async def cmd_points(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user_id = update.effective_user.id
    u = get_user(user_id)
    pts = u.get("points", 0)
    refs = len(u.get("referrals", []))
    bot_link = f"https://t.me/{BOT_USERNAME}?start=ref_{user_id}"
    if is_premium(user_id):
        pt = u.get("premium_until")
        if pt == "lifetime": status = "👑 Lifetime Premium"
        else:
            days = max(0, (int(pt) - int(time.time())) // 86400)
            status = f"👑 Premium ({days} days left)"
    else:
        status = f"💰 Points: {pts}"
    text = (f"📊 Your Account\n━━━━━━━━━━━━━━━━━━\n{status}\n"
            f"👥 Total Referrals: {refs}\n🔗 Referral Link:\n{bot_link}\n"
            f"━━━━━━━━━━━━━━━━━━\nEach referral = +{REFER_BONUS} points!")
    await update.message.reply_text(text)

# ── Start & Menu ────────────────────────────────────────────────────────────

async def start(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user = update.effective_user
    user_id = user.id
    try: await update.message.delete()
    except: pass
    args = context.args
    referrer_id = None
    if args and args[0].startswith("ref_"):
        try: referrer_id = int(args[0][4:])
        except: pass
    u = get_user(user_id)
    u["name"] = user.full_name or ""
    u["username"] = user.username or ""
    if referrer_id and referrer_id != user_id and u.get("referred_by") is None:
        u["referred_by"] = referrer_id
        ref_u = get_user(referrer_id)
        if user_id not in ref_u["referrals"]:
            ref_u["referrals"].append(user_id)
            ref_u["points"] = ref_u.get("points", 0) + REFER_BONUS
            save_user_data()
            try:
                await context.bot.send_message(chat_id=referrer_id,
                    text=f"🎉 New Referral!\n\n👤 {user.full_name} joined using your link!\n+{REFER_BONUS} points added.\n💰 Your Points: {ref_u['points']}")
            except: pass
            for admin_id in ADMIN_IDS:
                try:
                    await context.bot.send_message(chat_id=admin_id,
                        text=f"🔗 New Referral!\nReferrer ID: {referrer_id}\nNew User: {user.full_name} ({user_id})\nReferrer Points: {ref_u['points']} | Referrals: {len(ref_u['referrals'])}")
                except: pass
    save_user_data()
    for admin_id in ADMIN_IDS:
        try:
            await context.bot.send_message(chat_id=admin_id,
                text=f"🆕 New User!\nName: {user.full_name}\nUsername: @{user.username or 'N/A'}\nID: {user_id}")
        except: pass
    keyboard = [
        [InlineKeyboardButton(CHANNELS[0]["name"], url=CHANNELS[0]["url"]),
         InlineKeyboardButton(CHANNELS[1]["name"], url=CHANNELS[1]["url"])],
        [InlineKeyboardButton(CHANNELS[2]["name"], url=CHANNELS[2]["url"]),
         InlineKeyboardButton("✅   I Joined", callback_data="check_membership")]
    ]
    reply_keyboard = [[KeyboardButton("🏠 Start"), KeyboardButton("📞 Contact")],
                      [KeyboardButton("📸 Instagram"), KeyboardButton("🔐 Hacking")]]
    welcome_text = ("📋 Terms & Conditions\n\nBy using this bot, you agree to our terms.\n"
                    "This bot is for educational purposes only.\nUse at your own risk.\n\nJoin our channels to continue:")
    await context.bot.send_message(chat_id=user_id, text=welcome_text, reply_markup=InlineKeyboardMarkup(keyboard))
    await context.bot.send_message(chat_id=user_id, text="Menu:", reply_markup=ReplyKeyboardMarkup(reply_keyboard, resize_keyboard=True))

async def check_membership(update: Update, context: ContextTypes.DEFAULT_TYPE):
    query = update.callback_query
    await query.answer()
    user_id = query.from_user.id
    try:
        member = await context.bot.get_chat_member(chat_id=REQUIRED_CHANNEL_ID, user_id=user_id)
        if member.status in ["member", "administrator", "creator"]:
            await show_main_menu(query, user_id)
        else:
            await query.answer("❌ Please join the required channel first!", show_alert=True)
    except Exception as e:
        logger.error(f"Membership check error: {e}")
        await query.answer("❌ Please join all channels and try again!", show_alert=True)

async def show_main_menu(query, user_id=None):
    if user_id is None: user_id = query.from_user.id
    u = get_user(user_id)
    pts = u.get("points", 0)
    status_line = "👑 Premium — Unlimited Access" if is_premium(user_id) else f"💰 Points: {pts} | 1 point per search"
    keyboard = [
        [InlineKeyboardButton("📱 Number Info", callback_data="number_info"),
         InlineKeyboardButton("🚗 Vehicle Info", callback_data="vehicle_info")],
        [InlineKeyboardButton("🏦 Pin Code → Aadhar Bank", callback_data="pincode_info"),
         InlineKeyboardButton("💎 Paid Promotion 💎", url="https://t.me/GODCHEATOFFICIAL")],
        [InlineKeyboardButton("🆔 TG User ID to Number", callback_data="tg_userid"),
         InlineKeyboardButton("🪪 Aadhaar Full Info", callback_data="aadhaar_info")],
        [InlineKeyboardButton("📡 Pakistan Number Info", callback_data="sim_info"),
         InlineKeyboardButton("🐙 GitHub Info", callback_data="github_info")],
        [InlineKeyboardButton("🏢 GST Info", callback_data="gst_info"),
         InlineKeyboardButton("📛 Number to Name", callback_data="num_name")],
        [InlineKeyboardButton("🔗 Get Free Points (Refer)", callback_data="refer_menu")],
        [InlineKeyboardButton("🔔 Subscribe on YouTube 🔔", url="https://youtube.com/@coder_lobby?si=j_ZeU5xWCtr6FAaX")]
    ]
    await query.edit_message_text(text=f"✅ Welcome! Choose an option:\n\n{status_line}", reply_markup=InlineKeyboardMarkup(keyboard))

async def show_refer_menu(query, user_id):
    bot_link = f"https://t.me/{BOT_USERNAME}?start=ref_{user_id}"
    u = get_user(user_id)
    keyboard = [
        [InlineKeyboardButton("📤 Share Referral Link", switch_inline_query=f"Join this bot & get free searches!\n{bot_link}")],
        [InlineKeyboardButton("💳 Buy Premium (No Referral)", callback_data="buy_points")],
        [InlineKeyboardButton("🔙 Back to Menu", callback_data="back_to_menu")]
    ]
    text = (f"🔗 Refer & Earn Free Points!\n\n💰 Your Points: {u.get('points',0)}\n"
            f"👥 Your Referrals: {len(u.get('referrals',[]))}\n\n"
            f"📌 Your Referral Link:\n{bot_link}\n\n"
            f"✅ 1 friend joins = +{REFER_BONUS} points!\n\n👇 Share or buy premium below:")
    await query.edit_message_text(text=text, reply_markup=InlineKeyboardMarkup(keyboard))

async def show_buy_points(query):
    keyboard = [
        [InlineKeyboardButton("1️⃣ 1 Month — ₹500 / $15", url="https://t.me/GODCHEATOFFICIAL")],
        [InlineKeyboardButton("2️⃣ 3 Months — ₹2,000 / $50", url="https://t.me/GODCHEATOFFICIAL")],
        [InlineKeyboardButton("3️⃣ Lifetime — ₹3,000 / $70", url="https://t.me/GODCHEATOFFICIAL")],
        [InlineKeyboardButton("�� Back", callback_data="refer_menu")]
    ]
    text = ("💳 Buy Premium — Unlimited Access\n\n━━━━━━━━━━━━━━━━━━\n"
            "1️⃣  1 Month Unlimited\n    💰 ₹500 INR / $15 USD\n\n"
            "2️⃣  3 Months Unlimited\n    💰 ₹2,000 INR / $50 USD\n\n"
            "3️⃣  Lifetime Unlimited\n    💰 ₹3,000 INR / $70 USD\n"
            "━━━━━━━━━━━━━━━━━━\n\n📩 DM to buy: @GODCHEATOFFICIAL\nClick any plan to open DM 👆")
    await query.edit_message_text(text=text, reply_markup=InlineKeyboardMarkup(keyboard))

async def show_no_points_msg(query, user_id):
    bot_link = f"https://t.me/{BOT_USERNAME}?start=ref_{user_id}"
    keyboard = [
        [InlineKeyboardButton("📤 Share & Get Free Points", switch_inline_query=f"Join this bot!\n{bot_link}")],
        [InlineKeyboardButton("💳 Buy Premium", callback_data="buy_points")],
        [InlineKeyboardButton("🔙 Back to Menu", callback_data="back_to_menu")]
    ]
    text = (f"❌ Points Khatam!\n\n💰 Your Points: 0\n\n🔗 Refer karke free points pao:\n{bot_link}\n\n"
            f"✅ 1 referral = +{REFER_BONUS} points\n\nYa premium buy karo unlimited access ke liye 👇")
    await query.edit_message_text(text=text, reply_markup=InlineKeyboardMarkup(keyboard))

async def handle_menu(update: Update, context: ContextTypes.DEFAULT_TYPE):
    query = update.callback_query
    await query.answer()
    user_id = query.from_user.id
    if query.data == "refer_menu":
        await show_refer_menu(query, user_id); return
    if query.data == "buy_points":
        await show_buy_points(query); return
    if not has_points(user_id):
        await show_no_points_msg(query, user_id); return
    back_markup = InlineKeyboardMarkup([[InlineKeyboardButton("🔙 Back to Menu", callback_data="back_to_menu")]])
    prompts = {
        "number_info":  ("📱 Send phone number (without country code):\nExample: 9876543210", "number"),
        "vehicle_info": ("🚗 Send vehicle registration number:\nExample: DL01AB1234", "vehicle"),
        "pincode_info": ("🏦 Send PIN code:\nExample: 110001", "pincode"),
        "tg_userid":    ("🆔 Send Telegram User ID (numbers only):\nExample: 7178895428", "tg_userid"),
        "aadhaar_info": ("🪪 Send Aadhaar number (12 digits):\nExample: 819766120913", "aadhaar"),
        "sim_info":     ("📡 Send Pakistan mobile number:\nExample: 3247452828\n\n⚠️ Only Pakistan numbers supported.", "sim_info"),
        "github_info":  ("🐙 Send GitHub username:\nExample: torvalds", "github_info"),
        "gst_info":     ("🏢 Send GST number:\nExample: 10DJCPK4351Q1Z5", "gst_info"),
        "num_name":     ("📛 Send mobile number:\nExample: 9087654321", "num_name"),
    }
    if query.data in prompts:
        text, key = prompts[query.data]
        await query.edit_message_text(text, reply_markup=back_markup)
        context.user_data["waiting_for"] = key

# ── API helpers ─────────────────────────────────────────────────────────────

async def fetch_api(url):
    try:
        async with aiohttp.ClientSession() as session:
            async with session.get(url, timeout=aiohttp.ClientTimeout(total=10)) as r:
                if r.status == 200:
                    try: return await r.json(content_type=None)
                    except: return None
    except Exception as e:
        logger.error(f"API Error: {e}")
    return None

async def fetch_text_api(url):
    try:
        async with aiohttp.ClientSession() as session:
            async with session.get(url, timeout=aiohttp.ClientTimeout(total=10)) as r:
                if r.status == 200: return await r.text()
    except Exception as e:
        logger.error(f"Text API Error: {e}")
    return None

async def fetch_pincode_api(pincode):
    try:
        url = f"https://bhuvan-app3.nrsc.gov.in/aadhaar/usrtask/app_specific/get/getpinDetails.php?sno={pincode}"
        async with aiohttp.ClientSession() as session:
            async with session.get(url, timeout=aiohttp.ClientTimeout(total=15)) as r:
                if r.status == 200:
                    try: return await r.json()
                    except:
                        text = await r.text()
                        try: return json.loads(text)
                        except: return None
    except Exception as e:
        logger.error(f"Pincode API Error: {e}")
    return None

# ── Message handler ──────────────────────────────────────────────────────────

async def handle_message(update: Update, context: ContextTypes.DEFAULT_TYPE):
    user_input = update.message.text.strip()
    user_id = update.effective_user.id
    waiting_for = context.user_data.get("waiting_for")
    if user_input == "🏠 Start": await start(update, context); return
    elif user_input == "📞 Contact": await contact_command(update, context); return
    elif user_input == "�� Instagram": await instagram_command(update, context); return
    elif user_input == "🔐 Hacking": await hacking_command(update, context); return
    if not waiting_for: return
    if not has_points(user_id):
        bot_link = f"https://t.me/{BOT_USERNAME}?start=ref_{user_id}"
        keyboard = [[InlineKeyboardButton("📤 Share & Get Free Points", switch_inline_query=f"Join this bot!\n{bot_link}")],
                    [InlineKeyboardButton("💳 Buy Premium", callback_data="buy_points")]]
        await update.message.reply_text(f"❌ Points Khatam!\n\nRefer karke free points pao:\n{bot_link}\n\nYa premium buy karo.",
            reply_markup=InlineKeyboardMarkup(keyboard))
        return
    processing_msg = await update.message.reply_text("⏳ Processing your request...")
    result = ""
    if waiting_for == "number":
        if user_input == "6203950934":
            result = "❌ Chala ja bhosdk!\n\n🚫 This Number is protected!\n\n▫️ Owner: @GODCHEATOFFICIAL"
        else:
            url = f"https://database-sigma-nine.vercel.app/number/{user_input}?api_key=YOUR-PASSWORD"
            text = await fetch_text_api(url)
            result = format_number_info_text(user_input, text) if text and "success" in text.lower() else f"❌ Data Not Available for: {user_input}"
    elif waiting_for == "vehicle":
        data = await fetch_api(f"http://vehicle-info-aco-api.vercel.app/info?vehicle={user_input}")
        result = format_vehicle_info(user_input, data) if data else f"❌ Data Not Available for: {user_input}"
    elif waiting_for == "pincode":
        data = await fetch_pincode_api(user_input)
        result = format_pincode_info(user_input, data) if data else f"❌ Data Not Available for PIN: {user_input}"
    elif waiting_for == "tg_userid":
        if user_input.startswith("@") or not user_input.isdigit():
            result = "❌ User ID numbers only!\nExample: 7178895428"
        elif user_input == "1209978813":
            result = "❌ Chala ja bhosdk!\n\n🚫 This User ID is protected!\n\n▫️ Owner: @GODCHEATOFFICIAL"
        else:
            data = await fetch_api(f"https://api.subhxcosmo.in/api?key=suryanshrootx&type=sms&term={user_input}")
            result = format_telegram_userid_info(user_input, data.get("result", {})) if data and data.get("success") else f"❌ Data Not Available for User ID: {user_input}"
    elif waiting_for == "aadhaar":
        if not user_input.isdigit() or len(user_input) != 12:
            result = "❌ Invalid Aadhaar! Must be 12 digits.\nExample: 819766120913"
        else:
            url = f"https://database-sigma-nine.vercel.app/aadhaar/{user_input}?api_key=YOUR-PASSWORD"
            text = await fetch_text_api(url)
            result = format_aadhaar_info(user_input, text) if text and "success" in text.lower() else f"❌ Data Not Available for Aadhaar: {user_input}"
    elif waiting_for == "sim_info":
        data = await fetch_api(f"https://amscript.xyz/PublicApi/Siminfo.php?number={user_input}")
        result = format_sim_info(user_input, data["data"]) if data and data.get("success") and data.get("data") else f"❌ Data Not Available for: {user_input}\n\n⚠️ Only Pakistan numbers supported."
    elif waiting_for == "github_info":
        data = await fetch_api(f"https://abbas-apis.vercel.app/api/github?username={user_input}")
        result = format_github_info(user_input, data["data"]) if data and data.get("success") and data.get("data") else f"❌ Data Not Available for: {user_input}"
    elif waiting_for == "gst_info":
        data = await fetch_api(f"https://api.b77bf911.workers.dev/gst?number={user_input}")
        result = format_gst_info(user_input, data["data"]["data"]) if data and data.get("success") and data.get("data", {}).get("data") else f"❌ Data Not Available for GST: {user_input}"
    elif waiting_for == "num_name":
        data = await fetch_api(f"https://abbas-apis.vercel.app/api/num-name?number={user_input}")
        result = format_num_name(user_input, data["data"]) if data and data.get("success") and data.get("data", {}).get("success") else f"❌ Data Not Available for: {user_input}"
    deduct_point(user_id)
    u = get_user(user_id)
    if not is_premium(user_id):
        result += f"\n\n💰 Points Remaining: {u.get('points', 0)}"
    result += "\n\n🔄 Send another query or click Back to Menu!"
    await processing_msg.delete()
    await update.message.reply_text(result, reply_markup=InlineKeyboardMarkup([[InlineKeyboardButton("🔙 Back to Menu", callback_data="back_to_menu")]]))

# ── Format functions ─────────────────────────────────────────────────────────

def format_number_info_text(number, text):
    import re, json as _j
    cm = re.search(r"Count:\s*(\d+)", text); sm = re.search(r"Status:\s*(\w+)", text)
    count = cm.group(1) if cm else "?"; status = sm.group(1) if sm else "success"
    result = f"📱 Number Info\n━━━━━━━━━━━━━━━━━━\n▫️ Number: {number}\n▫️ Status: {status}\n▫️ Records: {count}\n━━━━━━━━━━━━━━━━━━"
    jm = re.search(r"Results:\s*(\[.*?\])", text, re.DOTALL)
    if jm:
        try:
            records = _j.loads(jm.group(1)); seen = set(); idx = 0
            for item in records:
                key = f"{item.get('mobile','')}{item.get('name','')}"
                if key in seen: continue
                seen.add(key); idx += 1
                result += f"\n📌 Result {idx}:"
                if item.get("name"):    result += f"\n▫️ Name: {item['name']}"
                if item.get("fname"):   result += f"\n▫️ Father: {item['fname']}"
                if item.get("mobile"):  result += f"\n▫️ Mobile: {item['mobile']}"
                if item.get("alt") and item["alt"] not in ("", "NA"): result += f"\n▫️ Alt: {item['alt']}"
                if item.get("circle"):  result += f"\n▫️ Circle: {item['circle']}"
                if item.get("address"): result += f"\n▫️ Address: {item['address'][:80]}"
                result += "\n━━━━━━━━━━━━━━━━━━"
        except Exception as e:
            logger.error(f"Parse error: {e}"); result += "\n▫️ Unable to parse\n━━━━━━━━━━━━━━━━━━"
    result += "\n▫️ Owner: @GODCHEATOFFICIAL"; return result

def format_telegram_userid_info(user_id, data):
    result = f"🆔 TG User ID Info\n━━━━━━━━━━━━━━━━━━\n▫️ User ID: {data.get('tg_id', user_id)}"
    if data.get("msg"):          result += f"\n▫️ Status: {data['msg']}"
    if data.get("country"):      result += f"\n▫️ Country: {data['country']}"
    if data.get("country_code"): result += f"\n▫️ Country Code: {data['country_code']}"
    if data.get("number"):       result += f"\n▫️ Number: {data['number']}"
    result += "\n━━━━━━━━━━━━━━━━━━\n▫️ Owner: @GODCHEATOFFICIAL"; return result

def format_aadhaar_info(aadhaar, text):
    import re, json as _j
    cm = re.search(r"Count:\s*(\d+)", text); sm = re.search(r"Status:\s*(\w+)", text)
    count = cm.group(1) if cm else "?"; status = sm.group(1) if sm else "success"
    result = f"🪪 Aadhaar Full Info\n━━━━━━━━━━━━━━━━━━\n▫️ Aadhaar: {aadhaar}\n▫️ Status: {status}\n▫️ Records: {count}\n━━━━━━━━━━━━━━━━━━"
    jm = re.search(r"Results:\s*(\[.*?\])", text, re.DOTALL)
    if jm:
        try:
            records = _j.loads(jm.group(1)); seen = set(); idx = 0
            for item in records:
                key = f"{item.get('name','')}{item.get('mobile','')}"
                if key in seen: continue
                seen.add(key); idx += 1
                result += f"\n📌 Result {idx}:"
                if item.get("name"):    result += f"\n▫️ Name: {item['name']}"
                if item.get("fname"):   result += f"\n▫️ Father: {item['fname']}"
                if item.get("mobile"):  result += f"\n▫️ Mobile: {item['mobile']}"
                if item.get("alt") and item["alt"] not in ("", "NA"): result += f"\n▫️ Alt: {item['alt']}"
                if item.get("email") and item["email"] not in ("", "NA"): result += f"\n▫️ Email: {item['email']}"
                if item.get("address"): result += f"\n▫️ Address: {item['address'][:100]}"
                result += "\n━━━━━━━━━━━━━━━━━━"
        except Exception as e:
            logger.error(f"Aadhaar parse error: {e}"); result += "\n▫️ Unable to parse\n━━━━━━━━━━━━━━━━━━"
    result += "\n▫️ Owner: @GODCHEATOFFICIAL"; return result

def format_vehicle_info(reg_no, data):
    if not isinstance(data, dict) or not data.get("owner_name") or str(data.get("owner_name","")).strip() in ("","NA","null","None"):
        return f"🚗 Vehicle Info for: {reg_no}\n▫️ NO DATA FOUND\n\n▫️ Owner: @GODCHEATOFFICIAL"
    result = f"🚗 Vehicle Info for: {reg_no}\n📌 Result:"
    for key, label in [("owner_name","Owner Name"),("make_model","Make & Model"),("make_name","Make Name"),
                        ("fuel_type","Fuel Type"),("vehicle_type","Vehicle Type"),("registration_date","Reg Date"),
                        ("registration_address","Reg Address"),("permanent_address","Perm Address"),
                        ("chassis_number","Chassis No"),("previous_policy_expiry_date","Policy Expiry")]:
        val = data.get(key, "")
        if val and str(val).strip() not in ("","NA","null","None"): result += f"\n▫️ {label}: {val}"
    result += "\n\n▫️ Owner: @GODCHEATOFFICIAL"; return result

def format_sim_info(number, data_list):
    result = f"📡 Pakistan SIM Info for: {number}\n📌 Result:"
    if not data_list: return result + "\n▫️ NO DATA FOUND\n\n▫️ Owner: @GODCHEATOFFICIAL"
    for idx, e in enumerate(data_list, 1):
        if len(data_list) > 1: result += f"\n\n━━━━━━━━━━━━━━━━━━\nRecord {idx}:"
        if e.get("full_name"): result += f"\n▫️ Name: {e['full_name']}"
        if e.get("phone"):     result += f"\n▫️ Phone: {e['phone']}"
        if e.get("cnic"):      result += f"\n▫️ CNIC: {e['cnic']}"
        if e.get("address"):   result += f"\n▫️ Address: {e['address']}"
    result += "\n\n▫️ Owner: @GODCHEATOFFICIAL"; return result

def format_github_info(username, data):
    result = f"�� GitHub Info for: {username}\n📌 Result:"
    for key, label in [("name","Name"),("username","Username"),("bio","Bio"),("company","Company"),
                        ("location","Location"),("email","Email"),("blog","Website"),
                        ("followers","Followers"),("following","Following"),("public_repos","Public Repos"),
                        ("created_at","Joined"),("profile_url","Profile URL")]:
        val = data.get(key)
        if val and str(val).strip() not in ("","None","null"): result += f"\n▫️ {label}: {val}"
    result += "\n\n▫️ Owner: @GODCHEATOFFICIAL"; return result

def format_gst_info(gst_no, data):
    result = f"🏢 GST Info for: {gst_no}\n📌 Result:"
    for key, label in [("Gstin","GSTIN"),("TradeName","Trade Name"),("LegalName","Legal Name"),
                        ("AddrSt","Street"),("AddrLoc","Location"),("AddrPncd","PIN Code"),
                        ("StateCode","State Code"),("TxpType","Taxpayer Type"),("Status","Status"),("DtReg","Reg Date")]:
        val = data.get(key)
        if val and str(val).strip() not in ("","None","null","0"): result += f"\n▫️ {label}: {val}"
    result += "\n\n▫️ Owner: @GODCHEATOFFICIAL"; return result

def format_num_name(number, data):
    result = f"📛 Number to Name for: {number}\n📌 Result:"
    name = data.get("name", "")
    result += f"\n▫️ Registered Name: {name}\n▫️ Number: {number}" if name else "\n▫️ NO DATA FOUND"
    result += "\n\n▫️ Owner: @GODCHEATOFFICIAL"; return result

def format_pincode_info(pincode, data):
    import re
    from html import unescape
    if not data or not isinstance(data, dict):
        return f"🏦 PIN Code Info for: {pincode}\n▫️ NO DATA FOUND\n\n▫️ Owner: @GODCHEATOFFICIAL"
    result = f"🏦 PIN Code → Aadhar Bank Centers for: {pincode}"
    center_count = data.get("centerCount", 0)
    if center_count == 0:
        result += "\n▫️ NO DATA FOUND\n\n▫️ Owner: @GODCHEATOFFICIAL"; return result
    result += f"\n▫️ Total Centers: {center_count}\n▫️ By: @GODCHEATOFFICIAL"
    center_data = data.get("centerData", "")
    if center_data:
        center_data = unescape(center_data)
        result += "\n\n📌 Locations:"
        pattern = r"<div class='vCenterName'>([^<]+?)\s*\((\d+)\)</div><div class='vCenterAdd'>([^<]+?)<"
        matches = re.findall(pattern, center_data, re.DOTALL)
        if matches:
            for idx, (name, pin, address) in enumerate(matches, 1):
                address = re.sub(r"\s+", " ", re.sub(r"<[^>]+>", "", address)).strip()
                result += f"\n\n━━━━━━━━━━━━━━━━━━\nLocation {idx}:\nName: {name.strip()}\nPin: {pin}\nAddress: {address}\n━━━━━━━━━━━━━━━━━━"
        else:
            result += "\n▫️ Unable to parse location details"
    result += "\n\n▫️ Owner: @GODCHEATOFFICIAL"; return result

# ── Misc commands ────────────────────────────────────────────────────────────

async def back_to_menu(update: Update, context: ContextTypes.DEFAULT_TYPE):
    query = update.callback_query
    await query.answer()
    context.user_data["waiting_for"] = None
    await show_main_menu(query)

async def contact_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text("📞 Need help?\nClick below to contact:",
        reply_markup=InlineKeyboardMarkup([[InlineKeyboardButton("📞 Contact Admin", url="https://t.me/GODCHEATOFFICIAL")]]))

async def instagram_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text("📸 Follow us on Instagram!",
        reply_markup=InlineKeyboardMarkup([[InlineKeyboardButton("📸 Follow on Instagram", url="https://www.instagram.com/god_moder_bio?igsh=MTNuNnE2azg0bDc4cQ==")]]))

async def hacking_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text("🔐 Join our Hacking Channel!",
        reply_markup=InlineKeyboardMarkup([[InlineKeyboardButton("🔐 Join Hacking Channel", url="https://t.me/+wlcGViIxPPUwNzM1")]]))

# ── Main ─────────────────────────────────────────────────────────────────────

def main():
    print("🚀 Starting bot...")
    global user_data_db
    user_data_db = load_user_data()
    app = Application.builder().token(BOT_TOKEN).build()
    app.add_handler(CommandHandler("start", start))
    app.add_handler(CommandHandler("points", cmd_points))
    app.add_handler(CommandHandler("contact", contact_command))
    app.add_handler(CommandHandler("instagram", instagram_command))
    app.add_handler(CommandHandler("hacking", hacking_command))
    app.add_handler(CommandHandler("addpoints", cmd_addpoints))
    app.add_handler(CommandHandler("removepoints", cmd_removepoints))
    app.add_handler(CommandHandler("setpremium", cmd_setpremium))
    app.add_handler(CommandHandler("userinfo", cmd_userinfo))
    app.add_handler(CommandHandler("stats", cmd_stats))
    app.add_handler(CommandHandler("broadcast", cmd_broadcast))
    app.add_handler(CallbackQueryHandler(check_membership, pattern="^check_membership$"))
    app.add_handler(CallbackQueryHandler(back_to_menu, pattern="^back_to_menu$"))
    app.add_handler(CallbackQueryHandler(handle_menu, pattern="^(number_info|vehicle_info|pincode_info|tg_userid|aadhaar_info|sim_info|github_info|gst_info|num_name|refer_menu|buy_points)$"))
    app.add_handler(MessageHandler(filters.TEXT & ~filters.COMMAND, handle_message))
    print("✅ Bot handlers registered")
    print("🤖 Bot is running...")
    app.run_polling(allowed_updates=Update.ALL_TYPES)

if __name__ == "__main__":
    main()
