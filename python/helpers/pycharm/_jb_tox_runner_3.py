# coding=utf-8
"""
Runs tox from current directory for tox 3

"""
import os
import pluggy
from tox import config as tox_config
from tox.session import Session

from tcmessages import TeamcityServiceMessages
from tox import exception

teamcity = TeamcityServiceMessages()

hookimpl = pluggy.HookimplMarker("tox")
helpers_dir = str(os.path.split(__file__)[0])


class JbToxHook(object):
    """
    Hook to report test start and test end.
    """

    def __init__(self, config):
        self.offsets = dict()
        self.current_env = None
        self.config = config

    @hookimpl
    def tox_runtest_pre(self, venv):
        """
        Launched before each setup.
        It means prev env (if any) just finished and new is going to be created
        :param venv: current virtual env
        """
        self.current_env = venv
        name = venv.name
        node_id = self.offsets[name]
        teamcity.testStarted(name, location="tox_env://" + str(name), parentNodeId="0",
                             nodeId=node_id)

    @hookimpl
    def tox_runtest_post(self, venv):
        """
        Finishes currently running env. reporting its state
        """
        if not self.current_env:
            return
        name = venv.name
        node_id = self.offsets[name]
        status = self.current_env.status
        if isinstance(status, exception.InterpreterNotFound):
            if self.config.option.skip_missing_interpreters:
                self._reportFailure("SKIP", status, node_id)
            else:
                self._reportFailure("ERROR", status, node_id)
        elif status == "platform mismatch":
            self._reportFailure("SKIP", status, node_id)
        elif status and status == "ignored failed command":
            print("  %s: %s" % (self.current_env.name, str(status)))
        elif status and status != "skipped tests":
            self._reportFailure("ERROR", status, node_id)
        else:
            teamcity.testStdOut(self.current_env.name, "\n", nodeId=node_id)
            teamcity.testFinished(self.current_env.name, nodeId=node_id)
        self.current_env = None

    def _reportFailure(self, state, message, node_id):
        """
        In idBased mode each test is leaf, there is no suites, so we can rerport directly to the test
        :param state: SKIP or ERROR (suite result)
        """
        if state == "SKIP":
            teamcity.testIgnored(state, str(message), nodeId=node_id)
        else:
            teamcity.testFailed(state, str(message), nodeId=node_id)


class Fixer(object):
    def __init__(self, runner_name):
        self.runner_name = runner_name

    def fix(self, command, bin, offset):
        return [bin, os.path.join(helpers_dir, self.runner_name), "--offset",
                str(offset), "--"]

    def is_parallel(self, *args, **kwargs):
        return False


class _Unit2(Fixer):
    def __init__(self):
        super(_Unit2, self).__init__("_jb_unittest_runner.py")

    def fix(self, command, bin, offset):
        if command[0] == "unit2":
            return [bin, os.path.join(helpers_dir, "utrunner.py")] + command[1:] + [
                "true"]
        elif command == ["python", "-m", "unittest", "discover"]:
            return super(_Unit2, self).fix(command, bin, offset) + ["discover"]
        return None


class _PyTest(Fixer):
    def __init__(self):
        super(_PyTest, self).__init__("_jb_pytest_runner.py")

    def is_parallel(self,
                    config):  # If xdist is used, then pytest will use parallel run
        deps = getattr(config, "deps", [])
        return bool([d for d in deps if d.name == "pytest-xdist"])

    def fix(self, command, bin, offset):
        if command[0] not in ["pytest", "py.test"]:
            return None
        return super(_PyTest, self).fix(command, bin, offset) + command[1:]


class _Nose(Fixer):
    def __init__(self):
        super(_Nose, self).__init__("_jb_nosetest_runner.py")

    def fix(self, command, bin, offset):
        if command[0] != "nosetests":
            return None
        return super(_Nose, self).fix(command, bin, offset) + command[1:]


_RUNNERS = [_Unit2(), _PyTest(), _Nose()]

import sys

durationStrategy = "automatic"
config = tox_config.parseconfig(args=sys.argv[1:])
hook = JbToxHook(config)
config.pluginmanager.register(hook, "jbtoxplugin")
offset = 1
for env, tmp_config in config.envconfigs.items():
    hook.offsets[env] = offset
    if not tmp_config.setenv:
        tmp_config.setenv = dict()
    tmp_config.setenv["_jb_do_not_call_enter_matrix"] = "1"
    commands = tmp_config.commands

    if "_jb_do_not_patch_test_runners" not in os.environ and isinstance(commands, list):
        for fixer in _RUNNERS:
            _env = config.envconfigs[env]
            for i, command in enumerate(commands):
                if command:
                    fixed_command = fixer.fix(command, str(_env.envpython), offset)
                    if fixer.is_parallel(tmp_config):
                        durationStrategy = "manual"
                    if fixed_command:
                        commands[i] = fixed_command
    tmp_config.commands = commands
    offset += 10000


def run_tox_3():
    session = Session(config)
    teamcity.testMatrixEntered(durationStrategy=durationStrategy)
    return session.runcommand()
