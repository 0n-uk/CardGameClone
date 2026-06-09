@echo off
setlocal enabledelayedexpansion

set SRC=src
set RESOURCES=resources
set OUT=target\classes
set JAR=target\cardgame.jar
set MAIN=cardGame.main

:: --- Locate jar.exe ---
set JAR_EXE=

:: Option 1: same bin dir as javac
for /f "delims=" %%i in ('where javac 2^>nul') do (
    if not defined JAR_EXE (
        for %%d in ("%%i") do set "CANDIDATE=%%~dpd"
        if exist "!CANDIDATE!jar.exe" set "JAR_EXE=!CANDIDATE!jar.exe"
    )
)

:: Option 2: JAVA_HOME
if not defined JAR_EXE (
    if defined JAVA_HOME (
        if exist "%JAVA_HOME%\bin\jar.exe" set "JAR_EXE=%JAVA_HOME%\bin\jar.exe"
    )
)

:: Option 3: jar directly on PATH
if not defined JAR_EXE (
    for /f "delims=" %%i in ('where jar 2^>nul') do (
        if not defined JAR_EXE set "JAR_EXE=%%i"
    )
)

if not defined JAR_EXE (
    echo ERROR: jar.exe not found. Make sure a JDK ^(not just JRE^) is installed
    echo and either JAVA_HOME is set or the JDK bin folder is on your PATH.
    exit /b 1
)
echo Using jar:   %JAR_EXE%

:: --- Locate javac ---
set JAVAC=
for /f "delims=" %%i in ('where javac 2^>nul') do (
    if not defined JAVAC set "JAVAC=%%i"
)
if not defined JAVAC (
    echo ERROR: javac not found on PATH.
    exit /b 1
)
echo Using javac: %JAVAC%

echo Cleaning previous build...
if exist target rmdir /s /q target
mkdir %OUT%

echo Compiling sources...
dir /s /b %SRC%\*.java > sources.txt
"%JAVAC%" --release 17 -encoding UTF-8 -d %OUT% @sources.txt
del sources.txt
if errorlevel 1 (
    echo Compilation failed.
    exit /b 1
)

echo Copying resources...
xcopy /e /i /q %RESOURCES% %OUT%

echo Building JAR...
echo Main-Class: %MAIN% > manifest.txt
"%JAR_EXE%" cfm %JAR% manifest.txt -C %OUT% .
del manifest.txt
if errorlevel 1 (
    echo JAR creation failed.
    exit /b 1
)

if not exist %JAR% (
    echo ERROR: JAR file was not created.
    exit /b 1
)

echo.
echo Build successful!
echo Run with:   java -jar %JAR%
