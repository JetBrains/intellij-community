:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

# tests.cmd builds and runs IDEA Community tests in way suitable for calling from CI/CD like TeamCity
# THIS SCRIPTS WORKS FOR ALL SYSTEMS Linux/Windows/macOS
# See README.md for usage scenarios

# Arguments are passed as JVM options
# and used in org.jetbrains.intellij.build.BuildOptions and org.jetbrains.intellij.build.TestingOptions

# To debug build scripts (CommunityRunTestsBuildTarget) use: --debug
# To debug tests use: -Dintellij.build.test.debug.suspend=true -Dintellij.build.test.debug.port=5005

set -eu
root="$(cd "$(dirname "$0")"; pwd)"

exec "$root/build/run_build_target.sh" "$root" //build:run_tests_build_target "$@"

:CMDSCRIPT

set "ROOT=%~dp0"
set "ROOT=%ROOT:~0,-1%"

"%SystemRoot%\system32\WindowsPowerShell\v1.0\powershell.exe" ^
  -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass ^
  -File "%~dp0build\run_build_target.ps1" ^
  "%ROOT%" ^
  "@community//build:run_tests_build_target" ^
  %*
