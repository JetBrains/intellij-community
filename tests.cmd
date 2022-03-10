:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

# tests.cmd builds and runs IDEA Community tests in way suitable for calling from CI/CD like TeamCity
# THIS SCRIPTS WORKS FOR ALL SYSTEMS Linux/Windows/macOS
# See README.md for usage scenarios

set -eux
root="$(cd "$(dirname "$0")"; pwd)"
exec "$root/platform/jps-bootstrap/jps-bootstrap.sh" "$@" "$root" intellij.idea.community.build CommunityRunTestsBuildTarget
:CMDSCRIPT

call "%~dp0\platform\jps-bootstrap\jps-bootstrap.cmd" %* "%~dp0." intellij.idea.community.build CommunityRunTestsBuildTarget
EXIT /B %ERRORLEVEL%
