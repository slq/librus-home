@echo off
setlocal
cd /d "%~dp0"
if not exist ".venv\Scripts\python.exe" goto missing
".venv\Scripts\python.exe" sprawdz_srodowisko.py
pause
exit /b 0
:missing
echo Najpierw uruchom Start.cmd lub Demo.cmd.
pause
exit /b 1
