# coding=utf-8
"""
Tools for running BDD frameworks in python.
You probably need to extend BddRunner (see its doc).

You may also need "get_path_by_args" that gets folder (current or passed as first argument)
"""
import os
import time
import abc

import tcmessages


__author__ = 'Ilya.Kazakevich'


def get_path_by_args(arguments):
    """
    :type arguments list
    :param arguments: arguments (sys.argv)
    :return: tuple (base_dir, what_to_run) where dir is current or first argument from argv, checking it exists
    :rtype tuple of str
    """
    what_to_run = arguments[1] if len(arguments) > 1 else "."
    base_dir = what_to_run
    assert os.path.exists(what_to_run), "{} does not exist".format(what_to_run)

    if os.path.isfile(what_to_run):
        base_dir = os.path.dirname(what_to_run) # User may point to the file directly
    return base_dir, what_to_run


class BddRunner(object):
    """
    Extends this class, implement abstract methods and use its API to implement new BDD frameworks.
    Call "run()" to launch it.
    This class does the following:
    * Gets features to run (using "_get_features_to_run()") and calculates steps in it
    * Reports steps to Intellij or TC
    * Calls "_run_tests()" where *you* should install all hooks you need into your BDD and use "self._" functions
    to report tests and features. It actually wraps tcmessages but adds some stuff like duration count etc
    :param base_dir:
    """
    __metaclass__ = abc.ABCMeta

    def __init__(self, base_dir):
        """
        :type base_dir str
        :param base_dir base directory of your project
        """
        super(BddRunner, self).__init__()
        self.tc_messages = tcmessages.TeamcityServiceMessages()
        """
        tcmessages TeamCity/Intellij test API. See TeamcityServiceMessages
        """
        self.__base_dir = base_dir
        self.__last_test_start_time = None  # TODO: Doc when use
        self.__last_test_name = None

    def run(self):
        """"
        Runs runner. To be called right after constructor.
        """
        self.tc_messages.testCount(self._get_number_of_tests())
        self.tc_messages.testMatrixEntered()
        self._run_tests()

    def __gen_location(self, location):
        """
        Generates location in format, supported by tcmessages
        :param location object with "file" (relative to base_dir) and "line" fields.
        :return: location in format file:line (as supported in tcmessages)
        """
        my_file = str(location.file).lstrip("/\\")
        return "file:///{}:{}".format(os.path.normpath(os.path.join(self.__base_dir, my_file)), location.line)

    def _test_undefined(self, test_name, location):
        """
        Mark test as undefined
        :param test_name: name of test
        :type test_name str
        :param location its location

        """
        if test_name != self.__last_test_name:
            self._test_started(test_name, location)
        self._test_failed(test_name, message="Test undefined", details="Please define test")

    def _test_skipped(self, test_name, reason, location):
        """
        Mark test as skipped
        :param test_name: name of test
        :param reason: why test was skipped
        :type reason str
        :type test_name str
        :param location its location

        """
        if test_name != self.__last_test_name:
            self._test_started(test_name, location)
        self.tc_messages.testIgnored(test_name, "Skipped: {}".format(reason))
        self.__last_test_name = None
        pass

    def _test_failed(self, name, message, details):
        """
        Report test failure
        :param name: test name
        :type name str
        :param message: failure message
        :type message str
        :param details: failure details (probably stacktrace)
        :type details str
        """
        self.tc_messages.testFailed(name, message=message, details=details)
        self.__last_test_name = None

    def _test_passed(self, name, duration=None):
        """
        Reports test passed
        :param name: test name
        :type name str
        :param duration: time (in seconds) test took. Pass None if you do not know (we'll try to calculate it)
        :type duration int
        :return:
        """
        duration_to_report = duration
        if self.__last_test_start_time and not duration:  # And not provided
            duration_to_report = int(time.time() - self.__last_test_start_time)
        self.tc_messages.testFinished(name, duration=int(duration_to_report))
        self.__last_test_start_time = None
        self.__last_test_name = None

    def _test_started(self, name, location):
        """
        Reports test launched
        :param name: test name
        :param location object with "file" (relative to base_dir) and "line" fields.
        :type name str
        """
        self.__last_test_start_time = time.time()
        self.__last_test_name = name
        self.tc_messages.testStarted(name, self.__gen_location(location))

    def _feature_or_scenario(self, is_started, name, location):
        """
        Reports feature or scenario launched or stopped
        :param is_started: started or finished?
        :type is_started bool
        :param name: scenario or feature name
        :param location object with "file" (relative to base_dir) and "line" fields.
        """
        if is_started:
            self.tc_messages.testSuiteStarted(name, self.__gen_location(location))
        else:
            self.tc_messages.testSuiteFinished(name)

    def _background(self, is_started, location):
        """
        Reports background or stopped
        :param is_started: started or finished?
        :type is_started bool
        :param location object with "file" (relative to base_dir) and "line" fields.
        """
        self._feature_or_scenario(is_started, "Background", location)

    def _get_number_of_tests(self):
        """"
        Gets number of tests using "_get_features_to_run()" to obtain number of features to calculate.
        Supports backgrounds as well.
         :return number of steps
         :rtype int
        """
        num_of_steps = 0
        for feature in self._get_features_to_run():
            if feature.background:
                num_of_steps += len(feature.background.steps) * len(feature.scenarios)
            for scenario in feature.scenarios:
                num_of_steps += len(scenario.steps)
        return num_of_steps

    @abc.abstractmethod
    def _get_features_to_run(self):
        """
        Implement it! Return list of features to run. Each "feature" should have "scenarios".
         Each "scenario" should have "steps". Each "feature" may have "background" and each "background" should have
          "steps". Duck typing.
        :rtype list
        :returns list of features
        """
        return []

    @abc.abstractmethod
    def _run_tests(self):
        """
        Implement it! It should launch tests using your BDD. Use "self._" functions to report results.
        """
        pass


