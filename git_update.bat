@echo off
setlocal enabledelayedexpansion

cd /d C:\Users\yezhi\WorkBuddy\2026-05-06-task-3\QuitSmoke

echo ============================================
echo   QuitSmoke - Git Update & Push
echo ============================================
echo.

echo [1/5] Checking changes...
"D:\Git\cmd\git.exe" status --short
echo.

echo [2/5] Adding all changes...
"D:\Git\cmd\git.exe" add .

echo [3/5] Enter commit message:
echo   (e.g. v1.3.0: Added new feature)
echo.
set /p MSG="Commit message: "

if "!MSG!"=="" (
    echo ERROR: Commit message cannot be empty!
    pause
    exit /b 1
)

"D:\Git\cmd\git.exe" commit -m "!MSG!"
echo.

echo [4/5] Pushing to GitHub...
"D:\Git\cmd\git.exe" push
echo.

echo [5/5] Done!
echo   Repo: https://github.com/zayer0817/QuitSmoke
echo.
echo   Want to create a Release with downloadable APK?
echo   Run: git_update_release.bat
echo.
pause
