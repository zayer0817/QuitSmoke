@echo off
setlocal

cd /d "%~dp0"

set "GIT_EXE=D:\Git\cmd\git.exe"
if not exist "%GIT_EXE%" (
    set "GIT_EXE=git"
)

echo ============================================
echo   QuitSmoke - Push Current Commit
echo ============================================
echo.

echo [1/4] Current repository status...
"%GIT_EXE%" status --short --branch
if errorlevel 1 goto error
echo.

echo [2/5] Temporarily stashing local unstaged changes...
echo      Untracked files, such as this helper bat, will stay in place.
"%GIT_EXE%" stash push -m "temporary local changes before push_current"
if errorlevel 1 goto error
echo.

echo [3/5] Pulling latest GitHub changes...
echo      This keeps your local commit and places it on top of origin/main.
"%GIT_EXE%" pull --rebase origin main
if errorlevel 1 goto restore_error
echo.

echo [4/5] Pushing to GitHub...
"%GIT_EXE%" push origin main
if errorlevel 1 goto restore_error
echo.

echo [5/5] Restoring local unstaged changes...
"%GIT_EXE%" stash pop
echo.

echo Final status...
"%GIT_EXE%" status --short --branch
echo.

echo ============================================
echo   Push complete!
echo   Repo: https://github.com/zayer0817/QuitSmoke
echo ============================================
echo.
pause
exit /b 0

:restore_error
echo.
echo Restoring local unstaged changes before exiting...
"%GIT_EXE%" stash pop
echo.
goto error

:error
echo.
echo ============================================
echo   Git command failed.
echo   If there is a conflict, open Android Studio
echo   and resolve the files marked by Git.
echo ============================================
echo.
pause
exit /b 1
