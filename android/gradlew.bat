@echo off
setlocal
set GRADLE_VERSION=9.6
set CACHE_DIR=%USERPROFILE%\.gradle\nexus-wrapper
set GRADLE_HOME=%CACHE_DIR%\gradle-%GRADLE_VERSION%
set ZIP=%CACHE_DIR%\gradle-%GRADLE_VERSION%-bin.zip
if not exist "%GRADLE_HOME%\bin\gradle.bat" (
  if not exist "%CACHE_DIR%" mkdir "%CACHE_DIR%"
  if not exist "%ZIP%" powershell -NoProfile -Command "Invoke-WebRequest -UseBasicParsing 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%ZIP%'"
  powershell -NoProfile -Command "Expand-Archive -Force '%ZIP%' '%CACHE_DIR%'"
)
call "%GRADLE_HOME%\bin\gradle.bat" -p "%~dp0" %*
endlocal
