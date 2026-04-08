import os
import unittest

from behave import configuration

from behave_runner import _BehaveRunner
from behave_runner import _register_null_formatter
from testing import _helpers_test_resources_root


class BehaveRunnerTest(unittest.TestCase):
    def test_scenarios_to_run(self):
        my_config = configuration.Configuration()
        path = os.path.join(_helpers_test_resources_root, "behave_examples", "feature_with_rules")
        my_config.paths = [os.path.join(path, "rule.feature")]
        base_dir = path

        format_name = "com.jetbrains.pycharm.formatter"
        _register_null_formatter(format_name)
        my_config.format = [format_name]

        br = _BehaveRunner(my_config, base_dir, use_old_runner=False)
        features = br._get_features_to_run()

        self.assertEqual(len(features), 1)
        self.assertEqual(len(list(features[0].scenarios)), 5)
