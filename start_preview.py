"""
GhostTrack Web Preview & ADB Live Assistant
Created by Princeyy
"""
import webbrowser
import os
import sys

def main():
    dashboard_path = os.path.abspath(os.path.join(os.path.dirname(__file__), "web-dashboard", "index.html"))
    print("=" * 60)
    print("      👑 GHOSTTRACK APP FORENSICS - BY PRINCEYY 👑")
    print("=" * 60)
    print(f"Opening interactive dashboard: {dashboard_path}")
    webbrowser.open(f"file:///{dashboard_path}")
    print("\nFeatures loaded:")
    print(" • Uninstalled App Detection Simulator")
    print(" • Foreground Screen Time ('fg time') Tracking")
    print(" • Sessions Counter & Launch Timeline")
    print(" • First Seen & Last Used Auditing")
    print(" • Live ADB UsageStats Parser for connected Android devices")
    print("=" * 60)

if __name__ == "__main__":
    main()
