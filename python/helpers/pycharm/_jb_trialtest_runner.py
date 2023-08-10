# coding=utf-8
import os
from pprint import pprint

from _jb_runner_tools import jb_start_tests, jb_doc_args, PROJECT_DIR, jb_finish_tests
from twisted.scripts import trial
import sys


if __name__ == '__main__':
    # This folder should be in sys.path because teamcity twisted plugin is there
    sys.path.append(os.path.join(os.path.dirname(__file__), "__jb.for_twisted"))

    path, targets, additional_args = jb_start_tests()

    sys.path.append(PROJECT_DIR)  # Current dir must be in sys.path according to trial docs

    sys.argv.append("--reporter=teamcity")
    sys.argv += additional_args

    if path:
        assert os.path.exists(path), path + " does not exist"
        # assert os.path.isfile(path), path + " is folder. Provide its name as python target (dot separated)"
        sys.argv.append(os.path.normpath(path))
    elif targets:
        sys.argv += targets

    jb_doc_args("trial", sys.argv[1:])
    try:
        trial.run()
    finally:
        jb_finish_tests()
