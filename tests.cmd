:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

set -eux
root="$(cd "$(dirname "$0")"; pwd)"
exec "$root/platform/jps-bootstrap/jps-bootstrap.sh" "$@" "$root" intellij.idea.community.build CommunityRunTestsBuildTarget
:CMDSCRIPT

call "%~dp0\platform\jps-bootstrap\jps-bootstrap.cmd" %* "%~dp0" intellij.idea.community.build CommunityRunTestsBuildTarget
EXIT /B %ERRORLEVEL%
