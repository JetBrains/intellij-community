@echo off
rem ---------------------------------------------------------------------------
rem jython.bat - start script for Jython (adapted from jruby.bat)
rem
rem Environment variables (optional)
rem
rem   JAVA_HOME      Java installation directory
rem
rem   JYTHON_HOME    Jython installation directory
rem
rem   JYTHON_OPTS    Default Jython command line arguments
rem
rem ---------------------------------------------------------------------------

setlocal enabledelayedexpansion

rem ----- Verify and set required environment variables -----------------------

set _JAVA_CMD=java
rem remove surrounding quotes from java home, to be able to safely empty-test it
set _TRIMMED_JAVA_HOME=%JAVA_HOME%
for /f "useback tokens=*" %%a in ('%_TRIMMED_JAVA_HOME%') do set _TRIMMED_JAVA_HOME=%%~a
if not "%_TRIMMED_JAVA_HOME%"=="" (
   set _JAVA_CMD="%JAVA_HOME:"=%\bin\java"
)

rem remove surrounding quotes from jython opts, to be able to safely empty-test it
set _TRIMMED_JYTHON_OPTS=%JYTHON_OPTS%
for /f "useback tokens=*" %%a in ('%_TRIMMED_JYTHON_OPTS%') do set _TRIMMED_JYTHON_OPTS=%%~a
if not "%_TRIMMED_JYTHON_OPTS%"=="" (
   set _JYTHON_OPTS="%_TRIMMED_JYTHON_OPTS%"
)

rem remove surrounding quotes from jython home, to be able to safely empty-test it
set _TRIMMED_JYTHON_HOME=%JYTHON_HOME%
for /f "useback tokens=*" %%a in ('%_TRIMMED_JYTHON_HOME%') do set _TRIMMED_JYTHON_HOME=%%~a
if not "%_TRIMMED_JYTHON_HOME%"=="" (
   set _JYTHON_HOME="%_TRIMMED_JYTHON_HOME%"
   goto gotHome
)

rem try to dynamically determine jython home
rem (this script typically resides in jython home, or in the /bin subdirectory)
pushd "%~dp0%"
set _JYTHON_HOME="%CD%"
popd
if exist %_JYTHON_HOME%\jython-dev.jar goto gotHome
if exist %_JYTHON_HOME%\jython.jar goto gotHome
pushd "%~dp0%\.."
set _JYTHON_HOME="%CD%"
popd
if exist %_JYTHON_HOME%\jython-dev.jar goto gotHome
if exist %_JYTHON_HOME%\jython.jar goto gotHome
rem jython home fallback (if all else fails)
rem if present, %JYTHON_HOME_FALLBACK% is already quoted
set _JYTHON_HOME=%JYTHON_HOME_FALLBACK%

:gotHome
if not exist %_JYTHON_HOME%\jython-dev.jar goto tryComplete
rem prefer built version
set _CP=%_JYTHON_HOME%\jython-dev.jar
for %%j in (%_JYTHON_HOME%\javalib\*.jar) do (
   set _CP=!_CP!;"%%j"
)
goto run

:tryComplete
set _CP=%_JYTHON_HOME%\jython.jar
if exist %_JYTHON_HOME%/jython.jar goto run

echo Cannot find jython-dev.jar or jython.jar in %_JYTHON_HOME%
echo Try running this batch file from the 'bin' directory of an installed Jython,
echo or setting JYTHON_HOME.
goto cleanup

rem ----- Execute the requested command ----------------------------------------

:run
set _JAVA_MEM=-Xmx512m
rem 1152k is the minimum for test_marshal to pass. Windows' default is
rem apparently 1M, anyway
set _JAVA_STACK=-Xss1152k

rem Escape any quotes. Use _S for ', _D for ", and _U to escape _ itself.
rem We have to escape _ itself, otherwise file names with _S and _D
rem will be converted to to wrong ones, when we un-escape. See JRUBY-2821.
set _ARGS=%*
if not defined _ARGS goto argsDone
set _ARGS=%_ARGS:_=_U%
set _ARGS=%_ARGS:'=_S%
set _ARGS=%_ARGS:"=_D%

set _ARGS="%_ARGS%"
set _JYTHON_ARGS=

:scanArgs
rem split args by spaces into first and rest
for /f "tokens=1,*" %%i in (%_ARGS%) do call :getArg "%%i" "%%j"
goto procArg

:getArg
rem remove quotes around first arg
for %%i in (%1) do set _CMP=%%~i
set _ARGS=%2
goto :EOF

:procArg
if ["%_CMP%"] == [""] (
   set _ARGS=
   goto argsDone
)

REM NOTE: If you'd like to use a parameter with underscore in its name,
REM NOTE: use the quoted value: --do_stuff -> --do_Ustuff

if ["%_CMP%"] == ["--"] goto argsDone

if ["%_CMP%"] == ["--jdb"] (
   if "%_TRIMMED_JAVA_HOME%"=="" (
      set _JAVA_CMD=jdb
   ) else (
      set _JAVA_CMD="%JAVA_HOME:"=%\bin\jdb"
   )
   goto :nextArg
)

if ["%_CMP%"] == ["--boot"] (
   set _BOOT_CP=-Xbootclasspath/a:%_CP%
   goto :nextArg
)

if ["%_CMP%"] == ["--print"] (
   set _PRINT=print
   goto :nextArg
)

rem now unescape _D, _S and _Q
set _CMP=%_CMP:_D="%
set _CMP=%_CMP:_S='%
set _CMP=%_CMP:_U=_%
set _CMP1=%_CMP:~0,1%
set _CMP2=%_CMP:~0,2%

rem detect first character is a quote; skip directly to jythonArg
rem this avoids a batch syntax error
if "%_CMP1:"=\\%" == "\\" goto jythonArg

rem removing quote avoids a batch syntax error
if "%_CMP2:"=\\%" == "-J" goto jvmArg

:jythonArg
set _JYTHON_ARGS=%_JYTHON_ARGS% %_CMP%
goto nextArg

:jvmArg
set _VAL=%_CMP:~2%

if "%_VAL:~0,4%" == "-Xmx" (
   set _JAVA_MEM=%_VAL%
) else if "%_VAL:~0,4%" == "-Xss" (
   set _JAVA_STACK=%_VAL%
) else (
   set _JAVA_OPTS=%_JAVA_OPTS% %_VAL%
)

:nextArg
set _CMP=
goto scanArgs

:argsDone
if not defined _BOOT_CP (
  if defined CLASSPATH (
    set CLASSPATH=%_CP:"=%;%CLASSPATH:"=%
  ) else (
    set CLASSPATH=%_CP:"=%
  )
)
set _FULL_CMD=%_JAVA_CMD% %_JAVA_OPTS% %_JAVA_MEM% %_JAVA_STACK% %_BOOT_CP% -Dpython.home=%_JYTHON_HOME% -Dpython.executable="%~f0" -classpath "%CLASSPATH%" org.python.util.jython %_JYTHON_OPTS% %_JYTHON_ARGS% %_ARGS%
if defined _PRINT (
  echo %_FULL_CMD%
) else (
  %_FULL_CMD%
)
set E=%ERRORLEVEL%

:cleanup
set _ARGS=
set _CMP=
set _CMP1=
set _CMP2=
set _CP=
set _BOOT_CP=
set _FULL_CMD=
set _JAVA_CMD=
set _JAVA_OPTS=
set _JAVA_MEM=
set _JAVA_STACK=
set _JYTHON_HOME=
set _JYTHON_OPTS=
set _JYTHON_ARGS=
set _PRINT=
set _TRIMMED_JAVA_HOME=
set _TRIMMED_JYTHON_HOME=
set _TRIMMED_JYTHON_OPTS=

:finish
%COMSPEC% /c exit /b %E%
