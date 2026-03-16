:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

# installer.cmd builds PyCharm Community installers
# THIS SCRIPTS WORKS FOR ALL SYSTEMS Linux/Windows/macOS
# See README.md for usage scenarios

# Arguments are passed as JVM options
# and used in org.jetbrains.intellij.build.BuildOptions

# Pass --debug to suspend and wait for debugger at port 5005

set -eu
root="$(cd "$(dirname "$0")" && cd .. && pwd)"

exec "$root/build/run_build_target.sh" "$root" @community//python/build:i_build_target "$@"

:CMDSCRIPT

"%SystemRoot%\system32\WindowsPowerShell\v1.0\powershell.exe" ^
  -NoLogo -NoProfile -NonInteractive -ExecutionPolicy Bypass ^
  -File "%~dp0..\build\run_build_target.ps1" ^
  "%~dp0.." ^
  "@community//python/build:i_build_target" ^
  %*
