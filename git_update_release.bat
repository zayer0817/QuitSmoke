@echo off
setlocal enabledelayedexpansion

cd /d C:\Users\yezhi\WorkBuddy\2026-05-06-task-3\QuitSmoke

echo ============================================
echo   QuitSmoke - Create Release
echo ============================================
echo.
echo This will create a version tag and push it.
echo GitHub Actions will automatically build APK
echo and create a Release with download links.
echo.

echo Current tags:
"D:\Git\cmd\git.exe" tag
echo.

echo Enter version tag (e.g. v1.2.1):
set /p TAG="Version: "

if "!TAG!"=="" (
    echo ERROR: Version tag cannot be empty!
    pause
    exit /b 1
)

echo.
echo Creating tag !TAG! and pushing...
"D:\Git\cmd\git.exe" tag !TAG!
"D:\Git\cmd\git.exe" push origin !TAG!

echo.
echo ============================================
echo   Tag pushed! GitHub Actions is building...
echo   Check progress: https://github.com/zayer0817/QuitSmoke/actions
echo   Releases page:  https://github.com/zayer0817/QuitSmoke/releases
echo.
echo   Build takes about 3-5 minutes.
echo   Once done, the APK will be available for download.
echo ============================================
echo.
pause
