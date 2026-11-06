@echo off
setlocal

rem Optional: run-client.bat 192.168.1.10 1099
if not "%~1"=="" set "LAB5_RMI_SERVER_HOST=%~1"
if not "%~2"=="" set "LAB5_RMI_SERVER_PORT=%~2"

call "%~dp0run.bat" client
if errorlevel 1 pause
