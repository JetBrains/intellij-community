# coding=utf-8
import sys

import pytest
from _pytest.config import get_plugin_manager
from _pytest import config

from pkg_resources import iter_entry_points

from _jb_runner_tools import jb_patch_separator, jb_doc_args, JB_DISABLE_BUFFERING, start_protocol, parse_arguments, \
    set_parallel_mode
from teamcity import pytest_plugin

if __name__ == '__main__':
    real_prepare_config = config._prepareconfig

    path, targets, additional_args = parse_arguments()
    sys.argv += additional_args
    joined_targets = jb_patch_separator(targets, fs_glue="/", python_glue="::", fs_to_python_glue=".py::")
    # When file is launched in pytest it should be file.py: you can't provide it as bare module
    joined_targets = [t + ".py" if ":" not in t else t for t in joined_targets]
    sys.argv += [path] if path else joined_targets

    # plugin is discovered automatically in 3, but not in 2
    # to prevent "plugin already registered" problem we check it first
    plugins_to_load = []
    if not get_plugin_manager().hasplugin("pytest-teamcity"):
        if "pytest-teamcity" not in map(lambda e: e.name, iter_entry_points(group='pytest11', name=None)):
            plugins_to_load.append(pytest_plugin)

    args = sys.argv[1:]
    if JB_DISABLE_BUFFERING and "-s" not in args:
        args += ["-s"]

    jb_doc_args("pytest", args)
    # We need to preparse numprocesses because user may set it using ini file
    config_result = real_prepare_config(args, plugins_to_load)

    if getattr(config_result.option, "numprocesses", None):
        set_parallel_mode()

    config._prepareconfig = lambda _, __: config_result

    start_protocol()
    pytest.main(args, plugins_to_load)
