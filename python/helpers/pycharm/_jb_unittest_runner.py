import errno
import os
import sys
from unittest import main as unittest_main

from _jb_runner_tools import JB_DISABLE_BUFFERING
from _jb_runner_tools import JB_VERBOSE
from _jb_runner_tools import PROJECT_DIR
from _jb_runner_tools import jb_doc_args
from _jb_runner_tools import jb_finish_tests
from _jb_runner_tools import jb_start_tests
from teamcity import unittestpy


def build_unittest_args(
    path,
    targets,
    additional_args,
    project_dir=PROJECT_DIR,
    verbose=JB_VERBOSE,
):
    args = ["python -m unittest"]
    subcommand_args = []

    if path:
        if not os.path.exists(path):
            raise OSError(errno.ENOENT, "No such file or directory", path)

        if sys.version_info >= (3, 0) and os.path.isfile(path):
            # in Py3 it is possible to run script directly
            # which is much more stable than discovery machinery
            # for example it supports hyphens in file names PY-23549
            subcommand_args = [path]
        else:
            subcommand_args = ["discover", "-s"]

            # unittest in py2 does not support running script directly
            # (and folders in py2 and py3),
            # but it can use "discover" to find all tests in some folder
            # (optionally filtering by script)
            if os.path.isfile(path):
                subcommand_args += [
                    os.path.dirname(path),
                    "-p",
                    os.path.basename(path)
                ]
            else:
                subcommand_args.append(path)

            # to force unittest to calculate the path relative to this folder
            subcommand_args += ["-t", project_dir]
    elif targets:
        subcommand_args = list(targets)

    args += subcommand_args

    if verbose:
        args.append("--verbose")
    else:
        args.append("--quiet")

    args += additional_args
    return args


def main():
    path, targets, additional_args = jb_start_tests()
    args = build_unittest_args(path, targets, additional_args)
    jb_doc_args("unittests", args)

    # working dir should be on path
    # that is how unittest work when launched from command line
    sys.path.insert(0, PROJECT_DIR)

    try:
        sys.exit(unittest_main(
            argv=args,
            module=None,
            testRunner=unittestpy.TeamcityTestRunner,
            buffer=not JB_DISABLE_BUFFERING
        ))
    finally:
        jb_finish_tests()


if __name__ == "__main__":
    main()
