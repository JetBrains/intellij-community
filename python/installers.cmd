:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

set -eux
root="$(cd "$(dirname "$0")" && cd .. && pwd)"
exec "$root/platform/jps-bootstrap/jps-bootstrap.sh" "$@" "$root" intellij.pycharm.community.build PyCharmCommunityInstallersBuildTarget
:CMDSCRIPT

call "%~dp0\..\platform\jps-bootstrap\jps-bootstrap.cmd" %* "%~dp0\.." intellij.pycharm.community.build PyCharmCommunityInstallersBuildTarget
EXIT /B %ERRORLEVEL%
