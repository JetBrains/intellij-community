# coding=utf-8
import re
import sys

import pytest
from _pytest.config import _prepareconfig

from _jb_runner_tools import jb_start_tests, jb_patch_separator, jb_doc_args
from teamcity import pytest_plugin


def __parse_parametrized(part):
    """

    Support pytest parametrized tests for cases like foo[1,2]
    """
    match = re.match("^(.+)\[(.+)\]$", part)
    if not match:
        return [part]
    else:
        return [match.group(1), match.group(2)]


if __name__ == '__main__':
    path, targets, additional_args = jb_start_tests(__parse_parametrized)
    sys.argv += additional_args
    joined_targets = jb_patch_separator(targets, fs_glue="/", python_glue="::", fs_to_python_glue=".py::")
    # When file is launched in py.test it should be file.py: you can't provide it as bare module
    joined_targets = [t + ".py" if ":" not in t else t for t in joined_targets]
    sys.argv += [path] if path else joined_targets
    jb_doc_args("py.test", sys.argv[1:])

    # plugin is discovered automatically in 3, but not in 2
    # to prevent "plugin already registered" problem we check it first
    plugins_to_load = []
    if not _prepareconfig().pluginmanager.hasplugin("pytest-teamcity"):
        plugins_to_load.append(pytest_plugin)
    pytest.main(sys.argv[1:], plugins_to_load)
