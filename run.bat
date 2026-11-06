@echo off
setlocal
cd /d "%~dp0"

rem Use JDK 17+ from JAVA_HOME, PATH, IntelliJ, or Android Studio.
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" goto find_maven
set "JAVA_HOME="
where javac.exe >nul 2>&1
if not errorlevel 1 goto find_maven
for /d %%D in ("%ProgramFiles%\JetBrains\IntelliJ IDEA *") do if exist "%%~fD\jbr\bin\javac.exe" set "JAVA_HOME=%%~fD\jbr"
if defined JAVA_HOME goto find_maven
if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\javac.exe" set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
if defined JAVA_HOME goto find_maven
echo JDK 17+ was not found. Install a JDK and set JAVA_HOME.
exit /b 1

:find_maven
set "MAVEN_CMD="
where mvn.cmd >nul 2>&1
if not errorlevel 1 set "MAVEN_CMD=mvn.cmd"
if defined MAVEN_CMD goto run_mode
for /d %%D in ("%ProgramFiles%\JetBrains\IntelliJ IDEA *") do if exist "%%~fD\plugins\maven\lib\maven3\bin\mvn.cmd" set "MAVEN_CMD=%%~fD\plugins\maven\lib\maven3\bin\mvn.cmd"
if defined MAVEN_CMD goto run_mode
echo Maven was not found. Install Maven or open this project in IntelliJ.
exit /b 1

:run_mode
if /i "%~1"=="server-gui" goto run_server_gui
if /i "%~1"=="client" goto run_client
echo Usage: run.bat server-gui
echo        run.bat client
exit /b 1

:run_client
call "%MAVEN_CMD%" javafx:run
exit /b %errorlevel%

:run_server_gui
call "%MAVEN_CMD%" compile javafx:run "-Djavafx.mainClass=vn.edu.rmi.server.ServerApp"
exit /b %errorlevel%
