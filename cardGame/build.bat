@echo off
setlocal

set SRC=src
set RESOURCES=resources
set OUT=target\classes
set JAR=target\cardgame.jar
set MAIN=cardGame.main

:: Find the JDK bin directory from wherever javac lives
for /f "delims=" %%i in ('where javac') do set JAVAC=%%i
for %%i in ("%JAVAC%") do set JDK_BIN=%%~dpi

echo Using JDK: %JDK_BIN%

echo Cleaning previous build...
if exist target rmdir /s /q target
mkdir %OUT%

echo Compiling sources...
dir /s /b %SRC%\*.java > sources.txt
"%JDK_BIN%javac" --release 17 -encoding UTF-8 -d %OUT% @sources.txt
del sources.txt
if errorlevel 1 (
    echo Compilation failed.
    exit /b 1
)

echo Copying resources...
xcopy /e /i /q %RESOURCES% %OUT%

echo Building JAR...
echo Main-Class: %MAIN% > manifest.txt
"%JDK_BIN%jar" cfm %JAR% manifest.txt -C %OUT% .
del manifest.txt

echo.
echo Done! Run with:
echo   java -jar %JAR%
