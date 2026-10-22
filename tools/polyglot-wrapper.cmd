@ECHO OFF
REM The shared steps of the CMD half of a polyglot wrapper in this directory. polyglot-wrapper.sh lists the wrappers
REM and holds the design. This file has two steps, and the first argument picks one:
REM
REM   call "%~dp0polyglot-wrapper.cmd"
REM     sets four variables in the caller:
REM       COMMUNITY_ROOT    the parent of this directory, in both layouts
REM       ROOT              the checkout root: COMMUNITY_ROOT, or its parent in an Ultimate checkout
REM       LABEL_PREFIX      `//` in a Community checkout, `@community//` in an Ultimate one
REM       POLYGLOT_WRAPPER  the path of this file, for the `build` step below. A caller runs that step after
REM                         its argument loop, and `shift` has moved %0 by then, so `%~dp0` of the caller
REM                         is no longer this directory.
REM
REM   call "%POLYGLOT_WRAPPER%" build <name> <target> <code> <basename> [log]
REM     builds <target>, the Bazel target of the wrapper <name>, and sets RESOLVED in the caller to the executable
REM     it made, whose base name is <basename> (with `.exe`). A failed build, a failed reader or a missing executable
REM     prints its message and exits with <code>, the infrastructure code of the wrapper, so the caller checks
REM     ERRORLEVEL. With `log`, the build output goes to a log file and reaches stderr only when the build fails
REM     (note 6 of vm.cmd); without it, the output goes to stderr as it comes. The bash step logs only when stderr
REM     is a pipe; CMD has no test for that, so `log` here applies on a console too. The steps are those of
REM     `polyglot_build_binary` in polyglot-wrapper.sh: a `bazel build` and not a `bazel run`, for the Skyframe
REM     deletion that the bash step names, and the path of the binary from the build event file, which
REM     polyglot-built-file.ps1 reads.
REM
REM No `setlocal` here, so the variables reach the caller. A caller runs the first step before its argument loop,
REM because `shift` also moves %0. The `build` step needs ROOT and so runs after the first step.
REM `%~dp0` ends with a backslash, so `%~dp0..` is the parent of this directory, and `%%~fd` resolves the `..`, which
REM makes the identity test comparable. A `goto` chain rather than a nested `if (...)` block: plain expansion reads a
REM stale value inside a block.

if /I "%~1"=="build" goto :BUILD

set "POLYGLOT_WRAPPER=%~f0"
for %%d in ("%~dp0..") do set "COMMUNITY_ROOT=%%~fd"
for %%d in ("%COMMUNITY_ROOT%\..") do set "POLYGLOT_PARENT=%%~fd"
for %%d in ("%POLYGLOT_PARENT%\community") do set "POLYGLOT_PARENT_COMMUNITY=%%~fd"
set "ROOT=%COMMUNITY_ROOT%"
set "LABEL_PREFIX=//"
if not exist "%POLYGLOT_PARENT%\MODULE.bazel" goto :DONE
if not exist "%POLYGLOT_PARENT%\bazel.cmd" goto :DONE
if not exist "%POLYGLOT_PARENT_COMMUNITY%\" goto :DONE
if /I not "%POLYGLOT_PARENT_COMMUNITY%"=="%COMMUNITY_ROOT%" goto :DONE
set "ROOT=%POLYGLOT_PARENT%"
set "LABEL_PREFIX=@community//"
:DONE
set "POLYGLOT_PARENT="
set "POLYGLOT_PARENT_COMMUNITY="
exit /B 0

:BUILD
set "POLYGLOT_NAME=%~2"
set "POLYGLOT_TARGET=%~3"
set "POLYGLOT_CODE=%~4"
set "POLYGLOT_BASENAME=%~5"
set "POLYGLOT_OUTPUT=%~6"
set "RESOLVED="
if not exist "%ROOT%\out\air" mkdir "%ROOT%\out\air"
REM One %RANDOM% read, reused: two reads give two different names.
set "POLYGLOT_STAMP=%RANDOM%"
set "POLYGLOT_EVENTS=%ROOT%\out\air\%POLYGLOT_NAME%.events.%POLYGLOT_STAMP%.json"
set "POLYGLOT_LOG=%ROOT%\out\air\%POLYGLOT_NAME%.build.%POLYGLOT_STAMP%.log"
set "POLYGLOT_PATH=%ROOT%\out\air\%POLYGLOT_NAME%.path.%POLYGLOT_STAMP%.txt"
REM `--config=bt` (`community/common.bazelrc`) keeps the progress and result lines of Bazel short, and nothing of the
REM build reaches stdout, which a wrapper may answer on.
pushd "%ROOT%"
if /I "%POLYGLOT_OUTPUT%"=="log" goto :BUILDLOG
call bazel.cmd build --config=bt --build_event_json_file="%POLYGLOT_EVENTS%" --nobuild_event_json_file_path_conversion "%POLYGLOT_TARGET%" 1>&2
set "POLYGLOT_STATUS=%ERRORLEVEL%"
popd
goto :BUILT
:BUILDLOG
call bazel.cmd build --config=bt --build_event_json_file="%POLYGLOT_EVENTS%" --nobuild_event_json_file_path_conversion "%POLYGLOT_TARGET%" >"%POLYGLOT_LOG%" 2>&1
set "POLYGLOT_STATUS=%ERRORLEVEL%"
popd
if not "%POLYGLOT_STATUS%"=="0" type "%POLYGLOT_LOG%" 1>&2
del /q "%POLYGLOT_LOG%"
:BUILT
if not "%POLYGLOT_STATUS%"=="0" goto :BUILDFAILED

REM The reader writes the path, or an empty line, to a file. A `for /f` over a backquoted command would have to carry
REM the quotes of two paths through `cmd /c`, which strips a leading quote under its own rules.
"%SystemRoot%\system32\WindowsPowerShell\v1.0\powershell.exe" -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "%~dp0polyglot-built-file.ps1" "%POLYGLOT_EVENTS%" "%POLYGLOT_BASENAME%" >"%POLYGLOT_PATH%"
set "POLYGLOT_STATUS=%ERRORLEVEL%"
set /p RESOLVED=<"%POLYGLOT_PATH%"
del /q "%POLYGLOT_EVENTS%" "%POLYGLOT_PATH%"
if not "%POLYGLOT_STATUS%"=="0" goto :READERFAILED
if not defined RESOLVED goto :RESOLVEFAILED
if not exist "%RESOLVED%" goto :RESOLVEFAILED
goto :BUILDCLEANUP

REM A failure keeps the POLYGLOT_* variables, because the caller exits with the code at once.
:BUILDFAILED
if exist "%POLYGLOT_EVENTS%" del /q "%POLYGLOT_EVENTS%"
echo %POLYGLOT_NAME%: could not build %POLYGLOT_TARGET%; see the bazel output above 1>&2
exit /B %POLYGLOT_CODE%

REM The reader itself failed: no PowerShell at its path, a language mode that refuses the script, or a URI that is
REM not one. Its own error is on stderr above this line.
:READERFAILED
echo %POLYGLOT_NAME%: could not read the build event file of %POLYGLOT_TARGET%; see the PowerShell output above 1>&2
exit /B %POLYGLOT_CODE%

:RESOLVEFAILED
echo %POLYGLOT_NAME%: could not resolve %POLYGLOT_BASENAME% from %POLYGLOT_TARGET% ^(got '%RESOLVED%'^) 1>&2
exit /B %POLYGLOT_CODE%

:BUILDCLEANUP
set "POLYGLOT_NAME="
set "POLYGLOT_TARGET="
set "POLYGLOT_CODE="
set "POLYGLOT_BASENAME="
set "POLYGLOT_OUTPUT="
set "POLYGLOT_STAMP="
set "POLYGLOT_EVENTS="
set "POLYGLOT_LOG="
set "POLYGLOT_PATH="
set "POLYGLOT_STATUS="
exit /B 0
