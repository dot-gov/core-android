@ECHO OFF

REM Start-up script for DexGuard -- optimizer and obfuscator for Android.
REM
REM Note: when passing file names containing spaces to this script,
REM       you'll have to add escaped quotes around them, e.g.
REM       "\"C:/My Directory/My File.txt\""

IF EXIST "%DEXGUARD_HOME%" GOTO home
SET DEXGUARD_HOME=%~dp0\..
:home

java -jar "%DEXGUARD_HOME%\lib\dexguard.jar" %*
