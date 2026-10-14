@echo off
REM ============================================================
REM  run.bat — launch Photo Booth from the packaged JAR
REM ============================================================

setlocal

cd /d "%~dp0"

set JAR=target\photobooth-1.0.0.jar

if not exist "%JAR%" (
    echo.
    echo [ERROR] JAR not found:
    echo         %CD%\%JAR%
    echo.
    echo Run build.bat first to compile the project.
    echo.
    pause
    exit /b 1
)

REM Sanity check: is Java available?
where java >nul 2>nul
if errorlevel 1 (
    echo [ERROR] java is not on PATH.
    echo         Install a JDK or check your environment variables.
    echo.
    pause
    exit /b 1
)

echo.
echo ============================================================
echo   Launching Photo Booth
echo ============================================================
echo.
echo   Keep this window open while using the app.
echo   The terminal shows frame logs when using Phone mode.
echo.
echo ============================================================
echo.

REM --enable-native-access and --add-opens silence Java 25 warnings
REM from the native webcam library. Harmless if you're on Java 17+.
java ^
    --enable-native-access=ALL-UNNAMED ^
    --add-opens java.base/java.lang=ALL-UNNAMED ^
    -jar "%JAR%"

echo.
echo [Photo Booth exited]
pause
endlocal