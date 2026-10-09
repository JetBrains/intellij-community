@ECHO OFF
REM The shared step of the CMD half of a polyglot wrapper in this directory. polyglot-wrapper.sh lists the wrappers
REM and holds the design. `call "%~dp0polyglot-wrapper.cmd"` sets three variables in the caller:
REM   COMMUNITY_ROOT  the parent of this directory, in both layouts
REM   ROOT            the checkout root: COMMUNITY_ROOT, or its parent in an Ultimate checkout
REM   LABEL_PREFIX    `//` in a Community checkout, `@community//` in an Ultimate one
REM No `setlocal` here, so the variables reach the caller. A caller runs this before its argument loop, because
REM `shift` also moves %0.
REM `%~dp0` ends with a backslash, so `%~dp0..` is the parent of this directory, and `%%~fd` resolves the `..`, which
REM makes the identity test comparable. A `goto` chain rather than a nested `if (...)` block: plain expansion reads a
REM stale value inside a block.

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
