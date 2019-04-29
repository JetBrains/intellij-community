# coding=utf-8
"""
Tools to implement runners (https://confluence.jetbrains.com/display/~link/PyCharm+test+runners+protocol)
"""
import os
import re
import sys

import _jb_utils
from teamcity import teamcity_presence_env_var, messages

# Some runners need it to "detect" TC and start protocol
if teamcity_presence_env_var not in os.environ:
    os.environ[teamcity_presence_env_var] = "LOCAL"

# Providing this env variable disables output buffering.
# anything sent to stdout/stderr goes to IDE directly, not after test is over like it is done by default.
# out and err are not in sync, so output may go to wrong test
JB_DISABLE_BUFFERING = "JB_DISABLE_BUFFERING" in os.environ
# getcwd resolves symlinks, but PWD is not supported by some shells
PROJECT_DIR = os.getenv('PWD', os.getcwd())


def _parse_parametrized(part):
    """

    Support nose generators / pytest parameters and other functions that provides names like foo(1,2)
    Until https://github.com/JetBrains/teamcity-messages/issues/121, all such tests are provided
    with parentheses.
    
    Tests with docstring are reported in similar way but they have space before parenthesis and should be ignored
    by this function
    
    """
    match = re.match("^([^\\s)(]+)(\\(.+\\))$", part)
    if not match:
        return [part]
    else:
        return [match.group(1), match.group(2)]


class _TreeManagerHolder(object):
    def __init__(self):
        self.parallel = "JB_USE_PARALLEL_TREE_MANAGER" in os.environ
        self._manager_imp = None

    @property
    def manager(self):
        if not self._manager_imp:
            self._fill_manager()
        return self._manager_imp

    def _fill_manager(self):
        if self.parallel:
            from _jb_parallel_tree_manager import ParallelTreeManager
            self._manager_imp = ParallelTreeManager()
        else:
            from _jb_serial_tree_manager import SerialTreeManager
            self._manager_imp = SerialTreeManager()


_TREE_MANAGER_HOLDER = _TreeManagerHolder()


def set_parallel_mode():
    _TREE_MANAGER_HOLDER.parallel = True


def is_parallel_mode():
    return _TREE_MANAGER_HOLDER.parallel


# Monkeypatching TC
_old_service_messages = messages.TeamcityServiceMessages

PARSE_FUNC = None


class NewTeamcityServiceMessages(_old_service_messages):
    _latest_subtest_result = None

    def message(self, messageName, **properties):
        if messageName in {"enteredTheMatrix", "testCount"}:
            _old_service_messages.message(self, messageName, **properties)
            return

        try:
            # Report directory so Java site knows which folder to resolve names against

            # tests with docstrings are reported in format "test.name (some test here)".
            # text should be part of name, but not location.
            possible_location = str(properties["name"])
            loc = possible_location.find("(")
            if loc > 0:
                possible_location = possible_location[:loc].strip()
            properties["locationHint"] = "python<{0}>://{1}".format(PROJECT_DIR, possible_location)
        except KeyError:
            # If message does not have name, then it is not test
            # Simply pass it
            _old_service_messages.message(self, messageName, **properties)
            return

        current, parent = _TREE_MANAGER_HOLDER.manager.get_node_ids(properties["name"])
        if not current and not parent:
            return
        # Shortcut for name
        try:
            properties["name"] = str(properties["name"]).split(".")[-1]
        except IndexError:
            pass

        properties["nodeId"] = str(current)
        properties["parentNodeId"] = str(parent)

        _old_service_messages.message(self, messageName, **properties)

    def _test_to_list(self, test_name):
        """
        Splits test name to parts to use it as list.
        It most cases dot is used, but runner may provide custom function
        """
        parts = test_name.split(".")
        result = []
        for part in parts:
            result += _parse_parametrized(part)
        return result

    def _fix_setup_teardown_name(self, test_name):
        """

        Hack to rename setup and teardown methods to much real python signatures
        """
        try:
            return {"test setup": "setUpClass", "test teardown": "tearDownClass"}[test_name]
        except KeyError:
            return test_name

    # Blocks are used for 2 cases now:
    # 1) Unittest subtests (only closed, opened by subTestBlockOpened)
    # 2) setup/teardown (does not work, see https://github.com/JetBrains/teamcity-messages/issues/114)
    # def blockOpened(self, name, flowId=None):
    #      self.testStarted(".".join(TREE_MANAGER.current_branch + [self._fix_setup_teardown_name(name)]))

    def blockClosed(self, name, flowId=None):

        # If _latest_subtest_result is not set or does not exist we closing setup method, not a subtest
        try:
            if not self._latest_subtest_result:
                return
        except AttributeError:
            return

        # closing subtest
        test_name = ".".join(_TREE_MANAGER_HOLDER.manager.current_branch)
        if self._latest_subtest_result in {"Failure", "Error"}:
            self.testFailed(test_name)
        if self._latest_subtest_result == "Skip":
            self.testIgnored(test_name)

        self.testFinished(test_name)
        self._latest_subtest_result = None

    def subTestBlockOpened(self, name, subTestResult, flowId=None):
        self.testStarted(".".join(_TREE_MANAGER_HOLDER.manager.current_branch + [name]))
        self._latest_subtest_result = subTestResult

    def testStarted(self, testName, captureStandardOutput=None, flowId=None, is_suite=False, metainfo=None):
        test_name_as_list = self._test_to_list(testName)
        testName = ".".join(test_name_as_list)

        def _write_start_message():
            # testName, captureStandardOutput, flowId
            args = {"name": testName, "captureStandardOutput": captureStandardOutput, "metainfo": metainfo}
            if is_suite:
                self.message("testSuiteStarted", **args)
            else:
                self.message("testStarted", **args)

        commands = _TREE_MANAGER_HOLDER.manager.level_opened(self._test_to_list(testName), _write_start_message)
        if commands:
            self.do_commands(commands)
            self.testStarted(testName, captureStandardOutput, metainfo=metainfo)

    def testFailed(self, testName, message='', details='', flowId=None, comparison_failure=None):
        testName = ".".join(self._test_to_list(testName))
        _old_service_messages.testFailed(self, testName, message, details, comparison_failure=comparison_failure)

    def testFinished(self, testName, testDuration=None, flowId=None, is_suite=False):
        testName = ".".join(self._test_to_list(testName))

        def _write_finished_message():
            # testName, captureStandardOutput, flowId
            current, parent = _TREE_MANAGER_HOLDER.manager.get_node_ids(testName)
            if not current and not parent:
                return
            args = {"nodeId": current, "parentNodeId": parent, "name": testName}

            # TODO: Doc copy/paste with parent, extract
            if testDuration is not None:
                duration_ms = testDuration.days * 86400000 + \
                              testDuration.seconds * 1000 + \
                              int(testDuration.microseconds / 1000)
                args["duration"] = str(duration_ms)

            if is_suite:
                if is_parallel_mode():
                    del args["duration"]
                self.message("testSuiteFinished", **args)
            else:
                self.message("testFinished", **args)

        commands = _TREE_MANAGER_HOLDER.manager.level_closed(self._test_to_list(testName), _write_finished_message)
        if commands:
            self.do_commands(commands)
            self.testFinished(testName, testDuration)

    def do_commands(self, commands):
        """

        Executes commands, returned by level_closed and level_opened
        """
        for command, test in commands:
            test_name = ".".join(test)
            # By executing commands we open or close suites(branches) since tests(leaves) are always reported by runner
            if command == "open":
                self.testStarted(test_name, is_suite=True)
            else:
                self.testFinished(test_name, is_suite=True)


messages.TeamcityServiceMessages = NewTeamcityServiceMessages


# Monkeypatched

def jb_patch_separator(targets, fs_glue, python_glue, fs_to_python_glue):
    """
    Converts python target if format "/path/foo.py::parts.to.python" provided by Java to 
    python specific format

    :param targets: list of dot-separated targets
    :param fs_glue: how to glue fs parts of target. I.e.: module "eggs" in "spam" package is "spam[fs_glue]eggs"
    :param python_glue: how to glue python parts (glue between class and function etc)
    :param fs_to_python_glue: between last fs-part and first python part
    :return: list of targets with patched separators
    """
    if not targets:
        return []

    def _patch_target(target):
        # /path/foo.py::parts.to.python
        match = re.match("^(:?(.+)[.]py::)?(.+)$", target)
        assert match, "unexpected string: {0}".format(target)
        fs_part = match.group(2)
        python_part = match.group(3).replace(".", python_glue)
        if fs_part:
            return fs_part.replace("/", fs_glue) + fs_to_python_glue + python_part
        else:
            return python_part

    return map(_patch_target, targets)


def jb_start_tests():
    """
    Parses arguments, starts protocol and fixes syspath and returns tuple of arguments
    """
    path, targets, additional_args = parse_arguments()
    start_protocol()
    return path, targets, additional_args


def start_protocol():
    properties = {"durationStrategy": "manual"} if is_parallel_mode() else dict()
    NewTeamcityServiceMessages().message('enteredTheMatrix', **properties)


def parse_arguments():
    """
    Parses arguments, fixes syspath and returns tuple of arguments

    :return: (string with path or None, list of targets or None, list of additional arguments)
    It may return list with only one element (name itself) if name is the same or split names to several parts
    """
    # Handle additional args after --
    additional_args = []
    try:
        index = sys.argv.index("--")
        additional_args = sys.argv[index + 1:]
        del sys.argv[index:]
    except ValueError:
        pass
    utils = _jb_utils.VersionAgnosticUtils()
    namespace = utils.get_options(
        _jb_utils.OptionDescription('--path', 'Path to file or folder to run'),
        _jb_utils.OptionDescription('--target', 'Python target to run', "append"))
    del sys.argv[1:]  # Remove all args

    # PyCharm helpers dir is first dir in sys.path because helper is launched.
    # But sys.path should be same as when launched with test runner directly
    try:
        if os.path.abspath(sys.path[0]) == os.path.abspath(os.environ["PYCHARM_HELPERS_DIR"]):
            sys.path.pop(0)
    except KeyError:
        pass
    return namespace.path, namespace.target, additional_args


def jb_doc_args(framework_name, args):
    """
    Runner encouraged to report its arguments to user with aid of this function

    """
    print("Launching {0} with arguments {1} in {2}\n".format(framework_name, " ".join(args), PROJECT_DIR))
