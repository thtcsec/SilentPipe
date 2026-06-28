@echo off
REM ============================================================
REM  SilentPipe - Build & Deploy to connected device
REM  Usage:
REM    deploy.bat          -> debug build + install
REM    deploy.bat release  -> release build + install
REM ============================================================

setlocal
cd /d "%~dp0"

set BUILD_TYPE=debug
set TASK=assembleDebug
set APK_PATH=app\build\outputs\apk\debug\app-debug.apk

if /i "%~1"=="release" (
    set BUILD_TYPE=release
    set TASK=assembleRelease
    set APK_PATH=app\build\outputs\apk\release\app-release.apk
)

echo.
echo ========================================
echo  Building SilentPipe [%BUILD_TYPE%]...
echo ========================================
echo.

call gradlew.bat %TASK% --daemon
if errorlevel 1 (
    echo.
    echo [ERROR] Build failed!
    pause
    exit /b 1
)

echo.
echo ========================================
echo  Installing on device...
echo ========================================
echo.

adb install -r "%APK_PATH%"
if errorlevel 1 (
    echo.
    echo [ERROR] Install failed! Make sure device is connected with USB debugging enabled.
    pause
    exit /b 1
)

echo.
echo ========================================
echo  Launching app...
echo ========================================
echo.

adb shell am start -n com.tuhoang.silentpipe/.ui.main.MainActivity
if errorlevel 1 (
    echo [WARN] Could not launch app automatically.
)

echo.
echo [DONE] SilentPipe %BUILD_TYPE% deployed successfully!
echo.
