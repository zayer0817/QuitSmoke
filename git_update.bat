@echo off
setlocal enabledelayedexpansion

cd /d C:\Users\yezhi\WorkBuddy\2026-05-06-task-3\QuitSmoke

set "GIT=D:\Git\cmd\git.exe"
if not exist "%GIT%" set "GIT=git"

echo ============================================
echo   QuitSmoke - Git Update ^& Push
echo ============================================
echo.

echo [1/5] Checking changes...
"%GIT%" status --short
echo.

echo [2/5] Adding all changes...
"%GIT%" add .
echo.

REM --- Commit message ---
REM If passed as argument, use it directly; otherwise ask
if "%~1"=="" (
    echo [3/5] Enter commit message:
    echo   e.g. v1.3.0: Added new feature
    echo.
    set /p "MSG=Commit message: "
) else (
    set "MSG=%~1"
)

if "!MSG!"=="" (
    echo.
    echo ERROR: Commit message cannot be empty!
    echo Please run again with a message, e.g.:
    echo   git_update.bat "fix: widget memory leak"
    echo.
    pause
    exit /b 1
)

echo.
echo Committing: !MSG!
"%GIT%" commit -m "!MSG!"
if errorlevel 1 (
    echo.
    echo NOTE: Commit may have failed because there are no new changes.
    echo       If you already committed, just use git_push_current.bat to push.
    echo.
    pause
    exit /b 1
)
echo.

echo [4/5] Pushing to GitHub...
"%GIT%" push origin main
if errorlevel 1 (
    echo.
    echo Push failed! Try running git_push_current.bat
    echo.
    pause
    exit /b 1
)
echo.

echo [5/5] Done!
echo   Repo: https://github.com/zayer0817/QuitSmoke
echo.
echo   Want to create a Release with downloadable APK?
echo   Run: git_update_release.bat
echo.
pause
