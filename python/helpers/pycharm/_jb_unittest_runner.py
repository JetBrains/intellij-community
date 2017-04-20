# coding=utf-8
import os
import sys
from unittest import main

from _jb_runner_tools import jb_start_tests, jb_doc_args, JB_DISABLE_BUFFERING
from teamcity import unittestpy

if __name__ == '__main__':
    path, targets, additional_args = jb_start_tests()

    args = ["python -m unittest"]
    if path:
        assert os.path.exists(path), "{0}: No such file or directory".format(path)
        if sys.version_info > (3, 0) and os.path.isfile(path):
            # In Py3 it is possible to run script directly which is much more stable than discovery machinery
            # For example it supports hyphens in file names PY-23549
            additional_args = [path] + additional_args
        else:
            discovery_args = ["discover", "-s"]
            # Unittest in py2 does not support running script directly (and folders in py2 and py3),
            # but it can use "discover" to find all tests in some folder (optionally filtering by script)
            if os.path.isfile(path):
                discovery_args += [os.path.dirname(path), "-p", os.path.basename(path)]
            else:
                discovery_args.append(path)
            discovery_args += ["-t", os.getcwd()]  # To force unit calculate path relative to this folder
            additional_args = discovery_args + additional_args
    elif targets:
        additional_args += targets
    args += additional_args
    jb_doc_args("unittests", args)
    # Working dir should be on path, that is how unittest work when launched from command line
    sys.path.append(os.getcwd())
    main(argv=args, module=None, testRunner=unittestpy.TeamcityTestRunner, buffer=not JB_DISABLE_BUFFERING)
