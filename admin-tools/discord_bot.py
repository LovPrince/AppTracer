"""
👑 PRINCEYY'S GHOSTTRACK DISCORD LICENSE BOT 👑
Run this bot to generate, verify, and issue time & usage limited keys directly inside Discord!
"""
import discord
from discord.ext import commands
from discord import app_commands
import hmac
import hashlib
import base64
import time
import os

# ==============================================================================
# CONFIGURATION
# ==============================================================================
BOT_TOKEN = "YOUR_DISCORD_BOT_TOKEN_HERE"  # Paste your bot token from discord.com/developers
MASTER_SECRET = "PRINCEYY_GHOSTTRACK_SECRET_KEY_9928174625"
ADMIN_USER_IDS = []  # Put your Discord User ID here (e.g. [123456789012345678]) so only YOU can generate keys!

intents = discord.Intents.default()
intents.message_content = True
bot = commands.Bot(command_prefix="!", intents=intents)

# ==============================================================================
# CRYPTOGRAPHIC ENGINE
# ==============================================================================
def calculate_hmac(data: str) -> str:
    h = hmac.new(MASTER_SECRET.encode('utf-8'), data.encode('utf-8'), hashlib.sha256)
    return h.hexdigest()[:16]

def generate_key_token(hwid: str, duration_hours: float, max_uses: int) -> str:
    hwid = hwid.strip().upper()
    now_ms = int(time.time() * 1000)

    if duration_hours == -1:
        expiry_ms = -1
    else:
        expiry_ms = now_ms + int(duration_hours * 3600 * 1000)

    raw_payload = f"{hwid}|{expiry_ms}|{max_uses}"
    signature = calculate_hmac(raw_payload)
    full_payload = f"{raw_payload}|{signature}"

    b64 = base64.urlsafe_b64encode(full_payload.encode('utf-8')).decode('utf-8').rstrip('=')
    return f"GHOST-{b64}"

def parse_duration_str(d_str: str) -> tuple[float, str]:
    d_str = d_str.strip().lower()
    if d_str in ["lifetime", "perm", "permanent", "inf", "-1"]:
        return -1, "Lifetime"
    if d_str.endswith("h"):
        hours = float(d_str[:-1])
        return hours, f"{int(hours)} Hour(s)"
    if d_str.endswith("d"):
        days = float(d_str[:-1])
        return days * 24, f"{int(days)} Day(s)"
    if d_str.endswith("m"):
        months = float(d_str[:-1])
        return months * 24 * 30, f"{int(months)} Month(s)"
    
    # Default numeric input treated as days
    days = float(d_str)
    return days * 24, f"{int(days)} Day(s)"

# ==============================================================================
# BOT EVENTS & COMMANDS
# ==============================================================================
@bot.event
async def on_ready():
    print("=" * 60)
    print(f"👑 Princeyy's GhostTrack License Bot is ONLINE as {bot.user}!")
    print("=" * 60)
    try:
        synced = await bot.tree.sync()
        print(f"Synced {len(synced)} slash command(s).")
    except Exception as e:
        print(f"Failed to sync slash commands: {e}")

# --- SLASH COMMAND: /gen ---
@bot.tree.command(name="gen", description="Generate a time & scan-limited license key for GhostTrack")
@app_commands.describe(
    hwid="User's Device HWID (e.g. PRINCEYY-8A12-99B4-E012)",
    duration="Time limit (e.g. 1h, 24h, 7d, 30d, or lifetime)",
    scans="Scan count limit (e.g. 5, 10, 50, or unlimited)"
)
async def slash_gen(interaction: discord.Interaction, hwid: str, duration: str = "7d", scans: str = "unlimited"):
    if ADMIN_USER_IDS and interaction.user.id not in ADMIN_USER_IDS:
        await interaction.response.send_message("❌ Unauthorized: Only Princeyy can generate license keys!", ephemeral=True)
        return

    try:
        hours, duration_label = parse_duration_str(duration)
    except Exception:
        await interaction.response.send_message("❌ Invalid duration. Use formats like: `1h`, `24h`, `7d`, `30d`, `lifetime`", ephemeral=True)
        return

    if scans.lower() in ["unlimited", "inf", "lifetime", "-1"]:
        max_uses = -1
        scans_label = "Unlimited Scans"
    else:
        try:
            max_uses = int(scans)
            scans_label = f"{max_uses} Scans"
        except ValueError:
            await interaction.response.send_message("❌ Invalid scans number. Use a number like `10` or `unlimited`", ephemeral=True)
            return

    key = generate_key_token(hwid, hours, max_uses)

    embed = discord.Embed(
        title="👑 GhostTrack License Key Generated",
        description="Here is the authorized license key for the requested device.",
        color=0x8B5CF6
    )
    embed.add_field(name="📱 Device HWID", value=f"`{hwid.upper()}`", inline=False)
    embed.add_field(name="⏳ Time Limit", value=f"**{duration_label}**", inline=True)
    embed.add_field(name="🔄 Scan Limit", value=f"**{scans_label}**", inline=True)
    embed.add_field(name="🔑 License Key (Tap to Copy)", value=f"```{key}```", inline=False)
    embed.set_footer(text="GhostTrack Security • Made by Princeyy")

    copy_msg = (
        f"Hey! Your GhostTrack access has been approved by Princeyy 👑\n"
        f"• Time Limit: **{duration_label}**\n"
        f"• Scan Limit: **{scans_label}**\n"
        f"• License Key: `{key}`\n\n"
        f"*Paste this key into GhostTrack to unlock the app!*"
    )

    await interaction.response.send_message(embed=embed)
    await interaction.followup.send(f"**Copy-paste text for the user:**\n{copy_msg}", ephemeral=True)

# --- PREFIX COMMAND: !gen <hwid> <duration> <scans> ---
@bot.command(name="gen")
async def prefix_gen(ctx, hwid: str = None, duration: str = "7d", scans: str = "unlimited"):
    if ADMIN_USER_IDS and ctx.author.id not in ADMIN_USER_IDS:
        await ctx.reply("❌ Unauthorized: Only Princeyy can generate license keys!")
        return

    if not hwid:
        await ctx.reply("❌ Usage: `!gen <HWID> [duration] [scans]`\nExample: `!gen PRINCEYY-8A12-99B4 7d 10`")
        return

    try:
        hours, duration_label = parse_duration_str(duration)
        max_uses = -1 if scans.lower() in ["unlimited", "inf", "-1"] else int(scans)
        scans_label = "Unlimited Scans" if max_uses == -1 else f"{max_uses} Scans"
    except Exception as e:
        await ctx.reply(f"❌ Error: {e}")
        return

    key = generate_key_token(hwid, hours, max_uses)

    embed = discord.Embed(
        title="👑 GhostTrack License Key Generated",
        color=0x8B5CF6
    )
    embed.add_field(name="📱 Device HWID", value=f"`{hwid.upper()}`", inline=False)
    embed.add_field(name="⏳ Time Limit", value=duration_label, inline=True)
    embed.add_field(name="🔄 Scan Limit", value=scans_label, inline=True)
    embed.add_field(name="🔑 Key", value=f"```{key}```", inline=False)
    embed.set_footer(text="Made by Princeyy")

    await ctx.reply(embed=embed)

if __name__ == "__main__":
    if BOT_TOKEN == "YOUR_DISCORD_BOT_TOKEN_HERE":
        print("⚠️ Please edit admin-tools/discord_bot.py and set your BOT_TOKEN!")
    else:
        bot.run(BOT_TOKEN)
