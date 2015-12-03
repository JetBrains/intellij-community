# coding=utf-8
"""
Runs tox from current directory.
It supports any runner, but well-known runners (py.test and unittest) are switched to our internal runners to provide better support
"""
import sys
from tox import config as tox_config, session as tox_session
from tox.session import Reporter

from tcmessages import TeamcityServiceMessages
import os

helpers_dir = str(os.path.split(__file__)[0])

# List of local runners to use
_RUNNERS = {"unit2": [os.path.join(helpers_dir, "utrunner.py"), os.getcwd(), "true"]}

teamcity = TeamcityServiceMessages()



class _Reporter(Reporter):
    def logaction_start(self, action):
        super(_Reporter, self).logaction_start(action)
        if action.activity == "getenv":
            teamcity.testSuiteStarted(action.id)
            self.current_suite = action.id

    def logaction_finish(self, action):
        super(_Reporter, self).logaction_finish(action)
        if action.activity == "runtests":
            teamcity.testSuiteFinished(action.id)


    def error(self, msg):
        super(_Reporter, self).error(msg)
        name = teamcity.current_test_name()
        if name:
            teamcity.testError(name, msg)
            if name == teamcity.topmost_suite:
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
    tmp_config.setenv.update({"_jb_do_not_call_enter_matrix": "1"})
    commands = tmp_config.commands
    if isinstance(commands, list) and len(commands) == 1:
        command_with_arguments = commands[0]
        if command_with_arguments[0] in _RUNNERS:
            command_with_arguments = _RUNNERS[command_with_arguments[0]]
            tmp_config.commands = [command_with_arguments]

session = tox_session.Session(config, Report=_Reporter)
teamcity.testMatrixEntered()
session.runcommand()