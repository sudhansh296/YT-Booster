import re

with open('yt-sub-exchange/prototype/bot_fresh.py', 'r', encoding='utf-8') as f:
    code = f.read()

# 1. Add 'import datetime' at top
code = code.replace(
    'import logging\nimport aiohttp\nimport json\nimport time\n',
    'import logging\nimport aiohttp\nimport json\nimport time\nimport datetime\n'
)

# 2. Add DAILY_POINTS constant after REFER_BONUS
code = code.replace(
    'FREE_USES = 5\nREFER_BONUS = 5\n',
    'FREE_USES = 5\nREFER_BONUS = 5\nDAILY_POINTS = 10\n'
)

# 3. Fix /start welcome message
old_welcome = '    welcome_text = ("📋 Terms & Conditions\\n\\nBy using this bot, you agree to our terms.\\n"\n                    "This bot is for educational purposes only.\\nUse at your own risk.\\n\\nJoin our channels to continue:")'
new_welcome = '    welcome_text = ("📋 Read the terms and conditions of this bot\\n\\n"\n                    "By using this bot, you agree to abide by our terms and conditions.\\n\\n"\n                    "This bot is made available for educational purposes only.\\n\\n"\n                    "If you use this bot, you do it at your own risk. If it causes any harm to anyone, "\n                    "then you will be responsible for it.\\n\\nNow join our channels to continue:")'
code = code.replace(old_welcome, new_welcome)

# 4. Fix switch_inline_query in show_refer_menu
code = code.replace(
    'InlineKeyboardButton("📤 Share Referral Link", switch_inline_query=f"Join this bot & get free searches!\\n{bot_link}")',
    'InlineKeyboardButton("📤 Share Referral Link", url=f"https://t.me/share/url?url={bot_link}&text=Is+bot+se+kisi+ka+bhi+information+nikal+sakte+ho%21+English+mein+bhi+kaam+karta+hai%21")'
)

# 5. Fix switch_inline_query in show_no_points_msg
code = code.replace(
    'InlineKeyboardButton("📤 Share & Get Free Points", switch_inline_query=f"Join this bot!\\n{bot_link}")',
    'InlineKeyboardButton("📤 Share & Get Free Points", url=f"https://t.me/share/url?url={bot_link}&text=Is+bot+se+kisi+ka+bhi+information+nikal+sakte+ho%21+English+mein+bhi+kaam+karta+hai%21")'
)

# 6. Fix switch_inline_query in handle_message no-points section (same pattern)
# Already covered by step 5 since same string

# 7. Add give_daily_points function before main()
daily_func = '''
async def give_daily_points(context):
    """Give 10 free points to all users daily"""
    today = datetime.date.today().isoformat()
    count = 0
    for uid, u in user_data_db.items():
        if u.get("last_daily_date") == today:
            continue
        u["points"] = u.get("points", 0) + DAILY_POINTS
        u["last_daily_date"] = today
        count += 1
        try:
            await context.bot.send_message(
                chat_id=int(uid),
                text=f"🎁 Daily Free Points!\\n\\n+{DAILY_POINTS} points aapke account mein add ho gaye!\\n\\n💰 Total Points: {u['points']}\\n\\n▫️ Bot: @{BOT_USERNAME}"
            )
        except Exception as e:
            logger.error(f"Daily points notify failed for {uid}: {e}")
    save_user_data()
    logger.info(f"Daily points given to {count} users")

'''

code = code.replace('def main():', daily_func + 'def main():')

# 8. Add job_queue in main() after app is built
code = code.replace(
    '    app = Application.builder().token(BOT_TOKEN).build()\n',
    '    app = Application.builder().token(BOT_TOKEN).build()\n    app.job_queue.run_repeating(give_daily_points, interval=86400, first=10)\n'
)

with open('yt-sub-exchange/prototype/bot_fresh.py', 'w', encoding='utf-8') as f:
    f.write(code)

print('All fixes applied successfully!')

# Verify key changes
checks = [
    ('import datetime', 'datetime import'),
    ('DAILY_POINTS = 10', 'DAILY_POINTS constant'),
    ('Read the terms and conditions', '/start welcome message'),
    ('t.me/share/url', 'share URL fix'),
    ('give_daily_points', 'daily points function'),
    ('run_repeating', 'job_queue setup'),
    ('last_daily_date', 'daily date tracking'),
]
for check, label in checks:
    status = '✅' if check in code else '❌'
    print(f'{status} {label}')
