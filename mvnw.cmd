@echo off
setlocal
set "MAVEN_OPTS=%MAVEN_OPTS%"
set "PROJECT_REPO=%~dp0.mvn\repository"

if not defined JAVA_HOME (
  if exist "C:\Program Files\Java\jdk-21" set "JAVA_HOME=C:\Program Files\Java\jdk-21"
)

if not defined JAVA_HOME (
  if exist "C:\Program Files\Java\jdk-17" set "JAVA_HOME=C:\Program Files\Java\jdk-17"
)

where mvn >nul 2>nul
if %errorlevel%==0 (
  mvn -Dmaven.repo.local="%PROJECT_REPO%" %*
  exit /b %errorlevel%
)

set "IDEA_MVN=C:\Program Files\JetBrains\IntelliJ IDEA 2025.1.3\plugins\maven\lib\maven3\bin\mvn.cmd"
if exist "%IDEA_MVN%" (
  call "%IDEA_MVN%" -Dmaven.repo.local="%PROJECT_REPO%" %*
  exit /b %errorlevel%
)

echo Maven executable not found. Please install Maven or adjust mvnw.cmd.
exit /b 1
