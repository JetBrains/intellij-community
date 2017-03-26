# coding=utf-8
"""
Tools to implement runners (https://confluence.jetbrains.com/display/~link/PyCharm+test+runners+protocol)
"""
import atexit
import _jb_utils
import os
import re
import sys

from teamcity import teamcity_presence_env_var, messages

# Some runners need it to "detect" TC and start protocol
if teamcity_presence_env_var not in os.environ:
    os.environ[teamcity_presence_env_var] = "LOCAL"


def _parse_parametrized(part):
    """

    Support nose generators / py.test parameters and other functions that provides names like foo(1,2)
    Until https://github.com/JetBrains/teamcity-messages/issues/121, all such tests are provided
    with parentheses
    """
    match = re.match("^(.+)(\\(.+\\))$", part)
    if not match:
        return [part]
    else:
        return [match.group(1), match.group(2)]


# Monkeypatching TC to pass location hint

class _TreeManager(object):
    """
    Manages output tree by building it from flat test names.
    """

    def __init__(self):
        super(_TreeManager, self).__init__()
        # Currently active branch as list. New nodes go to this branch
        self.current_branch = []
        # node unique name to its nodeId
        self._node_ids_dict = {}
        # Node id mast be incremented for each new branch
        self._max_node_id = 0

    def _calculate_relation(self, branch_as_list):
        """
        Get relation of branch_as_list to current branch.
        :return: tuple. First argument could be: "same", "child", "parent" or "sibling"(need to start new tree)
        Second argument is relative path from current branch to child if argument is child
        """
        if branch_as_list == self.current_branch:
            return "same", None

        hierarchy_name_len = len(branch_as_list)
        current_branch_len = len(self.current_branch)

        if hierarchy_name_len > current_branch_len and branch_as_list[0:current_branch_len] == self.current_branch:
            return "child", branch_as_list[current_branch_len:]

        if hierarchy_name_len < current_branch_len and self.current_branch[0:hierarchy_name_len] == branch_as_list:
            return "parent", None

        return "sibling", None

    def _add_new_node(self, new_node_name):
        """
        Adds new node to branch
        """
        self.current_branch.append(new_node_name)
        self._max_node_id += 1
        self._node_ids_dict[".".join(self.current_branch)] = self._max_node_id

    def level_opened(self, test_as_list, func_to_open):
        """
        To be called on test start.

        :param test_as_list: test name splitted as list
        :param func_to_open: func to be called if test can open new level
        :return: None if new level opened, or tuple of command client should execute and try opening level again
         Command is "open" (open provided level) or "close" (close it). Second item is test name as list
        """
        relation, relative_path = self._calculate_relation(test_as_list)
        if relation == 'same':
            return  # Opening same level?
        if relation == 'child':
            # If one level -- open new level gracefully
            if len(relative_path) == 1:
                self._add_new_node(relative_path[0])
                func_to_open()
                return None
            else:
                # Open previous level
                return "open", self.current_branch + relative_path[0:1]
        if relation == "sibling":
            if self.current_branch:
                # Different tree, close whole branch
                return "close", self.current_branch
            else:
                return None
        if relation == 'parent':
            # Opening parent? Insane
            pass

    def level_closed(self, test_as_list, func_to_close):
        """
        To be called on test end or failure.

        See level_opened doc.
        """
        relation, relative_path = self._calculate_relation(test_as_list)
        if relation == 'same':
            # Closing current level
            func_to_close()
            self.current_branch.pop()
        if relation == 'child':
            return None

        if relation == 'sibling':
            pass
        if relation == 'parent':
            return "close", self.current_branch

    @property
    def parent_branch(self):
        return self.current_branch[:-1] if self.current_branch else None

    def _get_node_id(self, branch):
        return self._node_ids_dict[".".join(branch)]

    @property
    def node_ids(self):
        """

        :return: (current_node_id, parent_node_id)
        """
        current = self._get_node_id(self.current_branch)
        parent = self._get_node_id(self.parent_branch) if self.parent_branch else "0"
        return str(current), str(parent)

    def close_all(self):
        if not self.current_branch:
            return None
        return "close", self.current_branch


TREE_MANAGER = _TreeManager()

_old_service_messages = messages.TeamcityServiceMessages

PARSE_FUNC = None


class NewTeamcityServiceMessages(_old_service_messages):
    def message(self, messageName, **properties):
        # Intellij may fail to process message if it has char just before it.
        # Space before message has no visible affect, but saves from such cases
        print(" ")
        if messageName in set(["enteredTheMatrix", "testCount"]):
            _old_service_messages.message(self, messageName, **properties)
            return

        try:
            # Report directory so Java site knows which folder to resolve names against
            properties["locationHint"] = "python<{0}>://{1}".format(os.getcwd(), properties["name"])
        except KeyError:
            # If message does not have name, then it is not test
            # Simply pass it
            _old_service_messages.message(self, messageName, **properties)
            return

        # Shortcut for name
        try:
            properties["name"] = str(properties["name"]).split(".")[-1]
        except IndexError:
            pass

        current, parent = TREE_MANAGER.node_ids
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
    # 1) Unittest subtests (broken, because failure can't be reported)
    # 2) setup/teardown (does not work, see https://github.com/JetBrains/teamcity-messages/issues/114)
    # So, temporary disabled
    # def blockOpened(self, name, flowId=None):
    #     self.testStarted(".".join(TREE_MANAGER.current_branch + [self._fix_setup_teardown_name(name)]))
    #
    # def blockClosed(self, name, flowId=None):
    #     self.testFinished(".".join(TREE_MANAGER.current_branch + [self._fix_setup_teardown_name(name)]))

    def testStarted(self, testName, captureStandardOutput=None, flowId=None, is_suite=False):
        test_name_as_list = self._test_to_list(testName)
        testName = ".".join(test_name_as_list)

        def _write_start_message():
            # testName, captureStandardOutput, flowId
            args = {"name": testName, "captureStandardOutput": captureStandardOutput}
            if is_suite:
                self.message("testSuiteStarted", **args)
            else:
                self.message("testStarted", **args)

        commands = TREE_MANAGER.level_opened(self._test_to_list(testName), _write_start_message)
        if commands:
            self.do_command(commands[0], commands[1])
            self.testStarted(testName, captureStandardOutput)

    def testFailed(self, testName, message='', details='', flowId=None):
        testName = ".".join(self._test_to_list(testName))
        args = {"name": testName, "message": str(message),
                "details": details}
        self.message("testFailed", **args)

    def testFinished(self, testName, testDuration=None, flowId=None, is_suite=False):
        testName = ".".join(self._test_to_list(testName))

        def _write_finished_message():
            # testName, captureStandardOutput, flowId
            current, parent = TREE_MANAGER.node_ids
            args = {"nodeId": current, "parentNodeId": parent, "name": testName}

            # TODO: Doc copy/paste with parent, extract
            if testDuration is not None:
                duration_ms = testDuration.days * 86400000 + \
                              testDuration.seconds * 1000 + \
                              int(testDuration.microseconds / 1000)
                args["duration"] = str(duration_ms)

            if is_suite:
                self.message("testSuiteFinished", **args)
            else:
                self.message("testFinished", **args)

        commands = TREE_MANAGER.level_closed(self._test_to_list(testName), _write_finished_message)
        if commands:
            self.do_command(commands[0], commands[1])
            self.testFinished(testName, testDuration)

    def do_command(self, command, test):
        """

        Executes commands, returned by level_closed and level_opened
        """
        test_name = ".".join(test)
        # By executing commands we open or close suites(branches) since tests(leaves) are always reported by runner
        if command == "open":
            self.testStarted(test_name, is_suite=True)
        else:
            self.testFinished(test_name, is_suite=True)

    def close_all(self):
        """

        Closes all tests
        """
        commands = TREE_MANAGER.close_all()
        if commands:
            self.do_command(commands[0], commands[1])
            self.close_all()


messages.TeamcityServiceMessages = NewTeamcityServiceMessages


# Monkeypatched


class _SymbolNameSplitter(object):
    """
    Strategy to split symbol name to package/module part and symbols part.
        
    """
    def check_is_importable(self, parts, current_step, separator):
        """
        
        Run this method for each name part. Method throws ImportError when name is not importable. 
        That means previous name is where module name ends.
        :param parts: list of module name parts
        :param current_step: from 0 to len(parts)
        :param separator: module name separator (".")
        """
        raise NotImplementedError()


class _SymbolName2KSplitter(_SymbolNameSplitter):
    """
    Based on imp which works in 2, but not 3.
    It also emulates packages for folders with out of __init__.py.
    Say, you have Python path "spam.eggs" where "spam" is plain folder.
    It works for Py3, but not Py2.
    find_module for "spam" raises exception which is processed then (see "_symbol_processed") 
    """
    def __init__(self):
        super(_SymbolNameSplitter, self).__init__()
        self._path = None
        # Set to True when at least one find_module success, so we have at least one symbol
        self._symbol_processed = False

    def check_is_importable(self, parts, current_step, separator):
        import imp
        module_to_import = parts[current_step]
        (fil, self._path, desc) = imp.find_module(module_to_import, [self._path] if self._path else None)
        self._symbol_processed = True
        if desc[2] == imp.PKG_DIRECTORY:
            # Package
            self._path = imp.load_module(module_to_import, fil, self._path, desc).__path__[0]



class _SymbolName3KSplitter(_SymbolNameSplitter):
    """
    Based on importlib which works in 3, but not 2
    """
    def check_is_importable(self, parts, current_step, separator):
        import importlib
        module_to_import = separator.join(parts[:current_step + 1])
        importlib.import_module(module_to_import)


def jb_patch_separator(targets, fs_glue, python_glue, fs_to_python_glue):
    """
    Targets are always dot separated according to manual.
    However, some runners may need different separators.
    This function splits target to file/symbol parts and glues them using provided glues.

    :param targets: list of dot-separated targets
    :param fs_glue: how to glue fs parts of target. I.e.: module "eggs" in "spam" package is "spam[fs_glue]eggs"
    :param python_glue: how to glue python parts (glue between class and function etc)
    :param fs_to_python_glue: between last fs-part and first python part
    :return: list of targets with patched separators
    """
    if not targets:
        return []
    
    import importlib # python>=2.7
    
    def _patch_target(target):
        _jb_utils.VersionAgnosticUtils.is_py3k()
        splitter = _SymbolName3KSplitter() if _jb_utils.VersionAgnosticUtils.is_py3k() else _SymbolName2KSplitter()

        separator = "."
        parts = target.split(separator)
        fs_part = ''
        for i in range(0, len(parts)):
            try:
                # TODO: splitter may be return path to module
                splitter.check_is_importable(parts, i, separator)

                module_to_import = separator.join(parts[:i + 1])
                m = importlib.import_module(module_to_import)
                fs_part = os.path.splitext(m.__file__)[0]
            except ImportError:
                python_path = python_glue.join(parts[i:])
                return fs_part + fs_to_python_glue + python_path if python_path else fs_part
        return target

    return map(_patch_target, targets)


def jb_start_tests():
    """
    Parses arguments, starts protocol and returns tuple of arguments

    :return: (string with path or None, list of targets or None, list of additional arguments)
    :param func_to_parse function that accepts each part of test name and returns list to be used instead of it.
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
    NewTeamcityServiceMessages().message('enteredTheMatrix')

    # Working dir should be on path, that is how runners work when launched from command line
    sys.path.append(os.getcwd())
    return namespace.path, namespace.target, additional_args


def _close_all_tests():
    NewTeamcityServiceMessages().close_all()


atexit.register(_close_all_tests)


def jb_doc_args(framework_name, args):
    """
    Runner encouraged to report its arguments to user with aid of this function

    """
    print("Launching {0} with arguments {1} in {2}".format(framework_name, " ".join(args), os.getcwd()))
