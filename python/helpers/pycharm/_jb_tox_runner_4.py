"""
Runs tox from current directory for tox 4

"""
import dataclasses
import enum
import os
import sys
from typing import List

from tox.config.sets import ConfigSet
from tox.config.types import Command
from tox.execute import Outcome
from tox.plugin import impl
from tox.plugin.manager import MANAGER
from tox.run import main
from tox.session.state import State
from tox.tox_env.api import ToxEnv
from tox.tox_env.python.pip.req_file import PythonDeps
from tox.tox_env.python.virtual_env.runner import VirtualEnvRunner

from tcmessages import TeamcityServiceMessages

teamcity = TeamcityServiceMessages()

helpers_dir = str(os.path.split(__file__)[0])


class Fixer:
    def __init__(self, runner_name):
        self.runner_name = runner_name

    def fix(self, command: List[str], offset: int):
        return ["python", os.path.join(helpers_dir, self.runner_name), "--offset",
                str(offset), "--"]


class _Unit2(Fixer):
    def __init__(self):
        super(_Unit2, self).__init__("_jb_unittest_runner.py")

    def fix(self, command: List[str], offset: int):
        if command == ["python", "-m", "unittest", "discover"]:
            return super(_Unit2, self).fix(command, offset) + ["discover"]
        return None


class _PyTest(Fixer):
    def __init__(self):
        super(_PyTest, self).__init__("_jb_pytest_runner.py")

    def fix(self, command: List[str], offset: int):
        if command[0] not in ["pytest", "py.test"]:
            return None
        return super(_PyTest, self).fix(command, offset) + command[1:]


class _Nose(Fixer):
    def __init__(self):
        super(_Nose, self).__init__("_jb_nosetest_runner.py")

    def fix(self, command: List[str], offset: int):
        if command[0] != "nosetests":
            return None
        return super(_Nose, self).fix(command, offset) + command[1:]


_RUNNERS = [_Unit2(), _PyTest(), _Nose()]


class EnvState(enum.Enum):
    NOT_STARTED = 0,
    SUCCESS = 1,
    FAILED = 2


@dataclasses.dataclass
class Env:
    offset: int
    state: EnvState


class JBToxPlugin:
    def __init__(self):
        self.envs: dict[str, Env] = dict()
        self.current_offset: int = 1
        self.duration_strategy = "automatic"
        self.matrix_called = False
        self.skip_missing_interpreters = False

    @impl
    def tox_env_teardown(self, tox_env: ToxEnv):
        if tox_env.name not in self.envs:
            # Env hasn't been started, lets pretend we started it to skip it later
            self._start_the_env(tox_env)

        env = self.envs[tox_env.name]

        if env.state == EnvState.NOT_STARTED:
            if self.skip_missing_interpreters:
                teamcity.testIgnored(tox_env.name, nodeId=env.offset)
            else:
                teamcity.testFailed(tox_env.name, nodeId=env.offset)
        elif env.state == EnvState.FAILED:
            teamcity.testFailed(tox_env.name, nodeId=env.offset, details="")
        else:
            teamcity.testFinished(tox_env.name, nodeId=env.offset)

    @impl
    def tox_add_core_config(self, core_conf: ConfigSet, state: State):
        if state.conf.options.skip_missing_interpreters == "true":
            self.skip_missing_interpreters = True

    @impl
    def tox_on_install(self, tox_env: ToxEnv, arguments: PythonDeps, section: str,
                       of_type: str):
        try:
            requirements = [str(r) for r in arguments.requirements]
            if "pytest-xdist" in requirements:
                self.duration_strategy = "manual"
        except AttributeError:
            pass  # may have no req at all

        self._start_the_env(tox_env)

    def _start_the_env(self, tox_env: ToxEnv):
        if tox_env.name in self.envs or tox_env.name == ".pkg":
            return  # Might be called several times, and .pkg is technical call
        env_name = tox_env.name
        env = Env(self.current_offset, EnvState.NOT_STARTED)
        self.envs[env_name] = env

        if not self.matrix_called:
            teamcity.testMatrixEntered(durationStrategy=self.duration_strategy)
            self.matrix_called = True

        teamcity.testStarted(env_name, location="tox_env://" + env_name,
                             parentNodeId="0", nodeId=env.offset)
        self.current_offset += 1000

    @impl
    def tox_before_run_commands(self, tox_env: VirtualEnvRunner):
        tox_env.environment_variables["_jb_do_not_call_enter_matrix"] = "1"
        commands: List[Command] = tox_env.conf["commands"]
        if "_jb_do_not_patch_test_runners" not in os.environ or isinstance(commands,
                                                                           list):
            for command in commands:
                for runner in _RUNNERS:
                    env = self.envs[tox_env.name]
                    fixed = runner.fix(command.args, env.offset)
                    if fixed:
                        command.args = fixed

    @impl
    def tox_after_run_commands(self, tox_env: ToxEnv, exit_code: int,
                               outcomes: List[Outcome]):
        success = exit_code == 0 and all(o.exit_code == 0 for o in outcomes)
        self.envs[tox_env.name].state = EnvState.SUCCESS if success else EnvState.FAILED


def patch_plugin_manager():
    old = MANAGER.load_plugins

    def custom(path):
        old(path)
        MANAGER.manager.register(JBToxPlugin())

    MANAGER.load_plugins = custom


def run_tox_4():
    patch_plugin_manager()
    return main(sys.argv[1:])
