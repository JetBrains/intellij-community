:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

set -eux
exec "$(cd "$(dirname "$0")"; pwd)/../platform/jps-bootstrap/jps-bootstrap.sh" "$@" intellij.pycharm.community.build PyCharmCommunityInstallersBuildTarget
:CMDSCRIPT

call "%~dp0\..\platform\jps-bootstrap\jps-bootstrap.cmd" %* intellij.pycharm.community.build PyCharmCommunityInstallersBuildTarget
EXIT /B %ERRORLEVEL%
