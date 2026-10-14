:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

# jscpd, the copy-paste detector, at the version that `jscpd.MODULE.bazel` pins.
#
# The wrapper never runs a locally installed jscpd, because a fingerprint of the clone baseline can depend on the
# version. Bazel downloads the release archive through the cache redirector, checks its sha256 and extracts it. Bazel
# does this on Windows too, where jscpd ships only `.tar.gz` archives.
#
# The first run of a version asks Bazel for the absolute path of `//tools:jscpd`, whose `select` picks the host
# binary. It writes the path to `out/jscpd/jscpd-<version>-<host>`. Each later run reads that file and spawns no
# Bazel. The version is the cache key, so a version bump makes an old entry unreachable, not wrong. A path that does
# not exist any more resolves again.
# `plugins/air/docs/decisions/0017-bazel-provided-bt.md` records this design for the old BT wrapper.
#
# `JSCPD_BIN` names an explicit binary and skips Bazel.
#
# A failure of the wrapper exits 78 and keeps the Bazel output on stderr above its message.

# A .cmd file has no shebang, so an ENOEXEC fallback picks the interpreter. Pin bash, as `bt.cmd` does.
[ -z "$BASH_VERSION" ] && exec /bin/bash "$0" "$@"

set -eu

case "$0" in
  */*) self_dir="${0%/*}" ;;
  *) self_dir="." ;;
esac
# The shared steps of the polyglot wrappers: the Git Bash hand-off, the override, the roots, the real path.
. "$self_dir/polyglot-wrapper.sh"
polyglot_hand_off_to_cmd "$0" "$@"
polyglot_exec_override jscpd JSCPD_BIN 78 "$@"

# The repositories of the community module have no name in the ultimate root, so the label goes through `@community`.
polyglot_find_roots "$self_dir"

# The pin file is the one home of the version. A shell loop reads it, so the warm path starts no other process.
version=""
while IFS= read -r line || [ -n "$line" ]; do
  case "$line" in
    'JSCPD_VERSION = "'*'"')
      version="${line#JSCPD_VERSION = \"}"
      version="${version%\"}"
      break
      ;;
  esac
done < "$community_root/tools/jscpd.MODULE.bazel"
if [ -z "$version" ]; then
  echo "jscpd: found no JSCPD_VERSION line in $community_root/tools/jscpd.MODULE.bazel" >&2
  exit 78
fi

# The host is part of the cache name, so a checkout that a guest mounts does not read the path of another machine.
# Bash sets both variables, so the warm path starts no process for them.
cache="$root/out/jscpd/jscpd-$version-$OSTYPE-$HOSTTYPE"
if [ -f "$cache" ]; then
  resolved=""
  IFS= read -r resolved < "$cache" || true
  if [ -n "$resolved" ] && [ -x "$resolved" ]; then
    exec "$resolved" "$@"
  fi
fi

label="${label_prefix}tools:jscpd"
echo "jscpd: the first run of jscpd $version asks Bazel for $label" >&2
if ! files="$(cd "$root" && ./bazel.cmd cquery --ui_event_filters=-info --noshow_progress --output=files "$label")"; then
  echo "jscpd: bazel cquery failed for $label; see the bazel output above" >&2
  exit 78
fi
# The last non-empty line is the answer, so a line that Bazel prints before it cannot be taken for it.
relative=""
while IFS= read -r line; do
  if [ -n "$line" ]; then
    relative="$line"
  fi
done <<< "$files"
if ! output_base="$(cd "$root" && ./bazel.cmd info output_base)"; then
  echo "jscpd: bazel info output_base failed; see the bazel output above" >&2
  exit 78
fi

# `cquery` prints `external/<canonical>/jscpd`, relative to the output base. That directory can be a symlink into the
# repository contents cache. The real path stays valid after `bazel clean --expunge`.
resolved="$output_base/$relative"
resolved="$(polyglot_real_path "$resolved")"
if [ -z "$relative" ] || [ ! -x "$resolved" ]; then
  echo "jscpd: could not resolve jscpd from $label (got '$resolved')" >&2
  exit 78
fi

mkdir -p "${cache%/*}"
printf '%s\n' "$resolved" > "$cache.$$"
mv -f "$cache.$$" "$cache"
exec "$resolved" "$@"

:CMDSCRIPT

setlocal

REM The same design as the POSIX half: JSCPD_BIN first, then the cached path, then one cquery and one info
REM output_base. Bazel writes each answer to a file. Thus no Bazel argument goes through a FOR /F command string, and
REM the exit code of Bazel stays visible. Windows has no readlink, so the cache holds the path under the output base.
REM After an expunge of the output base that path does not exist, and the next run resolves again.

call "%~dp0polyglot-wrapper.cmd"

REM `shift` does not rewrite %*, so the forwarded arguments are collected one at a time.
set "ARGS="
:PARSEARGS
if "%~1"=="" goto :PARSED
set "ARGS=%ARGS% %1"
shift
goto :PARSEARGS
:PARSED

if not defined JSCPD_BIN goto :NOOVERRIDE
if not exist "%JSCPD_BIN%" goto :BADOVERRIDE
"%JSCPD_BIN%"%ARGS%
exit /B %ERRORLEVEL%
:NOOVERRIDE

REM FINDSTR selects the version line, so FOR /F never parses the quotes of the other lines.
set "VERSION="
for /f "tokens=2 delims== " %%v in ('findstr /b /c:"JSCPD_VERSION = " "%COMMUNITY_ROOT%\tools\jscpd.MODULE.bazel"') do set "VERSION=%%~v"
if not defined VERSION goto :NOVERSION

REM The host is part of the cache name, as in the bash half. PROCESSOR_ARCHITECTURE is that of this process, which
REM is enough for a cache name. Bazel picks the host binary through the select of //tools:jscpd.
set "CACHE=%ROOT%\out\jscpd\jscpd-%VERSION%-%OS%-%PROCESSOR_ARCHITECTURE%"
set "RESOLVED="
if exist "%CACHE%" set /p RESOLVED=<"%CACHE%"
if not defined RESOLVED goto :RESOLVE
if exist "%RESOLVED%" goto :RUN

:RESOLVE
set "LABEL=%LABEL_PREFIX%tools:jscpd"
echo jscpd: the first run of jscpd %VERSION% asks Bazel for %LABEL% 1>&2
if not exist "%ROOT%\out\jscpd" mkdir "%ROOT%\out\jscpd"
REM One %RANDOM% read, reused: two reads give two different names.
set "STEM=%CACHE%.%RANDOM%"
pushd "%ROOT%"
call bazel.cmd cquery --ui_event_filters=-info --noshow_progress --output=files "%LABEL%" > "%STEM%.files"
set "STATUS=%ERRORLEVEL%"
if "%STATUS%"=="0" call bazel.cmd info output_base > "%STEM%.base"
if "%STATUS%"=="0" set "STATUS=%ERRORLEVEL%"
popd

REM FOR /F skips empty lines, and the loop overwrites, so the last non-empty line stays.
set "RELATIVE="
set "BASE="
if "%STATUS%"=="0" for /f "usebackq delims=" %%f in ("%STEM%.files") do set "RELATIVE=%%f"
if "%STATUS%"=="0" for /f "usebackq delims=" %%f in ("%STEM%.base") do set "BASE=%%f"
if exist "%STEM%.files" del /q "%STEM%.files"
if exist "%STEM%.base" del /q "%STEM%.base"
if not "%STATUS%"=="0" goto :BAZELFAILED
if not defined RELATIVE goto :RESOLVEFAILED
if not defined BASE goto :RESOLVEFAILED
set "BASE=%BASE:/=\%"
set "RESOLVED=%BASE%\%RELATIVE:/=\%"
if not exist "%RESOLVED%" goto :RESOLVEFAILED

> "%STEM%" echo %RESOLVED%
move /y "%STEM%" "%CACHE%" >nul

:RUN
"%RESOLVED%"%ARGS%
exit /B %ERRORLEVEL%

:BADOVERRIDE
echo jscpd: JSCPD_BIN is not an executable file: %JSCPD_BIN% 1>&2
exit /B 78

:NOVERSION
echo jscpd: found no JSCPD_VERSION line in %COMMUNITY_ROOT%\tools\jscpd.MODULE.bazel 1>&2
exit /B 78

:BAZELFAILED
echo jscpd: bazel failed for %LABEL%; see the bazel output above 1>&2
exit /B 78

:RESOLVEFAILED
echo jscpd: could not resolve jscpd from %LABEL% ^(got '%RESOLVED%'^) 1>&2
exit /B 78
