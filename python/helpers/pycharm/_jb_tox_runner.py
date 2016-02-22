# coding=utf-8
"""
Runs tox from current directory.
It supports any runner, but well-known runners (py.test and unittest) are switched to our internal runners to provide
better support
"""
import os
import sys

from tox import config as tox_config, session as tox_session
from tox.session import Reporter

from tcmessages import TeamcityServiceMessages

helpers_dir = str(os.path.split(__file__)[0])


class _Unit2(object):
    def fix(self, command, dir_to_run, bin):
        if command[0] == "unit2":
            return [bin, os.path.join(helpers_dir, "utrunner.py"), dir_to_run] + command[1:] + ["true"]
        elif command == ["python", "-m", "unittest", "discover"]:
            return [bin, os.path.join(helpers_dir, "utrunner.py"), dir_to_run, "true"]
        return None


class _PyTest(object):
    def fix(self, command, dir_to_run, bin):
        if command[0] != "py.test":
            return None
        normal_path = os.path.normpath(dir_to_run) + os.sep
        return [bin, os.path.join(helpers_dir, "pytestrunner.py"), "-p", "pytest_teamcity", normal_path] + command[1:]


class _Nose(object):
    def fix(self, command, dir_to_run, bin):
        if command[0] != "nosetests":
            return None
        return [bin, os.path.join(helpers_dir, "noserunner.py"), dir_to_run] + command[1:]



_RUNNERS = [_Unit2(), _PyTest(), _Nose()]

teamcity = TeamcityServiceMessages()


class _Reporter(Reporter):
    def logaction_start(self, action):
        super(_Reporter, self).logaction_start(action)
        if action.activity == "getenv":
            teamcity.output.write("\n")
            teamcity.testSuiteStarted(action.id, location="tox_env://" + str(action.id))
            self.current_suite = action.id

    def logaction_finish(self, action):
        super(_Reporter, self).logaction_finish(action)
        if action.activity == "runtests":
            teamcity.testSuiteFinished(action.id)
            teamcity.output.write("\n")

    def error(self, msg):
        super(_Reporter, self).error(msg)
        name = teamcity.current_test_name()
        if name:
            if name != teamcity.topmost_suite:
                teamcity.testFailed(name, msg)
            else:
                teamcity.testFailed("ERROR", msg)
                teamcity.testSuiteFinished(name)
        else:
            sys.stderr.write(msg)

    def skip(self, msg):
        super(_Reporter, self).skip(msg)
        name = teamcity.current_test_name()
        if name:
            teamcity.testFinished(name)


config = tox_config.parseconfig()
for env, tmp_config in config.envconfigs.items():
    if not tmp_config.setenv:
        tmp_config.setenv = dict()
    tmp_config.setenv["_jb_do_not_call_enter_matrix"] = "1"
    commands = tmp_config.commands
    if not isinstance(commands, list) or not len(commands):
        continue
    for fixer in _RUNNERS:
        _env = config.envconfigs[env]
        dir_to_run = str(_env.changedir)
        for i, command in enumerate(commands):
            fixed_command = fixer.fix(command, dir_to_run, str(_env.envpython))
            if fixed_command:
                commands[i] = fixed_command
    tmp_config.commands = commands

session = tox_session.Session(config, Report=_Reporter)
teamcity.testMatrixEntered()
session.runcommand()
