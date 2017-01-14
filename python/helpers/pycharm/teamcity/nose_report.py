# coding=utf-8
import os
import sys
import datetime
import inspect

from teamcity import is_running_under_teamcity
from teamcity.common import is_string, split_output, limit_output, get_class_fullname, convert_error_to_string
from teamcity.messages import TeamcityServiceMessages

from nose.exc import SkipTest, DeprecatedTest


CONTEXT_SUITE_FQN = "nose.suite.ContextSuite"


# from nose.util.ln
def _ln(label):
    label_len = len(label) + 2
    chunk = (70 - label_len) // 2
    out = '%s %s %s' % ('-' * chunk, label, '-' * chunk)
    pad = 70 - len(out)
    if pad > 0:
        out = out + ('-' * pad)
    return out


_captured_output_start_marker = _ln('>> begin captured stdout <<') + "\n"
_captured_output_end_marker = "\n" + _ln('>> end captured stdout <<')

_real_stdout = sys.stdout


class TeamcityReport(object):
    name = 'teamcity-report'
    score = 10000

    def __init__(self):
        super(TeamcityReport, self).__init__()

        self.messages = TeamcityServiceMessages(_real_stdout)
        self.test_started_datetime_map = {}
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

    def options(self, parser, env=os.environ):
        pass

    def _get_capture_plugin(self, test):
        """
        :type test: nose.case.Test
        :rtype: nose.plugins.base.Plugin
        """
        for plugin in test.config.plugins.plugins:
            if plugin.name == "capture":
                return plugin
        return None

    def _capture_plugin_enabled(self, test):
        """
        :type test: nose.case.Test
        """
        plugin = self._get_capture_plugin(test)
        return plugin is not None and plugin.enabled

    def _capture_plugin_buffer(self, test):
        """
        :type test: nose.case.Test
        """
        plugin = self._get_capture_plugin(test)
        if plugin is None:
            return None
        return getattr(plugin, "buffer", None)

    def _captureStandardOutput_value(self, test):
        """
        :type test: nose.case.Test
        """
        if self._capture_plugin_enabled(test):
            return 'false'
        else:
            return 'true'

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

        self.messages.testFailed(test_id, message=fail_type, details=details, flowId=test_id)

    def report_finish(self, test):
        test_id = self.get_test_id(test)

        captured_output = getattr(test, "capturedOutput", None)
        if captured_output is None and self._capture_plugin_enabled(test):
            # nose capture does not fill 'capturedOutput' property on successful tests
            captured_output = self._capture_plugin_buffer(test)
        if captured_output:
            for chunk in split_output(limit_output(captured_output)):
                self.messages.testStdOut(test_id, chunk, flowId=test_id)

        if test_id in self.test_started_datetime_map:
            time_diff = datetime.datetime.now() - self.test_started_datetime_map[test_id]
            self.messages.testFinished(test_id, testDuration=time_diff, flowId=test_id)
        else:
            self.messages.testFinished(test_id, flowId=test_id)

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
            self.messages.testStarted(test_id, captureStandardOutput=self._captureStandardOutput_value(test), flowId=test_id)
            self.report_fail(test, 'error in ' + test.error_context + ' context', err)
            self.messages.testFinished(test_id, flowId=test_id)
        else:
            self.report_fail(test, 'Error', err)
            self.report_finish(test)

    def addFailure(self, test, err):
        self.report_fail(test, 'Failure', err)
        self.report_finish(test)

    def startTest(self, test):
        test_id = self.get_test_id(test)

        self.test_started_datetime_map[test_id] = datetime.datetime.now()
        self.messages.testStarted(test_id, captureStandardOutput=self._captureStandardOutput_value(test), flowId=test_id)

    def addSuccess(self, test):
        self.report_finish(test)
