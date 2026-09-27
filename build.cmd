@echo off
rem Compiles everything into out\ and runs the tests. No build tool, no downloads.
setlocal
cd /d "%~dp0"
if exist out rmdir /s /q out
mkdir out

dir /s /b src\*.java test\*.java > out\sources.txt
javac -Xlint:all -d out @out\sources.txt
if errorlevel 1 exit /b 1

java -cp out com.waseemansari.evalsampler.Tests
