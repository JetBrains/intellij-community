# coding=utf-8
"""
Behave BDD runner.
See _bdd_utils#get_path_by_env for information how to pass list of features here.
Each feature could be file, folder with feature files or folder with "features" subfolder

Other args are tag expressionsin format (--tags=.. --tags=..).
See https://pythonhosted.org/behave/behave.html#tag-expression
"""
import functools
import glob
import sys
import os
import traceback

from behave.formatter.base import Formatter
from behave.model import Step, ScenarioOutline, Feature, Scenario
from behave.tag_expression import TagExpression
import re

import _bdd_utils
from distutils import version
from behave import __version__ as behave_version
from _jb_utils import VersionAgnosticUtils
_MAX_STEPS_SEARCH_FEATURES = 5000  # Do not look for features in folder that has more that this number of children
_FEATURES_FOLDER = 'features'  # "features" folder name.

__author__ = 'Ilya.Kazakevich'

from behave import configuration, runner


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
            err = "Folder {0} is too deep to find any features folder. Please provider concrete folder".format(
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
        utils = VersionAgnosticUtils()
        if isinstance(element, Step):
            # Process step
            step_name = u"{0} {1}".format(utils.to_unicode(element.keyword), utils.to_unicode(element.name))
            duration_ms = element.duration * 1000
            if is_started:
                self._test_started(step_name, element.location)
            elif element.status == 'passed':
                self._test_passed(step_name, duration_ms)
            elif element.status == 'failed':
                # Correct way is to use element.errormessage
                # but assertions do not have trace there (due to Behave internals)
                # do, we collect it manually
                error_message = element.error_message
                fetch_log = not error_message  # If no error_message provided, need to fetch log manually
                trace = ""
                if isinstance(element.exception, AssertionError):
                    trace = self._collect_trace(element, utils)

                # May be empty https://github.com/behave/behave/issues/468 for some exceptions
                if not trace and not error_message:
                    try:
                        error_message = traceback.format_exc()
                    except AttributeError:
                        # Exception may have empty stracktrace, and traceback.format_exc() throws
                        # AttributeError in this case
                        trace = self._collect_trace(element, utils)
                if not error_message:
                    # Format exception as last resort
                    error_message = element.exception
                message_as_string = utils.to_unicode(error_message)
                if fetch_log and self.__real_runner.config.log_capture:
                    message_as_string += u"\n" + utils.to_unicode(self.__real_runner.log_capture.getvalue())
                self._test_failed(step_name, message_as_string, trace, duration=duration_ms)
            elif element.status == 'undefined':
                self._test_undefined(step_name, element.location)
            else:
                self._test_skipped(step_name, element.status, element.location)
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

    def _collect_trace(self, element, utils):
        return u"".join([utils.to_unicode(l) for l in traceback.format_tb(element.exc_traceback)])

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


    def __filter_scenarios_by_args(self, scenario):
        """
        Filters out scenarios that should be skipped by tags or scenario names
        :param scenario scenario to check
        :return true if should pass
        """
        assert isinstance(scenario, Scenario), scenario
        # TODO: share with lettuce_runner.py#_get_features_to_run
        expected_tags = self.__config.tags
        scenario_name_re = self.__config.name_re
        if scenario_name_re and not scenario_name_re.match(scenario.name):
            return False
        if not expected_tags:
            return True  # No tags nor names are required
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
            feature.scenarios = filter(self.__filter_scenarios_by_args, scenarios)

        return features_to_run


if __name__ == "__main__":
    # TODO: support all other params instead

    class _Null(Formatter):
        """
        Null formater to prevent stdout output
        """
        pass

    command_args = list(filter(None, sys.argv[1:]))
    if command_args:
        if "--junit" in command_args:
            raise Exception("--junit report type for Behave is unsupported in PyCharm. \n "
            "See: https://youtrack.jetbrains.com/issue/PY-14219")
        _bdd_utils.fix_win_drive(command_args[0])
    (base_dir, scenario_names, what_to_run) = _bdd_utils.get_what_to_run_by_env(os.environ)

    for scenario_name in scenario_names:
        command_args += ["-n", re.escape(scenario_name)]  # TODO : rewite pythonic

    my_config = configuration.Configuration(command_args=command_args)

    # Temporary workaround to support API changes in 1.2.5
    if version.LooseVersion(behave_version) >= version.LooseVersion("1.2.5"):
        from behave.formatter import _registry
        _registry.register_as("com.intellij.python.null",_Null)
    else:
        from behave.formatter import formatters
        formatters.register_as(_Null, "com.intellij.python.null")


    my_config.format = ["com.intellij.python.null"]  # To prevent output to stdout
    my_config.reporters = []  # To prevent summary to stdout
    my_config.stdout_capture = False  # For test output
    my_config.stderr_capture = False  # For test output
    features = set()
    for feature in what_to_run:
        if os.path.isfile(feature) or glob.glob(
                os.path.join(feature, "*.feature")):  # File of folder with "features"  provided, load it
            features.add(feature)
        elif os.path.isdir(feature):
            features |= set(_get_dirs_to_run(feature))  # Find "features" subfolder
    my_config.paths = list(features)
    if what_to_run and not my_config.paths:
        raise Exception("Nothing to run in {0}".format(what_to_run))
    _BehaveRunner(my_config, base_dir).run()
