# coding=utf-8
import sys
from unittest import TestResult, TextTestRunner
import datetime
import re

from teamcity.messages import TeamcityServiceMessages
from teamcity.common import is_string, get_class_fullname, convert_error_to_string

_real_stdout = sys.stdout


class TeamcityTestResult(TestResult):
    separator2 = "\n"

    def __init__(self, stream=_real_stdout, descriptions=None, verbosity=None):
        super(TeamcityTestResult, self).__init__()

        self.test_started_datetime_map = {}
        self.failed_tests = set()
        self.subtest_failures = {}
        self.messages = TeamcityServiceMessages(stream)

    def get_test_id(self, test):
        if is_string(test):
            return test

        # Force test_id for doctests
        if get_class_fullname(test) != "doctest.DocTestCase":
            desc = test.shortDescription()
            test_method_name = getattr(test, "_testMethodName", "")
            if desc and desc != test.id() and desc != test_method_name:
                return "%s (%s)" % (test.id(), desc.replace('.', '_'))

        return test.id()

    def addSuccess(self, test):
        super(TeamcityTestResult, self).addSuccess(test)

    def addExpectedFailure(self, test, err):
        super(TeamcityTestResult, self).addExpectedFailure(test, err)

        err = convert_error_to_string(err)
        test_id = self.get_test_id(test)

        self.messages.testIgnored(test_id, message="Expected failure: " + err, flowId=test_id)

    def addSkip(self, test, reason=""):
        if sys.version_info >= (2, 7):
            super(TeamcityTestResult, self).addSkip(test, reason)

        test_id = self.get_test_id(test)

        if reason:
            reason_str = ": " + str(reason)
        else:
            reason_str = ""
        self.messages.testIgnored(test_id, message="Skipped" + reason_str, flowId=test_id)

    def addUnexpectedSuccess(self, test):
        super(TeamcityTestResult, self).addUnexpectedSuccess(test)

        test_id = self.get_test_id(test)
        self.messages.testFailed(test_id, message='Failure',
                                 details="Test should not succeed since it's marked with @unittest.expectedFailure",
                                 flowId=test_id)

    def addError(self, test, err, *k):
        super(TeamcityTestResult, self).addError(test, err)

        if get_class_fullname(test) == "unittest.suite._ErrorHolder":
            # This is a standalone error

            test_name = test.id()
            # patch setUpModule (__main__) -> __main__.setUpModule
            test_name = re.sub(r'^(.*) \((.*)\)$', r'\2.\1', test_name)

            self.messages.testStarted(test_name, flowId=test_name)
            self.report_fail(test_name, 'Failure', err)
            self.messages.testFinished(test_name, flowId=test_name)
        elif get_class_fullname(err[0]) == "unittest2.case.SkipTest":
            message = ""
            if hasattr(err[1], "message"):
                message = getattr(err[1], "message", "")
            elif hasattr(err[1], "args"):
                message = getattr(err[1], "args", [""])[0]
            self.addSkip(test, message)
        else:
            self.report_fail(test, 'Error', err)

    def addFailure(self, test, err, *k):
        super(TeamcityTestResult, self).addFailure(test, err)

        self.report_fail(test, 'Failure', err)

    def addSubTest(self, test, subtest, err):
        super(TeamcityTestResult, self).addSubTest(test, subtest, err)

        test_id = self.get_test_id(test)

        if err is not None:
            if issubclass(err[0], test.failureException):
                self.add_subtest_failure(test_id, self.get_test_id(subtest), err)
                self.messages.testStdErr(test_id, out="%s: failure\n" % self.get_test_id(subtest), flowId=test_id)
            else:
                self.add_subtest_failure(test_id, self.get_test_id(subtest), err)
                self.messages.testStdErr(test_id, out="%s: error\n" % self.get_test_id(subtest), flowId=test_id)
        else:
            self.messages.testStdOut(test_id, out="%s: ok\n" % self.get_test_id(subtest), flowId=test_id)

    def add_subtest_failure(self, test_id, subtest_id, err):
        fail_array = self.subtest_failures.get(test_id, [])
        fail_array.append("%s:\n%s" % (subtest_id, convert_error_to_string(err)))
        self.subtest_failures[test_id] = fail_array

    def get_subtest_failure(self, test_id):
        fail_array = self.subtest_failures.get(test_id, [])
        return "\n".join(fail_array)

    def report_fail(self, test, fail_type, err):
        test_id = self.get_test_id(test)

        if is_string(err):
            details = err
        elif get_class_fullname(err) == "twisted.python.failure.Failure":
            details = err.getTraceback()
        else:
            details = convert_error_to_string(err)

        subtest_failure = self.get_subtest_failure(test_id)
        if subtest_failure:
            details = subtest_failure + "\n" + details

        self.messages.testFailed(test_id, message=fail_type, details=details, flowId=test_id)
        self.failed_tests.add(test_id)

    def startTest(self, test):
        super(TeamcityTestResult, self).startTest(test)

        test_id = self.get_test_id(test)

        self.test_started_datetime_map[test_id] = datetime.datetime.now()
        self.messages.testStarted(test_id, captureStandardOutput='true', flowId=test_id)

    def stopTest(self, test):
        super(TeamcityTestResult, self).stopTest(test)

        test_id = self.get_test_id(test)

        if test_id not in self.failed_tests and self.subtest_failures.get(test_id, []):
            self.report_fail(test, "Subtest failed", "")

        time_diff = datetime.datetime.now() - self.test_started_datetime_map[test_id]
        self.messages.testFinished(test_id, testDuration=time_diff, flowId=test_id)

    def printErrors(self):
        pass


class TeamcityTestRunner(TextTestRunner):
    resultclass = TeamcityTestResult

    if sys.version_info < (2, 7):
        def _makeResult(self):
            return TeamcityTestResult(self.stream, self.descriptions, self.verbosity)


if __name__ == '__main__':
    from unittest import main

    main(module=None, testRunner=TeamcityTestRunner())
