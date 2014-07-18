# coding=utf-8
"""
BDD lettuce framework runner
"""
__author__ = 'Ilya.Kazakevich'
import os
from lettuce.exceptions import ReasonToFail
import time
import sys
import tcmessages
import lettuce
from lettuce import core


# Error message about unsupported outlines
_NO_OUTLINE_ERROR = "Outline scenarios are not supported due to https://github.com/gabrielfalcao/lettuce/issues/451"


class LettuceRunner(object):
    """
    TODO: Runs lettuce
    """

    def __init__(self, base_dir):
        """
        :param base_dir base directory to run tests in
        :type base_dir: str

        """
        self.base_dir = base_dir
        self.runner = lettuce.Runner(base_dir)
        self.messages = tcmessages.TeamcityServiceMessages()
        self.test_start_time = None

    def report_tests(self):
        """
        :returns : number of tests
        :rtype : int
        """
        result = 0
        for feature_file in self.runner.loader.find_feature_files():
            feature = core.Feature.from_file(feature_file)
            for scenario in feature.scenarios:
                assert isinstance(scenario, core.Scenario), scenario
                if not scenario.outlines:
                    result += len(scenario.steps)
        self.messages.testCount(result)

    def report_scenario_started(self, scenario):
        """
        Reports scenario launched
        :type scenario core.Scenario
        :param scenario: scenario
        """
        if scenario.outlines:
            self.messages.testIgnored(scenario.name,
                                      _NO_OUTLINE_ERROR)
            scenario.steps = []  # Clear to prevent running. TODO: Fix when this issue fixed
            scenario.background = None  # TODO: undocumented
            return
        self.report_suite(True, scenario.name, scenario.described_at)

    def report_suite(self, is_start, name, described_at):
        """
        Reports some suite (scenario, feature, background etc) is started or stopped
        :param is_start: started or not
        :param name: suite name
        :param described_at: where it is described (file, line)
        :return:
        """
        if is_start:
            self.messages.testSuiteStarted(name, self._gen_location(described_at))
        else:
            self.messages.testSuiteFinished(name)

    def report_step(self, is_start, step):
        """
        Reports step start / stop
        :param is_start: true if step started
        :type step core.Step
        :param step: step
        """
        test_name = step.sentence
        if is_start:
            self.test_start_time = time.time()
            self.messages.testStarted(test_name, self._gen_location(step.described_at))
        elif step.passed:
            duration = 0
            if self.test_start_time:
                duration = long(time.time() - self.test_start_time)
            self.messages.testFinished(test_name, duration=duration)
            self.test_start_time = None
        elif step.failed:
            reason = step.why
            assert isinstance(reason, ReasonToFail), reason
            self.messages.testFailed(test_name, message=reason.exception, details=reason.traceback)

    def _gen_location(self, description):
        """
        :param description: "described_at" (file, line)
        :return: location in format file:line by "described_at"
        """
        return "file:///{}/{}:{}".format(self.base_dir, description.file, description.line)

    def run(self):
        """
        Launches runner
        """
        self.report_tests()
        self.messages.testMatrixEntered()

        lettuce.before.each_feature(lambda f: self.report_suite(True, f.name, f.described_at))
        lettuce.after.each_feature(lambda f: self.report_suite(False, f.name, f.described_at))

        lettuce.before.each_scenario(lambda s: self.report_scenario_started(s))
        lettuce.after.each_scenario(lambda s: self.report_suite(False, s.name, s.described_at))

        lettuce.before.each_background(
            lambda b, *args: self.report_suite(True, "Scenario background", b.feature.described_at))
        lettuce.after.each_background(
            lambda b, *args: self.report_suite(False, "Scenario background", b.feature.described_at))

        lettuce.before.each_step(lambda s: self.report_step(True, s))
        lettuce.after.each_step(lambda s: self.report_step(False, s))

        self.runner.run()


if __name__ == "__main__":
    path = sys.argv[1] if len(sys.argv) > 1 else "."
    assert os.path.exists(path), "{} does not exist".format(path)
    LettuceRunner(path).run()