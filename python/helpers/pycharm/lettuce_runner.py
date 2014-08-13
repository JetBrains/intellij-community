# coding=utf-8
"""
BDD lettuce framework runner
TODO: Support other params (like tags) as well.
Supports only 1 param now: folder to search "features" for.
"""
import _bdd_utils

__author__ = 'Ilya.Kazakevich'
from lettuce.exceptions import ReasonToFail
import sys
import lettuce
from lettuce import core


class _LettuceRunner(_bdd_utils.BddRunner):
    """
    Lettuce runner (BddRunner for lettuce)
    """

    def __init__(self, base_dir, what_to_run):
        """

        :param base_dir base directory to run tests in
        :type base_dir: str
        :param what_to_run folder or file to run
        :type what_to_run str
        """
        super(_LettuceRunner, self).__init__(base_dir)
        self.__runner = lettuce.Runner(what_to_run)

    def _get_features_to_run(self):
        super(_LettuceRunner, self)._get_features_to_run()
        if self.__runner.single_feature:  # We need to run one and only one feature
            return [core.Feature.from_file(self.__runner.single_feature)]

        # Find all features in dir
        features = []
        for feature_file in self.__runner.loader.find_feature_files():
            feature = core.Feature.from_file(feature_file)
            assert isinstance(feature, core.Feature), feature
            # TODO: cut out due to https://github.com/gabrielfalcao/lettuce/issues/451  Fix when this issue fixed
            feature.scenarios = filter(lambda s: not s.outlines, feature.scenarios)
            if feature.scenarios:
                features.append(feature)
        return features

    def _run_tests(self):
        super(_LettuceRunner, self)._run_tests()
        self.__install_hooks()
        self.__runner.run()

    def __step(self, is_started, step):
        """
        Reports step start / stop
        :type step core.Step
        :param step: step
        """
        test_name = step.sentence
        if is_started:
            self._test_started(test_name, step.described_at)
        elif step.passed:
            self._test_passed(test_name)
        elif step.failed:
            reason = step.why
            assert isinstance(reason, ReasonToFail), reason
            self._test_failed(test_name, message=reason.exception, details=reason.traceback)
        elif step.has_definition:
            self._test_skipped(test_name, "In lettuce, we do know the reason", step.described_at)
        else:
            self._test_undefined(test_name, step.described_at)

    def __install_hooks(self):
        """
        Installs required hooks
        """

        # Install hooks
        lettuce.before.each_feature(
            lambda f: self._feature_or_scenario(True, f.name, f.described_at))
        lettuce.after.each_feature(
            lambda f: self._feature_or_scenario(False, f.name, f.described_at))

        lettuce.before.each_scenario(
            lambda s: self.__scenario(True, s))
        lettuce.after.each_scenario(
            lambda s: self.__scenario(False, s))

        lettuce.before.each_background(
            lambda b, *args: self._background(True, b.feature.described_at))
        lettuce.after.each_background(
            lambda b, *args: self._background(False, b.feature.described_at))

        lettuce.before.each_step(lambda s: self.__step(True, s))
        lettuce.after.each_step(lambda s: self.__step(False, s))

    def __scenario(self, is_started, scenario):
        """
        Reports scenario launched
        :type scenario core.Scenario
        :param scenario: scenario
        """
        if scenario.outlines:
            scenario.steps = []  # Clear to prevent running. TODO: Fix when this issue fixed
            scenario.background = None  # TODO: undocumented
            return
        self._feature_or_scenario(is_started, scenario.name, scenario.described_at)


if __name__ == "__main__":
    (base_dir, what_to_run) = _bdd_utils.get_path_by_args(sys.argv)
    _LettuceRunner(base_dir, what_to_run).run()