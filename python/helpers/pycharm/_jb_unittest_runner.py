import datetime
import errno
import os
import runpy
import sys
import unittest
import unittest.runner
from unittest import main as unittest_main

from _jb_runner_tools import JB_DISABLE_BUFFERING
from _jb_runner_tools import JB_VERBOSE
from _jb_runner_tools import PROJECT_DIR
from _jb_runner_tools import jb_doc_args
from _jb_runner_tools import jb_finish_tests
from _jb_runner_tools import jb_start_tests
from _jb_runner_tools import set_parallel_mode
from teamcity import unittestpy

# Script that the IDE runs instead of "unittest" to discover and run the tests
JB_RUNNER_SCRIPT = os.getenv("JB_UNITTEST_RUNNER_SCRIPT")

# per `unittest` API
QUIET = 0
VERBOSE = 2


def build_unittest_args(
    path,
    targets,
    additional_args,
    project_dir=PROJECT_DIR,
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
    args += additional_args
    return args


def build_runner_script_args(
    script,
    path,
    targets,
    additional_args,
    working_dir=None,
):
    """
    Builds sys.argv for the runner script.
    The script gets the tests to run as labels.
    A label is a dotted name relative to the working dir, as "manage.py test" and "runtests.py" expect.
    The working dir is the current dir by default.
    PWD is not used, because the debugger can give a PWD of another process.
    """
    labels = []
    if path:
        if not os.path.exists(path):
            raise OSError(errno.ENOENT, "No such file or directory", path)
        label = _path_to_label(path, working_dir or os.getcwd())
        if label:
            labels.append(label)
    elif targets:
        labels = list(targets)
    return [script] + labels + additional_args


def _path_to_label(path, working_dir):
    absolute_path = os.path.abspath(path)
    # Real paths, because the IDE and the shell can give different names for a symlink
    real_path = os.path.realpath(path)
    try:
        relative_path = os.path.relpath(real_path, os.path.realpath(working_dir))
    except ValueError:
        # On Windows, the path and the working dir can be on different drives
        return absolute_path
    if relative_path.split(os.sep)[0] == os.pardir:
        return absolute_path
    if os.path.isfile(real_path):
        relative_path = os.path.splitext(relative_path)[0]
        if os.path.basename(relative_path) == "__init__":
            relative_path = os.path.dirname(relative_path) or os.curdir
    if relative_path == os.curdir:
        # No labels: the script runs all the tests
        return None
    return relative_path.replace(os.sep, ".")


class _RunnerScriptTestResult(unittestpy.TeamcityTestResult):
    def __init__(self, *args, **kwargs):
        super(_RunnerScriptTestResult, self).__init__(*args, **kwargs)
        # The IDE shows the progress, so the result does not print it
        self.dots = False
        self.showAll = False

    def addDuration(self, test, elapsed):
        # A parallel runner replays all events of a test at one time, so the start time is wrong.
        # unittest calls addDuration before stopTest, which reports the duration.
        test_id = self.get_test_id_with_description(test)
        self.test_started_datetime_map[test_id] = datetime.datetime.now() - datetime.timedelta(seconds=elapsed)
        add_duration = getattr(super(_RunnerScriptTestResult, self), "addDuration", None)
        if add_duration:
            add_duration(test, elapsed)


class _RunnerScriptTestRunner(unittestpy.TeamcityTestRunner):
    resultclass = _RunnerScriptTestResult

    def __init__(self, *args, **kwargs):
        super(_RunnerScriptTestRunner, self).__init__(*args, **kwargs)
        # The script can give its own result class, for example Django with "--debug-sql" or "--pdb".
        # Add the reporting to it.
        if not issubclass(self.resultclass, _RunnerScriptTestResult):
            self.resultclass = type("RunnerScriptTestResult", (_RunnerScriptTestResult, self.resultclass), {})


def run_runner_script(argv):
    """
    Runs the script as "python script.py" does.
    The script must run the tests with unittest.TextTestRunner.
    This function replaces it with a runner that reports the results to the IDE.
    """
    unittest.TextTestRunner = _RunnerScriptTestRunner
    unittest.runner.TextTestRunner = _RunnerScriptTestRunner
    script = argv[0]
    sys.argv = argv
    sys.path.insert(0, os.path.dirname(os.path.abspath(script)))
    runpy.run_path(script, run_name="__main__")


def main():
    if JB_RUNNER_SCRIPT:
        # The script can run the tests in any order, for example in parallel processes
        set_parallel_mode()
    path, targets, additional_args = jb_start_tests()
    if JB_RUNNER_SCRIPT:
        args = build_runner_script_args(JB_RUNNER_SCRIPT, path, targets, additional_args)
        jb_doc_args("unittests", args)
        try:
            run_runner_script(args)
        finally:
            jb_finish_tests()
        return

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
            buffer=not JB_DISABLE_BUFFERING,
            verbosity=VERBOSE if JB_VERBOSE else QUIET,
        ))
    finally:
        jb_finish_tests()


if __name__ == "__main__":
    main()
