@echo off
title Deploy GhostTrack to GitHub - Made by Princeyy
color 0b
echo ======================================================================
echo           DEPLOY GHOSTTRACK TO GITHUB - MADE BY PRINCEYY
echo ======================================================================
echo.

set /p REPO_NAME="Enter your GitHub repository name (press Enter for 'GhostTrack'): "
if "%REPO_NAME%"=="" set REPO_NAME=GhostTrack

echo.
echo Adding and committing latest fixes...
git add .
git commit -m "Fix: Add Gradle wrapper JAR + official gradlew, new app icon, and GitHub Actions CI workflow" 2>nul
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
echo  Done! Your repository is now updated on GitHub:
echo  https://github.com/LovPrince/%REPO_NAME%
echo ======================================================================
pause
