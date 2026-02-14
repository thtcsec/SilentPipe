Write-Host "=========================================="
Write-Host "     SILENTPIPE CLEAN BUILD SCRIPT"
Write-Host "=========================================="

Write-Host "[1/5] Stopping Gradle Daemon..."
./gradlew --stop

Write-Host "[2/5] Killing lingering processes (Python, Java)..."
Stop-Process -Name "python" -Force -ErrorAction SilentlyContinue
Stop-Process -Name "java" -Force -ErrorAction SilentlyContinue

Write-Host "[3/5] Removing Build Directory..."
if (Test-Path "app/build") {
    Remove-Item -Recurse -Force "app/build"
    Write-Host "    - Built directory removed."
} else {
    Write-Host "    - Build directory not found (already clean)."
}

Write-Host "[4/5] Cleaning Project..."
./gradlew clean

Write-Host "[5/5] Assembling Debug APK..."
./gradlew assembleDebug --no-daemon

Write-Host "=========================================="
Write-Host "     BUILD COMPLETE"
Write-Host "=========================================="
Read-Host -Prompt "Press Enter to exit"
