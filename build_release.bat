@echo off
echo Building SilentPipe Release APK...
call gradlew.bat clean
call gradlew.bat assembleRelease
if %ERRORLEVEL% EQU 0 (
    echo.
    echo ==========================================
    echo BUILD SUCCESS!
    echo APK location: app/build/outputs/apk/release/
    echo ==========================================
) else (
    echo.
    echo ==========================================
    echo BUILD FAILED! Check input.
    echo ==========================================
)
pause
