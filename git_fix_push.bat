@echo off
cd /d C:\Users\yezhi\WorkBuddy\2026-05-06-task-3\QuitSmoke

echo [1/5] Adding fixed files...
"D:\Git\cmd\git.exe" add .

echo [2/5] Committing fix...
"D:\Git\cmd\git.exe" commit -m "fix: Gradle wrapper URL + CI workflow permissions"

echo [3/5] Deleting old failed tag...
"D:\Git\cmd\git.exe" tag -d v1.2.1
"D:\Git\cmd\git.exe" push origin :refs/tags/v1.2.1

echo [4/5] Pushing fix...
"D:\Git\cmd\git.exe" push

echo [5/5] Recreating tag v1.2.1...
"D:\Git\cmd\git.exe" tag v1.2.1
"D:\Git\cmd\git.exe" push origin v1.2.1

echo.
echo ============================================
echo   Fix pushed! GitHub Actions should rebuild.
echo   Check: https://github.com/zayer0817/QuitSmoke/actions
echo ============================================
echo.
pause
