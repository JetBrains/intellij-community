# coding=utf-8
"""
BDD lettuce framework runner
TODO: Support other params (like tags) as well.
Supports only 2 params now: folder to search "features" for or file and "-s scenario_index"
"""
import inspect
import optparse
import os
import _bdd_utils

__author__ = 'Ilya.Kazakevich'
from lettuce.exceptions import ReasonToFail
import lettuce
from lettuce import core


class _LettuceRunner(_bdd_utils.BddRunner):
    """
    Lettuce runner (BddRunner for lettuce)
    """

    def __init__(self, base_dir, what_to_run, scenarios, options):
        """

        :param scenarios scenario numbers to run
        :type scenarios list
        :param base_dir base directory to run tests in
        :type base_dir: str
        :param what_to_run folder or file to run
        :type options optparse.Values
        :param options optparse options passed by user
        :type what_to_run str

        """
        super(_LettuceRunner, self).__init__(base_dir)
        # TODO: Copy/Paste with lettuce.bin, need to reuse somehow

        # Delete args that do not exist in constructor
        args_to_pass = options.__dict__
        runner_args = inspect.getargspec(lettuce.Runner.__init__)[0]
        unknown_args = set(args_to_pass.keys()) - set(runner_args)
        map(args_to_pass.__delitem__, unknown_args)

        # Tags is special case and need to be preprocessed
        self.__tags = None  # Store tags in field
        if 'tags' in args_to_pass.keys() and args_to_pass['tags']:
            args_to_pass['tags'] = [tag.strip('@') for tag in args_to_pass['tags']]
            self.__tags = set(args_to_pass['tags'])

        # Special cases we pass directly
        args_to_pass['base_path'] = what_to_run
        args_to_pass['scenarios'] = ",".join(scenarios)

        self.__runner = lettuce.Runner(**args_to_pass)

    def _get_features_to_run(self):
        super(_LettuceRunner, self)._get_features_to_run()
        features = []
        if self.__runner.single_feature:  # We need to run one and only one feature
            features = [core.Feature.from_file(self.__runner.single_feature)]
        else:
            # Find all features in dir
            for feature_file in self.__runner.loader.find_feature_files():
                feature = core.Feature.from_file(feature_file)
                assert isinstance(feature, core.Feature), feature
                if feature.scenarios:
                    features.append(feature)

        # Choose only selected scenarios
        if self.__runner.scenarios:
            for feature in features:
                filtered_feature_scenarios = []
                for index in [i - 1 for i in self.__runner.scenarios]:  # decrease index by 1
                    if index < len(feature.scenarios):
                        filtered_feature_scenarios.append(feature.scenarios[index])
                feature.scenarios = filtered_feature_scenarios

        # Filter out tags TODO: Share with behave_runner.py#__filter_scenarios_by_args
        if self.__tags:
            for feature in features:
                feature.scenarios = filter(lambda s: set(s.tags) & self.__tags, feature.scenarios)
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
            self._test_failed(test_name, message=reason.exception.message, details=reason.traceback)
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

        try:
            lettuce.before.each_outline(lambda s, o: self.__outline(True, s, o))
            lettuce.after.each_outline(lambda s, o: self.__outline(False, s, o))
        except AttributeError:
            import sys
            sys.stderr.write("WARNING: your lettuce version is outdated and does not support outline hooks. "
                             "Outline scenarios may not work. Consider upgrade to latest lettuce (0.22 at least)")

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

    def __outline(self, is_started, scenario, outline):
        """
        report outline is started or finished
        """
        outline_description = ["{0}: {1}".format(k, v) for k, v in outline.items()]
        self._feature_or_scenario(is_started, "Outline {0}".format(outline_description), scenario.described_at)

    def __scenario(self, is_started, scenario):
        """
        Reports scenario launched
        :type scenario core.Scenario
        :param scenario: scenario
        """
        self._feature_or_scenario(is_started, scenario.name, scenario.described_at)


def _get_args():
    """
    Get options passed by user

    :return: tuple (options, args), see optparse
    """
    # TODO: Copy/Paste with lettuce.bin, need to reuse somehow
    parser = optparse.OptionParser()
    parser.add_option("-v", "--verbosity",
                      dest="verbosity",
                      default=0,  # We do not need verbosity due to GUI we use (although user may override it)
                      help='The verbosity level')

    parser.add_option("-s", "--scenarios",
                      dest="scenarios",
                      default=None,
                      help='Comma separated list of scenarios to run')

    parser.add_option("-t", "--tag",
                      dest="tags",
                      default=None,
                      action='append',
                      help='Tells lettuce to run the specified tags only; '
                           'can be used multiple times to define more tags'
                           '(prefixing tags with "-" will exclude them and '
                           'prefixing with "~" will match approximate words)')

    parser.add_option("-r", "--random",
                      dest="random",
                      action="store_true",
                      default=False,
                      help="Run scenarios in a more random order to avoid interference")

    parser.add_option("--with-xunit",
                      dest="enable_xunit",
                      action="store_true",
                      default=False,
                      help='Output JUnit XML test results to a file')

    parser.add_option("--xunit-file",
                      dest="xunit_file",
                      default=None,
                      type="string",
                      help='Write JUnit XML to this file. Defaults to '
                           'lettucetests.xml')

    parser.add_option("--with-subunit",
                      dest="enable_subunit",
                      action="store_true",
                      default=False,
                      help='Output Subunit test results to a file')

    parser.add_option("--subunit-file",
                      dest="subunit_filename",
                      default=None,
                      help='Write Subunit data to this file. Defaults to '
                           'subunit.bin')

    parser.add_option("--failfast",
                      dest="failfast",
                      default=False,
                      action="store_true",
                      help='Stop running in the first failure')

    parser.add_option("--pdb",
                      dest="auto_pdb",
                      default=False,
                      action="store_true",
                      help='Launches an interactive debugger upon error')
    return parser.parse_args()


if __name__ == "__main__":
    options, args = _get_args()
    (base_dir, scenarios, what_to_run) = _bdd_utils.get_what_to_run_by_env(os.environ)
    if len(what_to_run) > 1:
        raise Exception("Lettuce can't run more than one file now")
    _bdd_utils.fix_win_drive(what_to_run[0])
    _LettuceRunner(base_dir, what_to_run[0], scenarios, options).run()