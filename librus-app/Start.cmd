@echo off
setlocal
cd /d "%~dp0"
if exist ".venv\Scripts\python.exe" goto dependencies

echo Szkolny Panel - przygotowanie srodowiska przy pierwszym uruchomieniu.
where py >nul 2>nul
if not errorlevel 1 goto pythonlauncher
where python >nul 2>nul
if errorlevel 1 goto nopython
python -m venv .venv
if errorlevel 1 goto failure
goto dependencies

:pythonlauncher
py -3 -m venv .venv
if errorlevel 1 goto failure

:dependencies
".venv\Scripts\python.exe" -c "import sys; sys.exit(0 if sys.version_info >= (3,11) else 1)"
if errorlevel 1 goto nopython
if exist ".venv\panel-deps-0.1.0.ok" goto run
echo Pobieram biblioteki. Wymagane jest polaczenie z internetem.
".venv\Scripts\python.exe" -m pip install --disable-pip-version-check -r requirements.txt
if errorlevel 1 goto failure
".venv\Scripts\python.exe" -c "import tkinter, librus_apix, requests, bs4, lxml"
if errorlevel 1 goto failure
type nul > ".venv\panel-deps-0.1.0.ok"

:run
start "" ".venv\Scripts\pythonw.exe" -m szkolny_panel %*
exit /b 0

:nopython
echo.
echo Potrzebny jest Python 3.11 lub nowszy, wraz z tkinter i pip.
echo Zalecany do tej paczki: Python 3.12 lub 3.13 z python.org.
echo https://www.python.org/downloads/windows/
echo Jesli aktualizujesz Pythona, usun tylko folder .venv w paczce i uruchom Start.cmd ponownie.
pause
exit /b 1

:failure
echo.
echo Przygotowanie nie powiodlo sie. Przeczytaj komunikat powyzej i README.md.
echo Nie wpisuj loginu ani hasla Librusa w tym oknie.
pause
exit /b 1
