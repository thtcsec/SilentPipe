@echo off
echo ==========================================
echo      SILENTPIPE CLEAN BUILD SCRIPT
echo ==========================================

echo [1/5] Stopping Gradle Daemon...
call gradlew.bat --stop

echo [2/5] Killing lingering processes (Python, Java)...
taskkill /F /IM python.exe >nul 2>&1
taskkill /F /IM java.exe >nul 2>&1

echo [3/5] Removing Build Directory...
if exist "app\build" (
    rmdir /s /q "app\build"
    echo     - Built directory removed.
) else (
    echo     - Build directory not found (already clean).
)

echo [4/5] Cleaning Project...
call gradlew.bat clean

echo [5/5] Assembling Debug APK...
call gradlew.bat assembleDebug --no-daemon

echo ==========================================
echo      BUILD COMPLETE
echo ==========================================
pause
