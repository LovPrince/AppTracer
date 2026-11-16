@echo off
title Deploy GhostTrack to GitHub - Made by Princeyy
color 0b
echo ======================================================================
echo           DEPLOY GHOSTTRACK TO GITHUB - MADE BY PRINCEYY
echo ======================================================================
echo.

set /p REPO_NAME="Enter your GitHub repository name (e.g., GhostTrack): "
if "%REPO_NAME%"=="" set REPO_NAME=GhostTrack

echo.
echo Initializing Git repository...
git init
git add .
git commit -m "GhostTrack: Android Uninstalled App Forensics by Princeyy"
git branch -M main

echo.
echo Connecting to GitHub repository for LovPrince...
git remote remove origin 2>nul
git remote add origin https://github.com/LovPrince/%REPO_NAME%.git

echo.
echo Pushing code to https://github.com/LovPrince/%REPO_NAME%.git ...
git push -u origin main

echo.
echo ======================================================================
echo  Done! Your repository is now deployed on GitHub:
echo  https://github.com/LovPrince/%REPO_NAME%
echo ======================================================================
pause
