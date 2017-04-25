# coding=utf-8
import re
import sys

import pytest
from _pytest.config import get_plugin_manager

from _jb_runner_tools import jb_start_tests, jb_patch_separator, jb_doc_args, JB_DISABLE_BUFFERING
from teamcity import pytest_plugin



if __name__ == '__main__':
    path, targets, additional_args = jb_start_tests()
    sys.argv += additional_args
    joined_targets = jb_patch_separator(targets, fs_glue="/", python_glue="::", fs_to_python_glue=".py::")
    # When file is launched in py.test it should be file.py: you can't provide it as bare module
    joined_targets = [t + ".py" if ":" not in t else t for t in joined_targets]
    sys.argv += [path] if path else joined_targets

    # plugin is discovered automatically in 3, but not in 2
    # to prevent "plugin already registered" problem we check it first
    plugins_to_load = []
    if not get_plugin_manager().hasplugin("pytest-teamcity"):
        plugins_to_load.append(pytest_plugin)

    args = sys.argv[1:]
    if JB_DISABLE_BUFFERING and "-s" not in args:
        args += ["-s"]
    jb_doc_args("py.test", args)
    pytest.main(args, plugins_to_load)
