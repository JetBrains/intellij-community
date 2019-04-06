# coding=utf-8
"""
Runs tox from current directory.
It supports any runner, but well-known runners (py.test and unittest) are switched to our internal runners to provide
better support
"""
import os
import pluggy
from tox import config as tox_config, session as tox_session
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
        teamcity.testSuiteStarted(venv.name, location="tox_env://" + str(venv.name))

    @hookimpl
    def tox_runtest_post(self, venv):
        """
        Finishes currently running env. reporting its state
        """
        if not self.current_env:
            return

        status = self.current_env.status
        if isinstance(status, exception.InterpreterNotFound):
            if self.config.option.skip_missing_interpreters:
                self._reportSuiteStateLeaf("SKIP", status)
            else:
                self._reportSuiteStateLeaf("ERROR", status)
        elif status == "platform mismatch":
            self._reportSuiteStateLeaf("SKIP", status)
        elif status and status == "ignored failed command":
            print("  %s: %s" % (self.current_env.name, str(status)))
        elif status and status != "skipped tests":
            self._reportSuiteStateLeaf("ERROR", status)
        teamcity.testStdOut(self.current_env.name, "\n")
        teamcity.testSuiteFinished(self.current_env.name)
        self.current_env = None

    def _reportSuiteStateLeaf(self, state, message):
        """
        Since platform does not support empty suite, we need to output something.
        :param state: SKIP or ERROR (suite result)
        """
        teamcity.testStarted(state, "tox_env://" + str(self.current_env.name))
        if state == "SKIP":
            teamcity.testIgnored(state, str(message))
        else:
            teamcity.testFailed(state, str(message))


class _Unit2(object):
    def fix(self, command, bin):
        if command[0] == "unit2":
            return [bin, os.path.join(helpers_dir, "utrunner.py")] + command[1:] + ["true"]
        elif command == ["python", "-m", "unittest", "discover"]:
            return [bin, os.path.join(helpers_dir, "utrunner.py"), "true"]
        return None


class _PyTest(object):
    def fix(self, command, bin):
        if command[0] not in ["pytest", "py.test"]:
            return None
        return [bin, os.path.join(helpers_dir, "pytestrunner.py"), "-p", "pytest_teamcity"] + command[1:]


class _Nose(object):
    def fix(self, command, bin):
        if command[0] != "nosetests":
            return None
        return [bin, os.path.join(helpers_dir, "noserunner.py")] + command[1:]


_RUNNERS = [_Unit2(), _PyTest(), _Nose()]

import sys

config = tox_config.parseconfig(args=sys.argv[1:])
config.pluginmanager.register(JbToxHook(config), "jbtoxplugin")
for env, tmp_config in config.envconfigs.items():
    if not tmp_config.setenv:
        tmp_config.setenv = dict()
    tmp_config.setenv["_jb_do_not_call_enter_matrix"] = "1"
    commands = tmp_config.commands
    if not isinstance(commands, list) or not len(commands):
        continue
    for fixer in _RUNNERS:
        _env = config.envconfigs[env]
        for i, command in enumerate(commands):
            if command:
                fixed_command = fixer.fix(command, str(_env.envpython))
                if fixed_command:
                    commands[i] = fixed_command
    tmp_config.commands = commands

session = Session(config)
teamcity.testMatrixEntered()
session.runcommand()
