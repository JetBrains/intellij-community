# coding=utf-8
import re

import nose
import sys

from _jb_runner_tools import jb_start_tests, jb_patch_separator, jb_doc_args, JB_DISABLE_BUFFERING
from teamcity.nose_report import TeamcityReport

if __name__ == '__main__':
    path, targets, additional_args = jb_start_tests()
    sys.argv += [path] if path else jb_patch_separator(targets, fs_glue="/", python_glue=".", fs_to_python_glue=".py:")
    sys.argv += additional_args
    if JB_DISABLE_BUFFERING and "-s" not in sys.argv:
        sys.argv += ["-s"]
    jb_doc_args("Nosetest", sys.argv)
    nose.main(addplugins=[TeamcityReport()])
