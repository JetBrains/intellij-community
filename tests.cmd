:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

set -eux
exec "$(cd "$(dirname "$0")"; pwd)/platform/jps-bootstrap/jps-bootstrap.sh" "$@" intellij.idea.community.build CommunityRunTestsBuildTarget
:CMDSCRIPT

call "%~dp0\platform\jps-bootstrap\jps-bootstrap.cmd" %* intellij.idea.community.build CommunityRunTestsBuildTarget
EXIT /B %ERRORLEVEL%
