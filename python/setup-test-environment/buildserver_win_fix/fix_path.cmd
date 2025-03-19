@echo off
@REM Runs `fix_path.py` against every python either in the same dir or in PYCHARM_PYTHONS
SETLOCAL EnableDelayedExpansion
SET FIX="%~dp0\fix_path.py"

IF "%PYCHARM_PYTHONS%"=="" SET PYCHARM_PYTHONS="%~dp0"

FOR /D %%d in ("%PYCHARM_PYTHONS%\*") do (
    @rem skip conda
    echo "%%d" | find  "conda" > nul
    if !ERRORLEVEL!==1 (
        @rem skip 2.7
        echo "%%d" | find  "27" > nul
        if !ERRORLEVEL!==1 (
          %%d\python.exe  %FIX%
        )
    )
)