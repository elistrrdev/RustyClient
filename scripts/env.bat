@echo off
rem prepares msvc, ninja, cmake and the cef location for cargo

set "VSWHERE=%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe"
set "VSINSTALL="
if exist "%VSWHERE%" (
    for /f "usebackq tokens=*" %%i in (`"%VSWHERE%" -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath`) do set "VSINSTALL=%%i"
)
if not defined VSINSTALL (
    echo could not find the visual studio build tools with the c++ workload
    exit /b 1
)

rem vcvars64 looks for vswhere on the path
set "PATH=%ProgramFiles(x86)%\Microsoft Visual Studio\Installer;%PATH%"
call "%VSINSTALL%\VC\Auxiliary\Build\vcvars64.bat" >nul
if errorlevel 1 (
    echo vcvars64.bat failed
    exit /b 1
)

rem ninja and cmake ship inside the build tools
set "PATH=%VSINSTALL%\Common7\IDE\CommonExtensions\Microsoft\CMake\Ninja;%VSINSTALL%\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin;%PATH%"

rem cef lives outside the repo so it survives cargo clean
set "CEF_PATH=%USERPROFILE%\.local\share\cef"
exit /b 0
