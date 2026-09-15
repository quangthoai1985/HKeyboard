@echo off
REM =====================================================================
REM Build UniKey reference engine voi wrapper hook (tools/ref/ref.exe)
REM
REM Can: Visual Studio Build Tools (co cl.exe) va ma nguon UniKey 1.0.4.
REM Neu chua co ma nguon, chay truoc:
REM     powershell -NoProfile -ExecutionPolicy Bypass -File tools\fetch-unikey.ps1
REM =====================================================================
setlocal
set VCVARS=C:\Program Files (x86)\Microsoft Visual Studio\18\BuildTools\VC\Auxiliary\Build\vcvars64.bat
set SRC=%~dp0..\..\x-unikey-1.0.4\src
cd /d "%~dp0"

if not exist "%SRC%\ukengine\ukengine.cpp" (
  echo [LOI] Khong thay ma nguon UniKey tai "%SRC%"
  echo       Chay: powershell -NoProfile -ExecutionPolicy Bypass -File tools\fetch-unikey.ps1
  exit /b 1
)

if not exist "%VCVARS%" (
  echo [LOI] Khong thay Visual Studio Build Tools tai "%VCVARS%"
  echo       Sua lai bien VCVARS trong file nay cho dung duong dan tren may ban.
  exit /b 1
)

call "%VCVARS%" >nul 2>&1
powershell -NoProfile -ExecutionPolicy Bypass -File makewrap.ps1
if errorlevel 1 exit /b 1
powershell -NoProfile -ExecutionPolicy Bypass -File makeinputproc.ps1
if errorlevel 1 exit /b 1
cl /nologo /EHsc /D_CRT_SECURE_NO_WARNINGS /DUNIKEYHOOK ^
  /I "%SRC%\ukengine" /I "%SRC%\vnconv" /I "%SRC%\ukinterface" /I "%SRC%\byteio" ^
  refmain.cpp ukengine_wrap.cpp inputproc_wrap.cpp ^
  "%SRC%\vnconv\charset.cpp" ^
  "%SRC%\vnconv\data.cpp" ^
  "%SRC%\vnconv\convert.cpp" ^
  "%SRC%\vnconv\error.cpp" ^
  "%SRC%\vnconv\pattern.cpp" ^
  "%SRC%\byteio\byteio.cpp" ^
  /Fe:ref.exe
endlocal
