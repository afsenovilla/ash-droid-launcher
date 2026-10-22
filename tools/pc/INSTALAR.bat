@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0instalar.ps1"
echo.
echo Abre el juego y, en la pantalla de carga, elige el idioma "Espanol (Espana)".
pause
