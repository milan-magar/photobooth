@echo off
REM ============================================================
REM  build.bat — compile and package Photo Booth into a runnable JAR
REM ============================================================

setlocal

REM Move to the directory this .bat file lives in
cd /d "%~dp0"

echo.
echo ============================================================
echo   Building Photo Booth
echo ============================================================
echo.

REM Sanity check: is Maven available?
where mvn >nul 2>nul
if errorlevel 1 (
    echo [ERROR] Maven is not on PATH.
    echo         Install Maven or check your environment variables.
    echo.
    pause
    exit /b 1
)

REM Sanity check: is the pom.xml here?
if not exist "pom.xml" (
    echo [ERROR] pom.xml not found in:
    echo         %CD%
    echo         Put this .bat file next to pom.xml.
    echo.
    pause
    exit /b 1
)

echo Running: mvn clean package
echo.
call mvn clean package
if errorlevel 1 (
    echo.
    echo [ERROR] Build failed. See Maven output above.
    echo.
    pause
    exit /b 1
)

echo.
echo ============================================================
echo   BUILD SUCCESS
echo   JAR:  %CD%\target\photobooth-1.0.0.jar
echo ============================================================
echo.
pause
endlocal