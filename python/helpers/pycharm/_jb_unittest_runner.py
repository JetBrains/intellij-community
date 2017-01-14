# coding=utf-8
import os
from unittest import main

from _jb_runner_tools import jb_start_tests, jb_doc_args
from teamcity import unittestpy

if __name__ == '__main__':
    path, targets, additional_args = jb_start_tests()

    args = ["python -m unittest"]
    if path:
        # Unittest does not support script directly, but it can use "discover" to find all tests in some folder
        # filtering by script
        additional_args.append("discover")
        additional_args.append("-s")
        assert os.path.exists(path), "{0}: No such file or directory".format(path)
        if os.path.isfile(path):
            additional_args += [os.path.dirname(path), "-p", os.path.basename(path)]
        else:
            additional_args.append(path)
    else:
        additional_args += targets
    args += additional_args
    jb_doc_args("unittests", args)
    main(argv=args, module=None, testRunner=unittestpy.TeamcityTestRunner())
