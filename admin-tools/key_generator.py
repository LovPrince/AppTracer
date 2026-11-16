"""
👑 PRINCEYY KEY GENERATOR - GHOSTTRACK LICENSE SYSTEM 👑
Use this script to approve and issue time/usage-limited keys to users on Discord.
"""
import hmac
import hashlib
import base64
import time
import sys

MASTER_SECRET = "PRINCEYY_GHOSTTRACK_SECRET_KEY_9928174625"

def calculate_hmac(data: str) -> str:
    h = hmac.new(MASTER_SECRET.encode('utf-8'), data.encode('utf-8'), hashlib.sha256)
    return h.hexdigest()[:16]

def generate_key(hwid: str, duration_hours: float, max_uses: int) -> str:
    hwid = hwid.strip().upper()
    now_ms = int(time.time() * 1000)

    if duration_hours == -1:
        expiry_ms = -1
    else:
        expiry_ms = now_ms + int(duration_hours * 3600 * 1000)

    payload_raw = f"{hwid}|{expiry_ms}|{max_uses}"
    signature = calculate_hmac(payload_raw)
    full_payload = f"{payload_raw}|{signature}"

    b64 = base64.urlsafe_b64encode(full_payload.encode('utf-8')).decode('utf-8').rstrip('=')
    return f"GHOST-{b64}"

def main():
    print("=" * 65)
    print("      👑 PRINCEYY'S GHOSTTRACK LICENSE KEY GENERATOR 👑")
    print("=" * 65)
    print("Enter the details sent by the user on Discord:\n")

    hwid = input("1. Enter user's Device HWID (e.g. PRINCEYY-8A12-99B4-E012): ").strip()
    if not hwid:
        print("❌ HWID cannot be empty!")
        return

    print("\n2. Select Access Duration:")
    print("   [1] 1 Hour (Trial)")
    print("   [2] 24 Hours (1 Day)")
    print("   [3] 7 Days (1 Week)")
    print("   [4] 30 Days (1 Month)")
    print("   [5] Lifetime (No time expiration)")
    print("   [6] Custom hours")
    choice = input("Select choice (1-6) [default: 4]: ").strip() or "4"

    if choice == "1":
        hours = 1
        plan_desc = "1 Hour"
    elif choice == "2":
        hours = 24
        plan_desc = "24 Hours"
    elif choice == "3":
        hours = 24 * 7
        plan_desc = "7 Days"
    elif choice == "4":
        hours = 24 * 30
        plan_desc = "30 Days"
    elif choice == "5":
        hours = -1
        plan_desc = "Lifetime"
    else:
        hours = float(input("Enter custom duration in hours: ").strip())
        plan_desc = f"{hours} Hours"

    print("\n3. Select Scan / Use Limit:")
    print("   [1] 5 Scans")
    print("   [2] 10 Scans")
    print("   [3] 25 Scans")
    print("   [4] 50 Scans")
    print("   [5] Unlimited Scans")
    use_choice = input("Select choice (1-5) [default: 5]: ").strip() or "5"

    if use_choice == "1":
        max_uses = 5
    elif use_choice == "2":
        max_uses = 10
    elif use_choice == "3":
        max_uses = 25
    elif use_choice == "4":
        max_uses = 50
    else:
        max_uses = -1

    uses_desc = "Unlimited" if max_uses == -1 else f"{max_uses} Scans"

    license_key = generate_key(hwid, hours, max_uses)

    print("\n" + "=" * 65)
    print("🎉 LICENSE KEY GENERATED SUCCESSFULLY!")
    print("=" * 65)
    print(f"Target HWID:  {hwid}")
    print(f"Time Limit:   {plan_desc}")
    print(f"Usage Limit:  {uses_desc}")
    print("-" * 65)
    print(f"KEY: {license_key}")
    print("-" * 65)
    print("\n💬 Copy & Paste this message to the user on Discord:")
    print("-----------------------------------------------------------------")
    print(f"Hey! Your GhostTrack access has been approved by Princeyy 👑")
    print(f"• Time Limit:  {plan_desc}")
    print(f"• Scan Limit:  {uses_desc}")
    print(f"• Your Key:    {license_key}")
    print("Paste this key into GhostTrack to unlock the app!")
    print("-----------------------------------------------------------------")

if __name__ == "__main__":
    main()
