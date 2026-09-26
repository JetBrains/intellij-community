# coding=utf-8
"""
Runs tox from current directory.
It supports any runner, but well-known runners (py.test and unittest) are switched to our internal runners to provide
better support


There are two different APIs, so it uses either 3 or 4
"""
import tox

if int(tox.version.__version__.split(".")[0]) >= 4:
    from _jb_tox_runner_4 import run_tox_4

    exit(run_tox_4())
else:
    from _jb_tox_runner_3 import run_tox_3

    exit(run_tox_3())
