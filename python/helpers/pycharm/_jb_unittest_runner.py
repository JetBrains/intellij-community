# coding=utf-8
import os
from unittest import main

from _jb_runner_tools import jb_start_tests, jb_doc_args
from teamcity import unittestpy

if __name__ == '__main__':
    path, targets, additional_args = jb_start_tests()

    args = ["python -m unittest"]
    if path:
        discovery_args = ["discover", "-s"]
        # Unittest does not support script directly, but it can use "discover" to find all tests in some folder
        # filtering by script
        assert os.path.exists(path), "{0}: No such file or directory".format(path)
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
    test_runner = unittestpy.TeamcityTestRunner()
    test_runner.buffer = True
    main(argv=args, module=None, testRunner=test_runner)
