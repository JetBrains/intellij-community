# coding=utf-8
"""
Behave BDD runner.
*FIRST* param now: folder to search "features" for.
Each "features" folder should have features and "steps" subdir.

Other args are tag expressionsin format (--tags=.. --tags=..).
See https://pythonhosted.org/behave/behave.html#tag-expression
"""
import functools
import sys
import os
import traceback

from behave.formatter.base import Formatter
from behave.model import Step, ScenarioOutline, Feature, Scenario
from behave.tag_expression import TagExpression

import _bdd_utils


_MAX_STEPS_SEARCH_FEATURES = 5000  # Do not look for features in folder that has more that this number of children
_FEATURES_FOLDER = 'features'  # "features" folder name.

__author__ = 'Ilya.Kazakevich'

from behave import configuration, runner
from behave.formatter import formatters


def _get_dirs_to_run(base_dir_to_search):
    """
    Searches for "features" dirs in some base_dir
    :return: list of feature dirs to run
    :rtype: list
    :param base_dir_to_search root directory to search (should not have too many children!)
    :type base_dir_to_search str

    """
    result = set()
    for (step, (folder, sub_folders, files)) in enumerate(os.walk(base_dir_to_search)):
        if os.path.basename(folder) == _FEATURES_FOLDER and os.path.isdir(folder):
            result.add(os.path.abspath(folder))
        if step == _MAX_STEPS_SEARCH_FEATURES:  # Guard
            err = "Folder {} is too deep to find any features folder. Please provider concrete folder".format(
                base_dir_to_search)
            raise Exception(err)
    return list(result)


def _merge_hooks_wrapper(*hooks):
    """
    Creates wrapper that runs provided behave hooks sequentally
    :param hooks: hooks to run
    :return: wrapper
    """
    # TODO: Wheel reinvented!!!!
    def wrapper(*args, **kwargs):
        for hook in hooks:
            hook(*args, **kwargs)

    return wrapper


class _RunnerWrapper(runner.Runner):
    """
    Wrapper around behave native wrapper. Has nothing todo with BddRunner!
    We need it to support dry runs (to fetch data from scenarios) and hooks api
    """

    def __init__(self, config, hooks):
        """
        :type config configuration.Configuration
        :param config behave configuration
        :type hooks dict
        :param hooks hooks in format "before_scenario" => f(context, scenario) to load after/before hooks, provided by user
        """
        super(_RunnerWrapper, self).__init__(config)
        self.dry_run = False
        """
        Does not run tests (only fetches "self.features") if true. Runs tests otherwise.
        """
        self.__hooks = hooks

    def load_hooks(self, filename='environment.py'):
        """
        Overrides parent "load_hooks" to add "self.__hooks"
        :param filename: env. file name
        """
        super(_RunnerWrapper, self).load_hooks(filename)
        for (hook_name, hook) in self.__hooks.items():
            hook_to_add = hook
            if hook_name in self.hooks:
                user_hook = self.hooks[hook_name]
                if hook_name.startswith("before"):
                    user_and_custom_hook = [user_hook, hook]
                else:
                    user_and_custom_hook = [hook, user_hook]
                hook_to_add = _merge_hooks_wrapper(*user_and_custom_hook)
            self.hooks[hook_name] = hook_to_add

    def run_model(self, features=None):
        """
        Overrides parent method to stop (do nothing) in case of "dry_run"
        :param features: features to run
        :return:
        """
        if self.dry_run:  # To stop further execution
            return
        return super(_RunnerWrapper, self).run_model(features)

    def clean(self):
        """
        Cleans runner after dry run (clears hooks, features etc). To be called before real run!
        """
        self.dry_run = False
        self.hooks.clear()
        self.features = []


class _BehaveRunner(_bdd_utils.BddRunner):
    """
    BddRunner for behave
    """


    def __process_hook(self, is_started, context, element):
        """
        Hook to be installed. Reports steps, features etc.
        :param is_started true if test/feature/scenario is started
        :type is_started bool
        :param context behave context
        :type context behave.runner.Context
        :param element feature/suite/step
        """
        element.location.file = element.location.filename  # To preserve _bdd_utils contract
        if isinstance(element, Step):
            # Process step
            if is_started:
                self._test_started(element.name, element.location)
            elif element.status == 'passed':
                self._test_passed(element.name, element.duration)
            elif element.status == 'failed':
                try:
                    trace = traceback.format_exc()
                except Exception:
                    trace = "".join(traceback.format_tb(element.exc_traceback))
                self._test_failed(element.name, element.error_message, trace)
            elif element.status == 'undefined':
                self._test_undefined(element.name, element.location)
            else:
                self._test_skipped(element.name, element.status, element.location)
        elif not is_started and isinstance(element, Scenario) and element.status == 'failed':
            # To process scenarios with undefined/skipped tests
            for step in element.steps:
                assert isinstance(step, Step), step
                if step.status not in ['passed', 'failed']:  # Something strange, probably skipped or undefined
                    self.__process_hook(False, context, step)
            self._feature_or_scenario(is_started, element.name, element.location)
        elif isinstance(element, ScenarioOutline):
            self._feature_or_scenario(is_started, str(element.examples), element.location)
        else:
            self._feature_or_scenario(is_started, element.name, element.location)

    def __init__(self, config, base_dir):
        """
        :type config configuration.Configuration
        """
        super(_BehaveRunner, self).__init__(base_dir)
        self.__config = config
        # Install hooks
        self.__real_runner = _RunnerWrapper(config, {
            "before_feature": functools.partial(self.__process_hook, True),
            "after_feature": functools.partial(self.__process_hook, False),
            "before_scenario": functools.partial(self.__process_hook, True),
            "after_scenario": functools.partial(self.__process_hook, False),
            "before_step": functools.partial(self.__process_hook, True),
            "after_step": functools.partial(self.__process_hook, False)
        })

    def _run_tests(self):
        self.__real_runner.run()


    def __filter_scenarios_by_tag(self, scenario):
        """
        Filters out scenarios that should be skipped by tags
        :param scenario scenario to check
        :return true if should pass
        """
        assert isinstance(scenario, Scenario), scenario
        expected_tags = self.__config.tags
        if not expected_tags:
            return True  # No tags are required
        return isinstance(expected_tags, TagExpression) and expected_tags.check(scenario.tags)


    def _get_features_to_run(self):
        self.__real_runner.dry_run = True
        self.__real_runner.run()
        features_to_run = self.__real_runner.features
        self.__real_runner.clean()  # To make sure nothing left after dry run

        # Change outline scenario skeletons with real scenarios
        for feature in features_to_run:
            assert isinstance(feature, Feature), feature
            scenarios = []
            for scenario in feature.scenarios:
                if isinstance(scenario, ScenarioOutline):
                    scenarios.extend(scenario.scenarios)
                else:
                    scenarios.append(scenario)
            feature.scenarios = filter(self.__filter_scenarios_by_tag, scenarios)

        return features_to_run


if __name__ == "__main__":
    # TODO: support all other params instead

    class _Null(Formatter):
        """
        Null formater to prevent stdout output
        """
        pass

    command_args = list(filter(None, sys.argv[1:]))
    my_config = configuration.Configuration(command_args=command_args)
    formatters.register_as(_Null, "com.intellij.python.null")
    my_config.format = ["com.intellij.python.null"]  # To prevent output to stdout
    my_config.reporters = []  # To prevent summary to stdout
    my_config.stdout_capture = False  # For test output
    my_config.stderr_capture = False  # For test output
    (base_dir, what_to_run) = _bdd_utils.get_path_by_args(sys.argv)
    if not my_config.paths:  # No path provided, trying to load dit manually
        if os.path.isfile(what_to_run):  # File is provided, load it
            my_config.paths = [what_to_run]
        else:  # Dir is provided, find subdirs ro run
            my_config.paths = _get_dirs_to_run(base_dir)
    _BehaveRunner(my_config, base_dir).run()


