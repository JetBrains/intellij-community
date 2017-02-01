# coding=utf-8
import re

import nose
import sys

from _jb_runner_tools import jb_start_tests, jb_patch_separator, jb_doc_args
from teamcity.nose_report import TeamcityReport

def __parse_parametrized(part):
    """

    Support nose generators that provides names like foo(1,2)
    """
    match = re.match("^(.+)\((.+)\)$", part)
    if not match:
        return [part]
    else:
        return [match.group(1), match.group(2)]

if __name__ == '__main__':
    path, targets, additional_args = jb_start_tests(__parse_parametrized)
    sys.argv += [path] if path else jb_patch_separator(targets, fs_glue=".", python_glue=".", fs_to_python_glue=":")
    sys.argv += additional_args
    jb_doc_args("Nosetest", sys.argv)
    nose.main(addplugins=[TeamcityReport()])
