@echo off
cd /d C:\Users\yezhi\WorkBuddy\2026-05-06-task-3\QuitSmoke

echo [1/6] Initializing Git repository...
"D:\Git\cmd\git.exe" init

echo [2/6] Configuring Git user info...
"D:\Git\cmd\git.exe" config user.name "yezhi"
"D:\Git\cmd\git.exe" config user.email "yezhi@users.noreply.github.com"

echo [3/6] Adding files to staging area...
"D:\Git\cmd\git.exe" add .

echo [4/6] Making initial commit...
"D:\Git\cmd\git.exe" commit -m "v1.2.1: Immersive status bar + warning fixes + compatibility"

echo [5/6] Checking commit log...
"D:\Git\cmd\git.exe" log --oneline

echo [6/6] Git initialization complete!
echo.
echo Next steps: Create GitHub repository and push
echo   1. Open https://github.com/new
echo   2. Repository name: QuitSmoke
echo   3. Description: Minimalist quit smoking helper - widget tracks daily smoking count
echo   4. Set to Public
echo   5. Do NOT check README, .gitignore, or License (already have them)
echo   6. Click Create repository
echo   7. Generate Personal Access Token:
echo      https://github.com/settings/tokens/new
echo      Check the 'repo' permission
echo   8. Run these commands to push:
echo.
echo   git remote add origin https://github.com/YOUR_USERNAME/QuitSmoke.git
echo   git branch -M main
echo   git push -u origin main
echo.
echo   When prompted for password, use your Personal Access Token
echo.
pause
