# coding=utf-8
"""
Behave formatter that supports TC
"""
import datetime
import traceback
from collections import deque
from distutils import version

from behave.formatter.base import Formatter
from behave.model import Step, Feature, Scenario
from behave.model_core import Status
from behave import __version__ as behave_version

from teamcity.messages import TeamcityServiceMessages


def _step_name(step):
    assert isinstance(step, Step)
    return step.keyword + " " + step.name.strip()


def _suite_name(suite):
    return suite.name.strip()


class TeamcityFormatter(Formatter):
    """
    Stateful TC reporter.
    Since we can't fetch all steps from the very beginning (even skipped tests are reported)
    we store tests and features on each call.

    To hook into test reporting override _report_suite_started and/or _report_test_started
    """

    def __init__(self, stream_opener, config):
        super(TeamcityFormatter, self).__init__(stream_opener, config)
        assert version.LooseVersion(behave_version) >= version.LooseVersion("1.2.6"), "Only 1.2.6+ is supported"
        self._messages = TeamcityServiceMessages()

        self.__feature = None
        self.__scenario = None
        self.__steps = deque()
        self.__scenario_opened = False
        self.__feature_opened = False

        self.__test_start_time = None

    def feature(self, feature):
        assert isinstance(feature, Feature)
        assert not self.__feature, "Prev. feature not closed"
        self.__feature = feature

    def scenario(self, scenario):
        assert isinstance(scenario, Scenario)
        self.__scenario = scenario
        self.__scenario_opened = False
        self.__steps.clear()

    def step(self, step):
        assert isinstance(step, Step)
        self.__steps.append(step)

    def match(self, match):
        if not self.__feature_opened:
            self._report_suite_started(self.__feature, _suite_name(self.__feature))
            self.__feature_opened = True

        if not self.__scenario_opened:
            self._report_suite_started(self.__scenario, _suite_name(self.__scenario))
            self.__scenario_opened = True

        assert self.__steps, "No steps left"

        step = self.__steps.popleft()
        self._report_test_started(step, _step_name(step))
        self.__test_start_time = datetime.datetime.now()

    def _report_suite_started(self, suite, suite_name):
        """
        :param suite: behave suite
        :param suite_name: suite name that must be reported, be sure to use it instead of suite.name

        """
        self._messages.testSuiteStarted(suite_name)

    def _report_test_started(self, test, test_name):
        """
        Suite name is always stripped, be sure to strip() it too
        :param test: behave test
        :param test_name: test name that must be reported, be sure to use it instead of test.name
        """
        self._messages.testStarted(test_name)

    def result(self, step):
        assert isinstance(step, Step)
        step_name = _step_name(step)
        if step.status == Status.failed:
            try:
                error = traceback.format_exc(step.exc_traceback)
                if error != step.error_message:
                    self._messages.testStdErr(step_name, error)
            except Exception:
                pass  # exception shall not prevent error message
            self._messages.testFailed(step_name, message=step.error_message)

        if step.status == Status.undefined:
            self._messages.testFailed(step_name, message="Undefined")

        if step.status == Status.skipped:
            self._messages.testIgnored(step_name)

        self._messages.testFinished(step_name, testDuration=datetime.datetime.now() - self.__test_start_time)

        if not self.__steps:
            self.__close_scenario()
        elif step.status in [Status.failed, Status.undefined]:
            # Broken background/undefined step stops whole scenario

            reason = "Undefined step" if step.status == Status.undefined else "Prev. step failed"
            self.__skip_rest_of_scenario(reason)

    def __skip_rest_of_scenario(self, reason):
        while self.__steps:
            step = self.__steps.popleft()
            self._report_test_started(step, _step_name(step))
            self._messages.testIgnored(_step_name(step),
                                       message="{0}. Rest part of scenario is skipped".format(reason))
            self._messages.testFinished(_step_name(step))
        self.__close_scenario()

    def __close_scenario(self):
        if self.__scenario:
            self._messages.testSuiteFinished(_suite_name(self.__scenario))
            self.__scenario = None

    def eof(self):
        self.__skip_rest_of_scenario("")
        self._messages.testSuiteFinished(_suite_name(self.__feature))
        self.__feature = None
        self.__feature_opened = False
