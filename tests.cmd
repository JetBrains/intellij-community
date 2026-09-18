:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

[ -z "$BASH_VERSION" ] && exec /bin/bash "$0" "$@"

# tests.cmd builds and runs IDEA Community tests in way suitable for calling from CI/CD like TeamCity
# THIS SCRIPTS WORKS FOR ALL SYSTEMS Linux/Windows/macOS
# See community/README.md for usage scenarios

# Arguments are passed as JVM options
# and used in org.jetbrains.intellij.build.BuildOptions and org.jetbrains.intellij.build.TestingOptions

show_help() {
  echo "Usage: tests.cmd --module <module> --test <pattern> [options]"
  echo ""
  echo "Required:"
  echo "  --module <module>    Name of the JPS module which contains the test classes"
  echo "  --test <pattern>     Full test class name (FQN) or wild card pattern (e.g. com.intellij.*Test) or exact FQN#methodName"
  echo ""
  echo "Options:"
  echo "  --debug              Debug build scripts JVM process"
  echo "  --help               Show this help message"
  echo ""
  echo "Additional options are passed as JVM flags to org.jetbrains.intellij.build.TestingOptions"
  echo "  Example: -Dintellij.build.test.debug.enabled=true -Dintellij.build.test.debug.suspend=true -Dintellij.build.test.debug.port=5005"
}

set -eu
root="$(cd "$(dirname "$0")"; pwd)"

module=""
test_pattern=""
extra_args=()

while [ $# -gt 0 ]; do
  case "$1" in
    --help|-h)
      show_help
      exit 0
      ;;
    --module)
      if [ -n "$module" ]; then
        echo "Error: --module may only be specified once" >&2
        echo >&2
        show_help >&2
        exit 1
      fi
      # --module can be the last argument. A plain "shift 2" fails then, and
      # set -e stops the script without a message.
      module="${2-}"
      shift $(( $# > 1 ? 2 : 1 ))
      ;;
    --test)
      if [ -n "$test_pattern" ]; then
        echo "Error: --test may only be specified once" >&2
        echo >&2
        show_help >&2
        exit 1
      fi
      # --test can be the last argument. See the note above --module.
      test_pattern="${2-}"
      shift $(( $# > 1 ? 2 : 1 ))
      ;;
    *)
      extra_args+=("$1")
      shift
      ;;
  esac
done

if [ -z "$module" ]; then
  echo "Error: --module is required" >&2
  echo >&2
  show_help >&2
  exit 1
fi

if [ -z "$test_pattern" ]; then
  echo "Error: --test is required" >&2
  echo >&2
  show_help >&2
  exit 1
fi

# Modules listed in this file run their tests only under Bazel.
bazel_migrated_modules_file="build/bazel-migrated-test-modules.txt"
if [ ! -f "$root/$bazel_migrated_modules_file" ]; then
  echo "Error: $bazel_migrated_modules_file is missing" >&2
  exit 1
fi
bazel_migrated_pattern=""
while IFS= read -r pattern || [ -n "$pattern" ]; do
  pattern="${pattern%$'\r'}"
  case "$pattern" in
    ''|'#'*) ;;
    *'*') if [[ "$module" == "${pattern%\*}"* ]]; then bazel_migrated_pattern="$pattern"; break; fi ;;
    *) if [ "$module" = "$pattern" ]; then bazel_migrated_pattern="$pattern"; break; fi ;;
  esac
done < "$root/$bazel_migrated_modules_file"
if [ -n "$bazel_migrated_pattern" ]; then
  echo "Error: the tests of module '$module' run only under Bazel. tests.cmd cannot run them." >&2
  echo "The module matches '$bazel_migrated_pattern' in $bazel_migrated_modules_file." >&2
  echo "Use: ./bazel.cmd test <label of the *_test target in the BUILD.bazel next to the module .iml> --test_filter=<ClassName>" >&2
  echo "See: .agents/skills/testing/SKILL.md" >&2
  exit 1
fi

# See java_stub_template.txt on how bazel java wrapper works
# '#' means method selector (e.g. com.example.MyTest#myMethod), use intellij.build.test.simple.patterns for exact matching
if [[ "$test_pattern" == *"#"* ]]; then
  test_pattern_prop="intellij.build.test.simple.patterns"
else
  test_pattern_prop="intellij.build.test.patterns"
fi

args=()
for arg in "-Dintellij.build.test.main.module=$module" "-D$test_pattern_prop=$test_pattern" "${extra_args[@]+"${extra_args[@]}"}"; do
  if [ "$arg" = "--debug" ]; then
    args+=("--debug")
  else
    args+=("--jvm_flag=$arg")
  fi
done

cd "$root"
echo "Running: $root/bazel.cmd run //build:run_tests_build_target -- ${args[*]}"
exec /bin/bash "$root/bazel.cmd" run //build:run_tests_build_target -- "${args[@]}"

:CMDSCRIPT

set "ROOT=%~dp0"
set "ROOT=%ROOT:~0,-1%"

rem cmd.exe splits %1..%9 on '=' as well as on space, so a shift loop turns
rem -Dkey=value into two arguments. %* keeps the raw command line. See MRI-5341.
rem A quoted argument which holds an '&', a '|', a '<' or a '>' breaks this line,
rem because the quotes of the argument flip the quote state.
set "ARGS=%*"

rem The parser below runs under delayed expansion, which eats a '!' from a percent
rem expansion. These two lines run before it. '@A' stands for a '@' and '@Q' stands
rem for a quote, so the parser meets no quote and keeps every '!'.
if defined ARGS set "ARGS=%ARGS:@=@A%"
if defined ARGS set "ARGS=%ARGS:"=@Q%"

setlocal enabledelayedexpansion

set "MODULE="
set "TEST_PATTERN="
rem A delayed reference to a variable which holds nothing stays in the text, so
rem BAZEL_EXTRA starts with a space.
set "BAZEL_EXTRA= "
set "EXPECT="
set "STOP="

call :SCAN_ARGS

rem A redirect on a "call :label" line makes cmd.exe lose the label in a file with
rem LF line ends, so the redirect belongs to the block below.
if "!STOP!"=="HELP" (
  call :PRINT_HELP
  exit /b 0
)
if "!STOP!"=="DUPLICATE_MODULE" >&2 (
  echo Error: --module may only be specified once
  echo.
  call :PRINT_HELP
  exit /b 1
)
if "!STOP!"=="DUPLICATE_TEST" >&2 (
  echo Error: --test may only be specified once
  echo.
  call :PRINT_HELP
  exit /b 1
)
if not defined MODULE >&2 (
  echo Error: --module is required
  echo.
  call :PRINT_HELP
  exit /b 1
)
if not defined TEST_PATTERN >&2 (
  echo Error: --test is required
  echo.
  call :PRINT_HELP
  exit /b 1
)

rem Modules listed in this file run their tests only under Bazel.
set "BAZEL_MIGRATED_MODULES_FILE=build\bazel-migrated-test-modules.txt"
if not exist "%ROOT%\%BAZEL_MIGRATED_MODULES_FILE%" (
  echo Error: %BAZEL_MIGRATED_MODULES_FILE% is missing 1>&2
  exit /b 1
)
set "BAZEL_MIGRATED_PATTERN="
for /f "usebackq eol=# tokens=* delims=" %%P in ("%ROOT%\%BAZEL_MIGRATED_MODULES_FILE%") do call :MATCH_BAZEL_MIGRATED_PATTERN "%%P"
if defined BAZEL_MIGRATED_PATTERN (
  echo Error: the tests of module '!MODULE!' run only under Bazel. tests.cmd cannot run them. 1>&2
  echo The module matches '!BAZEL_MIGRATED_PATTERN!' in %BAZEL_MIGRATED_MODULES_FILE%. 1>&2
  echo Use: .\bazel.cmd test ^<label of the *_test target in the BUILD.bazel next to the module .iml^> --test_filter=^<ClassName^> 1>&2
  echo See: .agents\skills\testing\SKILL.md 1>&2
  exit /b 1
)

rem '#' means method selector (e.g. com.example.MyTest#myMethod), use intellij.build.test.simple.patterns for exact matching
set "TEST_PATTERN_PROP=intellij.build.test.patterns"
if not "!TEST_PATTERN!"=="!TEST_PATTERN:#=!" set "TEST_PATTERN_PROP=intellij.build.test.simple.patterns"

set "RUN_ARGS="--jvm_flag=-Dintellij.build.test.main.module=!MODULE!" "--jvm_flag=-D!TEST_PATTERN_PROP!=!TEST_PATTERN!"!BAZEL_EXTRA!"

rem bazel.cmd is a batch file, and it runs in this cmd.exe. Delayed expansion
rem would eat a '!' from the '%*' line of bazel.cmd, so the script leaves it
rem here. The inner setlocal keeps the '!' of a value in the line below.
setlocal disabledelayedexpansion
endlocal & endlocal & set "RUN_ARGS=%RUN_ARGS%"

cd /d "%ROOT%"
echo Running: %ROOT%\bazel.cmd run //build:run_tests_build_target -- %RUN_ARGS%
"%ROOT%\bazel.cmd" run //build:run_tests_build_target -- %RUN_ARGS%
exit /b %ERRORLEVEL%

:MATCH_BAZEL_MIGRATED_PATTERN
rem One pattern from the file. The first match wins.
if defined BAZEL_MIGRATED_PATTERN exit /b 0
set "MIGRATED_PATTERN=%~1"
if "!MIGRATED_PATTERN:~-1!"=="*" goto :MATCH_BAZEL_MIGRATED_PREFIX
echo(!MODULE!| findstr /x /l /c:"!MIGRATED_PATTERN!" >nul && set "BAZEL_MIGRATED_PATTERN=!MIGRATED_PATTERN!"
exit /b 0

:MATCH_BAZEL_MIGRATED_PREFIX
echo(!MODULE!| findstr /b /l /c:"!MIGRATED_PATTERN:~0,-1!" >nul && set "BAZEL_MIGRATED_PATTERN=!MIGRATED_PATTERN!"
exit /b 0

rem Reads ARGS character by character and calls :HANDLE_TOKEN for each token.
rem A '@Q' pair opens or closes a quoted part. A space or a tab ends the token
rem outside a quoted part, and stays in the token inside one. The loop stops
rem when ARGS holds nothing, and 8191 is the command line limit of cmd.exe.
:SCAN_ARGS
set "TOK="
set "STARTED="
set "INQUOTE="
for /l %%I in (1,1,8191) do if defined ARGS (
  set "PAIR=!ARGS:~0,2!"
  if "!PAIR!"=="@A" (
    set "TOK=!TOK!@"
    set "STARTED=1"
    set "ARGS=!ARGS:~2!"
  ) else if "!PAIR!"=="@Q" (
    if defined INQUOTE (set "INQUOTE=") else (set "INQUOTE=1")
    set "STARTED=1"
    set "ARGS=!ARGS:~2!"
  ) else (
    set "CHAR=!ARGS:~0,1!"
    set "ARGS=!ARGS:~1!"
    set "DELIMITER="
    if not defined INQUOTE if "!CHAR!"==" " set "DELIMITER=1"
    if not defined INQUOTE if "!CHAR!"=="	" set "DELIMITER=1"
    if defined DELIMITER (
      if defined STARTED call :HANDLE_TOKEN
      set "TOK="
      set "STARTED="
    ) else (
      set "TOK=!TOK!!CHAR!"
      set "STARTED=1"
    )
  )
)
if defined STARTED call :HANDLE_TOKEN
exit /b 0

rem Handles one token. A subroutine cannot stop the script, so STOP holds the
rem first event which ends the parse. The main flow reads STOP after the scan.
:HANDLE_TOKEN
if defined STOP exit /b 0
if defined EXPECT (
  if "!EXPECT!"=="MODULE" set "MODULE=!TOK!"
  if "!EXPECT!"=="TEST" set "TEST_PATTERN=!TOK!"
  set "EXPECT="
  exit /b 0
)
if "!TOK!"=="--help" (set "STOP=HELP" & exit /b 0)
if "!TOK!"=="-h" (set "STOP=HELP" & exit /b 0)
if "!TOK!"=="--module" (
  if defined MODULE (set "STOP=DUPLICATE_MODULE") else (set "EXPECT=MODULE")
  exit /b 0
)
if "!TOK!"=="--test" (
  if defined TEST_PATTERN (set "STOP=DUPLICATE_TEST") else (set "EXPECT=TEST")
  exit /b 0
)
if "!TOK!"=="--debug" (
  set "BAZEL_EXTRA=!BAZEL_EXTRA! --debug"
  exit /b 0
)
rem The quotes below group the value. A program reads a '\"' as a literal quote,
rem so a backslash at the end of the value needs a double.
set "TAIL="
for /l %%I in (1,1,64) do if "!TOK:~-1!"=="\" (
  set "TOK=!TOK:~0,-1!"
  set "TAIL=\\!TAIL!"
)
set "BAZEL_EXTRA=!BAZEL_EXTRA! "--jvm_flag=!TOK!!TAIL!""
exit /b 0

:PRINT_HELP
echo Usage: tests.cmd --module ^<module^> --test ^<pattern^> [options]
echo.
echo Required:
echo   --module ^<module^>    Name of the JPS module which contains the test classes
echo   --test ^<pattern^>     Full test class name (FQN) or wild card pattern (e.g. com.intellij.*Test) or exact FQN#methodName
echo.
echo Options:
echo   --debug              Debug build scripts JVM process
echo   --help               Show this help message
echo.
echo Additional options are passed as JVM flags to org.jetbrains.intellij.build.TestingOptions
echo   Example: -Dintellij.build.test.debug.enabled=true -Dintellij.build.test.debug.suspend=true -Dintellij.build.test.debug.port=5005
exit /b 0
