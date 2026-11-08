@echo off
setlocal
rem type checks the installed and portable launcher, and the installer when a staged build exists

call "%~dp0env.bat"
if errorlevel 1 exit /b 1

pushd "%~dp0.."
cargo check --release -p RustyClient
if errorlevel 1 goto failed
cargo check --release -p RustyClient --features portable
if errorlevel 1 goto failed
if exist target\stage\RustyClient.exe (
    set "RUSTY_SETUP_PAYLOAD=%CD%\target\stage\RustyClient.exe"
    cargo check --release -p RustyClient --features setup
    if errorlevel 1 goto failed
)
popd
exit /b 0

:failed
popd
exit /b 1
