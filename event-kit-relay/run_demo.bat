@echo off
echo ===================================================
echo   Compiling College Event Kit Relay Project...
echo ===================================================
if not exist "bin" mkdir bin
dir /s /b src\*.java > sources.txt
javac -d bin @sources.txt
if %ERRORLEVEL% NEQ 0 (
    echo Compilation failed!
    exit /b %ERRORLEVEL%
)

echo.
echo ===================================================
echo   Running College Event Kit Relay Showcase...
echo ===================================================
java -cp bin com.relay.Main
