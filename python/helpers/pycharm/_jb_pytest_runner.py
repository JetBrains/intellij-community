# coding=utf-8

#  Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

import pytest
from distutils import version
import sys
from _pytest.config import get_plugin_manager
from pkg_resources import iter_entry_points

from _jb_runner_tools import jb_patch_separator, jb_doc_args, JB_DISABLE_BUFFERING, start_protocol, parse_arguments, \
  set_parallel_mode
from teamcity import pytest_plugin

if __name__ == '__main__':
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
    if "--jb-show-summary" in args:
        args.remove("--jb-show-summary")
    elif version.LooseVersion(pytest.__version__) >= version.LooseVersion("6.0"):
        args += ["--no-header", "--no-summary", "-q"]

    if JB_DISABLE_BUFFERING and "-s" not in args:
      args += ["-s"]


    jb_doc_args("pytest", args)


    class Plugin:
        @staticmethod
        def pytest_configure(config):
            if getattr(config.option, "numprocesses", None):
                set_parallel_mode()
            start_protocol()


    sys.exit(pytest.main(args, plugins_to_load + [Plugin]))
