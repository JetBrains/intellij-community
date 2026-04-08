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
  echo "  Example: -Dintellij.build.test.debug.suspend=true -Dintellij.build.test.debug.port=5005"
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
      module="$2"
      shift 2
      ;;
    --test)
      if [ -n "$test_pattern" ]; then
        echo "Error: --test may only be specified once" >&2
        echo >&2
        show_help >&2
        exit 1
      fi
      test_pattern="$2"
      shift 2
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

set "MODULE="
set "TEST_PATTERN="
set "BAZEL_EXTRA="

:PARSE_LOOP
if "%~1"=="--help" goto :DO_HELP
if "%~1"=="-h" goto :DO_HELP
if "%~1"=="--module" goto :SET_MODULE
if "%~1"=="--test" goto :SET_TEST
if "%~1"=="" goto :CHECK_REQUIRED
if "%~1"=="--debug" (
  set "BAZEL_EXTRA=%BAZEL_EXTRA% --debug"
) else (
  set "BAZEL_EXTRA=%BAZEL_EXTRA% --jvm_flag=%~1"
)
shift
goto :PARSE_LOOP

:SET_MODULE
if defined MODULE (
  echo Error: --module may only be specified once 1>&2
  echo. 1>&2
  call :PRINT_HELP 1>&2
  exit /b 1
)
set "MODULE=%~2"
shift
shift
goto :PARSE_LOOP

:SET_TEST
if defined TEST_PATTERN (
  echo Error: --test may only be specified once 1>&2
  echo. 1>&2
  call :PRINT_HELP 1>&2
  exit /b 1
)
set "TEST_PATTERN=%~2"
shift
shift
goto :PARSE_LOOP

:CHECK_REQUIRED
if not defined MODULE (
  echo Error: --module is required 1>&2
  echo. 1>&2
  call :PRINT_HELP 1>&2
  exit /b 1
)
if not defined TEST_PATTERN (
  echo Error: --test is required 1>&2
  echo. 1>&2
  call :PRINT_HELP 1>&2
  exit /b 1
)

rem '#' means method selector (e.g. com.example.MyTest#myMethod), use intellij.build.test.simple.patterns for exact matching
set "TEST_PATTERN_PROP=intellij.build.test.patterns"
echo %TEST_PATTERN% | findstr /C:"#" >nul 2>&1 && set "TEST_PATTERN_PROP=intellij.build.test.simple.patterns"

cd /d "%ROOT%"
echo Running: %ROOT%\bazel.cmd run //build:run_tests_build_target -- "--jvm_flag=-Dintellij.build.test.main.module=%MODULE%" "--jvm_flag=-D%TEST_PATTERN_PROP%=%TEST_PATTERN%"%BAZEL_EXTRA%
"%ROOT%\bazel.cmd" run //build:run_tests_build_target -- "--jvm_flag=-Dintellij.build.test.main.module=%MODULE%" "--jvm_flag=-D%TEST_PATTERN_PROP%=%TEST_PATTERN%"%BAZEL_EXTRA%
exit /b %ERRORLEVEL%

:DO_HELP
call :PRINT_HELP
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
echo   Example: -Dintellij.build.test.debug.suspend=true -Dintellij.build.test.debug.port=5005
exit /b 0
