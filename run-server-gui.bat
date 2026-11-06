@echo off
setlocal

call "%~dp0run.bat" server-gui
if errorlevel 1 pause
