import os
import sys
import unittest

from behave import configuration
from behave.step_registry import registry as the_step_registry

from behave_runner import _BehaveRunner
from behave_runner import _register_null_formatter
from testing import _helpers_test_resources_root

_EXAMPLES = os.path.join(_helpers_test_resources_root, "behave_examples")


def _clear_step_registry():
    # behave <= 1.2.6 has no StepRegistry.clear(), see PY-86339
    if hasattr(the_step_registry, 'clear'):
        the_step_registry.clear()
    else:
        the_step_registry.steps = dict(given=[], when=[], then=[], step=[])


def _make_config(example, feature):
    my_config = configuration.Configuration()
    base_dir = os.path.join(_EXAMPLES, example)
    my_config.paths = [os.path.join(base_dir, feature)]

    format_name = "com.jetbrains.pycharm.formatter"
    _register_null_formatter(format_name)
    my_config.format = [format_name]
    my_config.reporters = []
    my_config.stdout_capture = False
    my_config.stderr_capture = False
    return my_config, base_dir


class BehaveRunnerTest(unittest.TestCase):
    def test_scenarios_to_run(self):
        my_config, base_dir = _make_config("feature_with_rules", "rule.feature")

        br = _BehaveRunner(my_config, base_dir, use_old_runner=False)
        features = br._get_features_to_run()

        self.assertEqual(len(features), 1)
        self.assertEqual(len(list(features[0].scenarios)), 5)
        # Read twice on purpose: "scenarios" must not be a one-shot iterator
        self.assertEqual(len(list(features[0].scenarios)), 5)

    def test_number_of_tests_includes_background_steps(self):
        # rule.feature runs 17 steps: 1 feature background step per scenario (5),
        # 2 steps of the R1 background for each of the 2 scenarios of that rule (4),
        # and the steps of the scenarios themselves (2 + 2 + 2 + 1 + 1 = 8)
        my_config, base_dir = _make_config("feature_with_rules", "rule.feature")

        br = _BehaveRunner(my_config, base_dir, use_old_runner=False)

        self.assertEqual(br._get_number_of_tests(), 17)


class BehaveRunnerExecutionCountTest(unittest.TestCase):
    """
    The test count needs the features before the real run. The runner must get them
    without an execution of environment.py and the step modules, so that the real run
    executes each of them once, like the "behave" command (PY-92481).
    """

    _FIXTURE_MODULES = ('nested_pkg', 'nested_pkg.counters', 'nested_pkg.more_steps')

    def setUp(self):
        self._saved_path = list(sys.path)
        self._drop_fixture_modules()
        _clear_step_registry()

    def tearDown(self):
        self._drop_fixture_modules()
        _clear_step_registry()
        sys.path[:] = self._saved_path
        if hasattr(sys, '_pycharm_behave_fixture_counters'):
            delattr(sys, '_pycharm_behave_fixture_counters')

    def _drop_fixture_modules(self):
        for name in self._FIXTURE_MODULES:
            sys.modules.pop(name, None)

    def _run_nested_feature(self):
        cwd = os.getcwd()
        my_config, base_dir = _make_config("nested_steps", "nested.feature")
        try:
            _BehaveRunner(my_config, base_dir, use_old_runner=False).run()
        finally:
            os.chdir(cwd)
        return getattr(sys, '_pycharm_behave_fixture_counters', {})

    def test_each_module_is_executed_once(self):
        counters = self._run_nested_feature()

        self.assertEqual(counters, {
            'environment': 1,
            'before_all': 1,
            'steps': 1,
            'nested_pkg': 1,
            'nested_pkg.counters': 1,
            'nested_pkg.more_steps': 1,
        })

    def test_number_of_tests_executes_no_project_code(self):
        my_config, base_dir = _make_config("nested_steps", "nested.feature")

        number_of_tests = _BehaveRunner(my_config, base_dir, use_old_runner=False)._get_number_of_tests()

        self.assertEqual(number_of_tests, 2)
        self.assertEqual(getattr(sys, '_pycharm_behave_fixture_counters', {}), {})

    def test_nested_steps_are_defined_on_the_real_run(self):
        self._run_nested_feature()

        # Without these registrations every step of the feature would be undefined.
        # behave 1.3.x keeps the anchored regexp in "pattern", 1.2.6 stores the original
        patterns = set(matcher.pattern.strip('^$') for matchers in the_step_registry.steps.values()
                       for matcher in matchers)
        self.assertEqual(patterns, {"I am set up by a nested step",
                                    "the nested step module is loaded"})
