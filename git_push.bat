@echo off
cd /d C:\Users\yezhi\WorkBuddy\2026-05-06-task-3\QuitSmoke

echo ============================================
echo   Push to GitHub - QuitSmoke
echo ============================================
echo.

echo [1/3] Adding remote repository...
"D:\Git\cmd\git.exe" remote add origin https://github.com/zayer0817/QuitSmoke.git

echo [2/3] Renaming branch to main...
"D:\Git\cmd\git.exe" branch -M main

echo [3/3] Pushing to GitHub...
echo.
echo IMPORTANT: When prompted for credentials:
echo   Username: zayer0817
echo   Password: [Your Personal Access Token - NOT your GitHub password]
echo.
echo If you don't have a token yet, open this link in your browser:
echo https://github.com/settings/tokens/new
echo   - Note: WorkBuddy QuitSmoke
echo   - Expiration: 90 days
echo   - Check: repo (full control)
echo   - Click: Generate token
echo   - COPY THE TOKEN IMMEDIATELY (you won't see it again!)
echo.

"D:\Git\cmd\git.exe" push -u origin main

echo.
echo ============================================
echo   Push complete! Check output above for success.
echo   Your repo: https://github.com/zayer0817/QuitSmoke
echo ============================================
echo.
pause
