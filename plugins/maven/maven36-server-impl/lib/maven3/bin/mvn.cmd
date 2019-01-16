@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.

@REM -----------------------------------------------------------------------------
@REM Apache Maven Startup Script
@REM
@REM Environment Variable Prerequisites
@REM
@REM   JAVA_HOME          Must point at your Java Development Kit installation.
@REM   MAVEN_BATCH_ECHO  (Optional) Set to 'on' to enable the echoing of the batch commands.
@REM   MAVEN_BATCH_PAUSE (Optional) set to 'on' to wait for a key stroke before ending.
@REM   MAVEN_OPTS        (Optional) Java runtime options used when Maven is executed.
@REM   MAVEN_SKIP_RC     (Optional) Flag to disable loading of mavenrc files.
@REM -----------------------------------------------------------------------------

@REM Begin all REM lines with '@' in case MAVEN_BATCH_ECHO is 'on'
@echo off
@REM enable echoing my setting MAVEN_BATCH_ECHO to 'on'
@if "%MAVEN_BATCH_ECHO%"=="on" echo %MAVEN_BATCH_ECHO%

@REM Execute a user defined script before this one
if not "%MAVEN_SKIP_RC%"=="" goto skipRcPre
@REM check for pre script, once with legacy .bat ending and once with .cmd ending
if exist "%USERPROFILE%\mavenrc_pre.bat" call "%USERPROFILE%\mavenrc_pre.bat"
if exist "%USERPROFILE%\mavenrc_pre.cmd" call "%USERPROFILE%\mavenrc_pre.cmd"
:skipRcPre

@setlocal

set ERROR_CODE=0

@REM ==== START VALIDATION ====
if not "%JAVA_HOME%"=="" goto OkJHome
for %%i in (java.exe) do set "JAVACMD=%%~$PATH:i"
goto checkJCmd

:OkJHome
set "JAVACMD=%JAVA_HOME%\bin\java.exe"

:checkJCmd
if exist "%JAVACMD%" goto chkMHome

echo The JAVA_HOME environment variable is not defined correctly >&2
echo This environment variable is needed to run this program >&2
echo NB: JAVA_HOME should point to a JDK not a JRE >&2
goto error

:chkMHome
set "MAVEN_HOME=%~dp0.."
if not "%MAVEN_HOME%"=="" goto stripMHome
goto error

:stripMHome
if not "_%MAVEN_HOME:~-1%"=="_\" goto checkMCmd
set "MAVEN_HOME=%MAVEN_HOME:~0,-1%"
goto stripMHome

:checkMCmd
if exist "%MAVEN_HOME%\bin\mvn.cmd" goto init
goto error
@REM ==== END VALIDATION ====

:init

set MAVEN_CMD_LINE_ARGS=%*

@REM Find the project basedir, i.e., the directory that contains the folder ".mvn".
@REM Fallback to current working directory if not found.

set "MAVEN_PROJECTBASEDIR=%MAVEN_BASEDIR%"
if not "%MAVEN_PROJECTBASEDIR%"=="" goto endDetectBaseDir

set "EXEC_DIR=%CD%"
set "WDIR=%EXEC_DIR%"

@REM Look for the --file switch and start the search for the .mvn directory from the specified
@REM POM location, if supplied.

set FILE_ARG=
:arg_loop
if "%~1" == "-f" (
  set "FILE_ARG=%~2"
  shift
  goto process_file_arg
)
if "%~1" == "--file" (
  set "FILE_ARG=%~2"
  shift
  goto process_file_arg
)
@REM If none of the above, skip the argument
shift
if not "%~1" == "" (
  goto arg_loop
) else (
  goto findBaseDir
)

:process_file_arg
if "%FILE_ARG%" == "" (
  goto findBaseDir
)
if not exist "%FILE_ARG%" (
  echo POM file %FILE_ARG% specified the -f/--file command-line argument does not exist >&2
  goto error
)
if exist "%FILE_ARG%\*" (
  set "POM_DIR=%FILE_ARG%"
) else (
  call :get_directory_from_file "%FILE_ARG%"
)
if not exist "%POM_DIR%" (
  echo Directory %POM_DIR% extracted from the -f/--file command-line argument %FILE_ARG% does not exist >&2
  goto error
)
set "WDIR=%POM_DIR%"
goto findBaseDir

:get_directory_from_file
set "POM_DIR=%~dp1"
:stripPomDir
if not "_%POM_DIR:~-1%"=="_\" goto pomDirStripped
set "POM_DIR=%POM_DIR:~0,-1%"
goto stripPomDir
:pomDirStripped
exit /b

:findBaseDir
cd /d "%WDIR%"
:findBaseDirLoop
if exist "%WDIR%\.mvn" goto baseDirFound
cd ..
IF "%WDIR%"=="%CD%" goto baseDirNotFound
set "WDIR=%CD%"
goto findBaseDirLoop

:baseDirFound
set "MAVEN_PROJECTBASEDIR=%WDIR%"
cd /d "%EXEC_DIR%"
goto endDetectBaseDir

:baseDirNotFound
if "_%EXEC_DIR:~-1%"=="_\" set "EXEC_DIR=%EXEC_DIR:~0,-1%"
set "MAVEN_PROJECTBASEDIR=%EXEC_DIR%"
cd "%EXEC_DIR%"

:endDetectBaseDir

set "jvmConfig=\.mvn\jvm.config"
if not exist "%MAVEN_PROJECTBASEDIR%%jvmConfig%" goto endReadAdditionalConfig

@setlocal EnableExtensions EnableDelayedExpansion
for /F "usebackq delims=" %%a in ("%MAVEN_PROJECTBASEDIR%\.mvn\jvm.config") do set JVM_CONFIG_MAVEN_PROPS=!JVM_CONFIG_MAVEN_PROPS! %%a
@endlocal & set JVM_CONFIG_MAVEN_PROPS=%JVM_CONFIG_MAVEN_PROPS%

:endReadAdditionalConfig

for %%i in ("%MAVEN_HOME%"\boot\plexus-classworlds-*) do set CLASSWORLDS_JAR="%%i"
set CLASSWORLDS_LAUNCHER=org.codehaus.plexus.classworlds.launcher.Launcher

"%JAVACMD%" ^
  %JVM_CONFIG_MAVEN_PROPS% ^
  %MAVEN_OPTS% ^
  %MAVEN_DEBUG_OPTS% ^
  -classpath %CLASSWORLDS_JAR% ^
  "-Dclassworlds.conf=%MAVEN_HOME%\bin\m2.conf" ^
  "-Dmaven.home=%MAVEN_HOME%" ^
  "-Dlibrary.jansi.path=%MAVEN_HOME%\lib\jansi-native" ^
  "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" ^
  %CLASSWORLDS_LAUNCHER% %MAVEN_CMD_LINE_ARGS%
if ERRORLEVEL 1 goto error
goto end

:error
set ERROR_CODE=1

:end
@endlocal & set ERROR_CODE=%ERROR_CODE%

if not "%MAVEN_SKIP_RC%"=="" goto skipRcPost
@REM check for post script, once with legacy .bat ending and once with .cmd ending
if exist "%USERPROFILE%\mavenrc_post.bat" call "%USERPROFILE%\mavenrc_post.bat"
if exist "%USERPROFILE%\mavenrc_post.cmd" call "%USERPROFILE%\mavenrc_post.cmd"
:skipRcPost

@REM pause the script if MAVEN_BATCH_PAUSE is set to 'on'
if "%MAVEN_BATCH_PAUSE%"=="on" pause

if "%MAVEN_TERMINATE_CMD%"=="on" exit %ERROR_CODE%

cmd /C exit /B %ERROR_CODE%
