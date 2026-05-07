@echo off
cd /d C:\Users\yezhi\WorkBuddy\2026-05-06-task-3\QuitSmoke

echo [1/4] Adding new files...
"D:\Git\cmd\git.exe" add .

echo [2/4] Committing...
"D:\Git\cmd\git.exe" commit -m "v1.2.1: Add GitHub Actions CI/CD + build badge + version update"

echo [3/4] Pushing to GitHub...
"D:\Git\cmd\git.exe" push

echo [4/4] Creating release tag v1.2.1...
"D:\Git\cmd\git.exe" tag v1.2.1
"D:\Git\cmd\git.exe" push origin v1.2.1

echo.
echo ============================================
echo   Done! GitHub Actions is building APK now.
echo   Check: https://github.com/zayer0817/QuitSmoke/actions
echo ============================================
echo.
pause
