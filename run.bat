@echo off
chcp 65001 > nul
title RKh BPB Wizard
cd /d "%~dp0"
echo 🟠 RKh BPB Wizard
echo 🌑 Dark Orange Liquid Glass UI
echo 🔐 A new secure local URL will appear below.
echo 🚀 Starting local web app...
echo 🛑 Press Ctrl+C to stop.
echo.
python rkh_bpb_wizard.py
pause
