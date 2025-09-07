@echo off
cd /d "%~dp0"
echo =====================================
echo Checking files in current directory
echo =====================================
echo.
echo Current directory: %CD%
echo.
echo JAR files:
dir *.jar 2>nul
if %errorlevel% neq 0 echo No JAR files found!
echo.
echo Batch files:
dir *.bat 2>nul
echo.
echo Other files:
dir /b | findstr /v ".jar .bat"
echo.
pause