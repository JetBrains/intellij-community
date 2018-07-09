# coding=utf-8
import os
import sys
import datetime
import inspect

from teamcity import is_running_under_teamcity
from teamcity.common import is_string, get_class_fullname, convert_error_to_string, dump_test_stdout, FlushingStringIO
from teamcity.messages import TeamcityServiceMessages
from .diff_tools import EqualsAssertionError, patch_unittest_diff

import nose
# noinspection PyPackageRequirements
from nose.exc import SkipTest, DeprecatedTest
# noinspection PyPackageRequirements
from nose.plugins import Plugin

patch_unittest_diff()

CONTEXT_SUITE_FQN = "nose.suite.ContextSuite"


# from nose.util.ln
def _ln(label):
    label_len = len(label) + 2
    chunk = (70 - label_len) // 2
    out = '%s %s %s' % ('-' * chunk, label, '-' * chunk)
    pad = 70 - len(out)
    if pad > 0:
        out += '-' * pad
    return out


_captured_output_start_marker = _ln('>> begin captured stdout <<') + "\n"
_captured_output_end_marker = "\n" + _ln('>> end captured stdout <<')

_real_stdout = sys.stdout


# noinspection PyPep8Naming,PyMethodMayBeStatic
class TeamcityReport(Plugin):
    name = 'teamcity-report'
    score = 10000

    def __init__(self):
        super(TeamcityReport, self).__init__()

        self.messages = TeamcityServiceMessages(_real_stdout)
        self.test_started_datetime_map = {}
        self.config = None
        self.total_tests = 0
        self.enabled = False

    def get_test_id(self, test):
        if is_string(test):
            return test

        # Handle special "tests"
        test_class_name = get_class_fullname(test)
        if test_class_name == CONTEXT_SUITE_FQN:
            if inspect.ismodule(test.context):
                module_name = test.context.__name__
                return module_name + "." + test.error_context
            elif inspect.isclass(test.context):
                class_name = get_class_fullname(test.context)
                return class_name + "." + test.error_context

        test_id = test.id()

        real_test = getattr(test, "test", test)
        real_test_class_name = get_class_fullname(real_test)

        test_arg = getattr(real_test, "arg", tuple())
        if (type(test_arg) is tuple or type(test_arg) is list) and len(test_arg) > 0:
            # As written in nose.case.FunctionTestCase#__str__ or nose.case.MethodTestCase#__str__
            test_arg_str = "%s" % (test_arg,)
            if test_id.endswith(test_arg_str):
                # Replace '.' in test args with '_' to preserve test hierarchy on TeamCity
                test_id = test_id[:len(test_id) - len(test_arg_str)] + test_arg_str.replace('.', '_')

        # Force test_id for doctests
        if real_test_class_name != "doctest.DocTestCase" and real_test_class_name != "nose.plugins.doctests.DocTestCase":
            desc = test.shortDescription()
            if desc and desc != test.id():
                return "%s (%s)" % (test_id, desc.replace('.', '_'))

        return test_id

    def configure(self, options, conf):
        self.enabled = is_running_under_teamcity()
        self.config = conf

        if self._capture_plugin_enabled():
            capture_plugin = self._get_capture_plugin()

            old_before_test = capture_plugin.beforeTest
            old_after_test = capture_plugin.afterTest
            old_format_error = capture_plugin.formatError

            def newCaptureBeforeTest(test):
                rv = old_before_test(test)
                test_id = self.get_test_id(test)
                capture_plugin._buf = FlushingStringIO(lambda data: dump_test_stdout(self.messages, test_id, test_id, data))
                sys.stdout = capture_plugin._buf
                return rv

            def newCaptureAfterTest(test):
                if isinstance(capture_plugin._buf, FlushingStringIO):
                    capture_plugin._buf.flush()
                return old_after_test(test)

            def newCaptureFormatError(test, err):
                if isinstance(capture_plugin._buf, FlushingStringIO):
                    capture_plugin._buf.flush()
                return old_format_error(test, err)

            capture_plugin.beforeTest = newCaptureBeforeTest
            capture_plugin.afterTest = newCaptureAfterTest
            capture_plugin.formatError = newCaptureFormatError

    def options(self, parser, env=os.environ):
        pass

    def _get_capture_plugin(self):
        """
        :rtype: nose.plugins.capture.Capture
        """
        for plugin in self.config.plugins.plugins:
            if plugin.name == "capture":
                return plugin
        return None

    def _capture_plugin_enabled(self):
        plugin = self._get_capture_plugin()
        return plugin is not None and plugin.enabled

    def _capture_plugin_buffer(self):
        plugin = self._get_capture_plugin()
        if plugin is None:
            return None
        return getattr(plugin, "buffer", None)

    def _captureStandardOutput_value(self):
        if self._capture_plugin_enabled():
            return 'false'
        else:
            return 'true'

    def report_started(self, test):
        test_id = self.get_test_id(test)

        self.test_started_datetime_map[test_id] = datetime.datetime.now()
        self.messages.testStarted(test_id, captureStandardOutput=self._captureStandardOutput_value(), flowId=test_id)

    def report_fail(self, test, fail_type, err):
        # workaround nose bug on python 3
        if is_string(err[1]):
            err = (err[0], Exception(err[1]), err[2])

        test_id = self.get_test_id(test)

        details = convert_error_to_string(err)

        start_index = details.find(_captured_output_start_marker)
        end_index = details.find(_captured_output_end_marker)

        if 0 <= start_index < end_index:
            # do not log test output twice, see report_finish for actual output handling
            details = details[:start_index] + details[end_index + len(_captured_output_end_marker):]

        try:
            error = err[1]
            if isinstance(error, EqualsAssertionError):
                details = convert_error_to_string(err, 2)
                self.messages.testFailed(test_id, message=error.msg, details=details, flowId=test_id, comparison_failure=error)
                return
        except Exception:
            pass
        self.messages.testFailed(test_id, message=fail_type, details=details, flowId=test_id)

    def report_finish(self, test):
        test_id = self.get_test_id(test)

        if test_id in self.test_started_datetime_map:
            time_diff = datetime.datetime.now() - self.test_started_datetime_map[test_id]
            self.messages.testFinished(test_id, testDuration=time_diff, flowId=test_id)
        else:
            self.messages.testFinished(test_id, flowId=test_id)

    def prepareTestLoader(self, loader):
        """Insert ourselves into loader calls to count tests.
        The top-level loader call often returns lazy results, like a LazySuite.
        This is a problem, as we would destroy the suite by iterating over it
        to count the tests. Consequently, we monkey-patch the top-level loader
        call to do the load twice: once for the actual test running and again
        to yield something we can iterate over to do the count.

        from https://github.com/erikrose/nose-progressive/
        :type loader: nose.loader.TestLoader
        """

        # TODO: If there's ever a practical need, also patch loader.suiteClass
        # or even TestProgram.createTests. createTests seems to be main top-
        # level caller of loader methods, and nose.core.collector() (which
        # isn't even called in nose) is an alternate one.
        #
        # nose 1.3.4 contains required fix:
        # Another fix for Python 3.4: Call super in LazySuite to access _removed_tests variable
        if hasattr(loader, 'loadTestsFromNames') and nose.__versioninfo__ >= (1, 3, 4):
            old_loadTestsFromNames = loader.loadTestsFromNames

            def _loadTestsFromNames(*args, **kwargs):
                suite = old_loadTestsFromNames(*args, **kwargs)
                self.total_tests += suite.countTestCases()

                # Clear out the loader's cache. Otherwise, it never finds any tests
                # for the actual test run:
                loader._visitedPaths = set()

                return old_loadTestsFromNames(*args, **kwargs)
            loader.loadTestsFromNames = _loadTestsFromNames

    # noinspection PyUnusedLocal
    def prepareTestRunner(self, runner):
        if self.total_tests:
            self.messages.testCount(self.total_tests)

    def addError(self, test, err):
        test_class_name = get_class_fullname(test)
        test_id = self.get_test_id(test)

        if issubclass(err[0], SkipTest):
            self.messages.testIgnored(test_id, message=("SKIPPED: %s" % str(err[1])), flowId=test_id)
            self.report_finish(test)
        elif issubclass(err[0], DeprecatedTest):
            self.messages.testIgnored(test_id, message="Deprecated", flowId=test_id)
            self.report_finish(test)
        elif test_class_name == CONTEXT_SUITE_FQN:
            self.messages.testStarted(test_id, captureStandardOutput=self._captureStandardOutput_value(), flowId=test_id)
            self.report_fail(test, 'error in ' + test.error_context + ' context', err)
            self.messages.testFinished(test_id, flowId=test_id)
        else:
            # some test cases may report errors in pre setup when startTest was not called yet
            # example: https://github.com/JetBrains/teamcity-messages/issues/153
            if test_id not in self.test_started_datetime_map:
                self.report_started(test)
            self.report_fail(test, 'Error', err)
            self.report_finish(test)

    def addFailure(self, test, err):
        self.report_fail(test, 'Failure', err)
        self.report_finish(test)

    def startTest(self, test):
        test_id = self.get_test_id(test)

        self.test_started_datetime_map[test_id] = datetime.datetime.now()
        self.messages.testStarted(test_id, captureStandardOutput=self._captureStandardOutput_value(), flowId=test_id)

    def addSuccess(self, test):
        self.report_finish(test)
