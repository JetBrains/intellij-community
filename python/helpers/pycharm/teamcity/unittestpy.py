# coding=utf-8
import sys
from unittest import TestResult, TextTestRunner
import datetime
import re

from teamcity.messages import TeamcityServiceMessages
from teamcity.common import is_string, get_class_fullname, convert_error_to_string, limit_output, split_output

_real_stdout = sys.stdout


class TeamcityTestResult(TestResult):
    separator2 = "\n"

    # noinspection PyUnusedLocal
    def __init__(self, stream=_real_stdout, descriptions=None, verbosity=None):
        super(TeamcityTestResult, self).__init__()

        # Some code may ask for self.failfast, see unittest2.case.TestCase.subTest
        self.failfast = getattr(self, "failfast", False)

        self.test_started_datetime_map = {}
        self.failed_tests = set()
        self.subtest_failures = {}
        self.messages = TeamcityServiceMessages(_real_stdout)

    @staticmethod
    def get_test_id(test):
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
        _super = super(TeamcityTestResult, self)
        if hasattr(_super, "addExpectedFailure"):
            _super.addExpectedFailure(test, err)

        err = convert_error_to_string(err)
        test_id = self.get_test_id(test)

        self.messages.testIgnored(test_id, message="Expected failure: " + err, flowId=test_id)

    def get_subtest_block_id(self, test, subtest):
        test_id = self.get_test_id(test)
        subtest_id = self.get_test_id(subtest)

        if subtest_id.startswith(test_id):
            block_id = subtest_id[len(test_id):].strip()
        else:
            block_id = subtest_id
        if len(block_id) == 0:
            block_id = test_id
        return block_id

    def addSkip(self, test, reason=""):
        if sys.version_info >= (2, 7):
            super(TeamcityTestResult, self).addSkip(test, reason)

        if reason:
            reason_str = ": " + str(reason)
        else:
            reason_str = ""

        test_class_name = get_class_fullname(test)
        if test_class_name == "unittest.case._SubTest" or test_class_name == "unittest2.case._SubTest":
            parent_test = test.test_case
            parent_test_id = self.get_test_id(parent_test)
            subtest = test

            block_id = self.get_subtest_block_id(parent_test, subtest)

            self.messages.subTestBlockOpened(block_id, subTestResult="Skip", flowId=parent_test_id)
            self.messages.testStdOut(parent_test_id, out="SubTest skipped" + reason_str + "\n", flowId=parent_test_id)
            self.messages.blockClosed(block_id, flowId=parent_test_id)
        else:
            test_id = self.get_test_id(test)
            self.messages.testIgnored(test_id, message="Skipped" + reason_str, flowId=test_id)

    def addUnexpectedSuccess(self, test):
        _super = super(TeamcityTestResult, self)
        if hasattr(_super, "addUnexpectedSuccess"):
            _super.addUnexpectedSuccess(test)

        test_id = self.get_test_id(test)
        self.messages.testFailed(test_id, message='Failure',
                                 details="Test should not succeed since it's marked with @unittest.expectedFailure",
                                 flowId=test_id)

    def addError(self, test, err, *k):
        super(TeamcityTestResult, self).addError(test, err)

        test_class = get_class_fullname(test)
        if test_class == "unittest.suite._ErrorHolder" or test_class == "unittest2.suite._ErrorHolder":
            # This is a standalone error

            test_name = test.id()
            # patch setUpModule (__main__) -> __main__.setUpModule
            test_name = re.sub(r'^(.*) \((.*)\)$', r'\2.\1', test_name)

            self.messages.testStarted(test_name, flowId=test_name)
            # noinspection PyTypeChecker
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
        _super = super(TeamcityTestResult, self)
        if hasattr(_super, "addSubTest"):
            _super.addSubTest(test, subtest, err)

        test_id = self.get_test_id(test)
        subtest_id = self.get_test_id(subtest)

        if subtest_id.startswith(test_id):
            # Replace "." -> "_" since '.' is a test hierarchy separator
            # See i.e. https://github.com/JetBrains/teamcity-messages/issues/134 (https://youtrack.jetbrains.com/issue/PY-23846)
            block_id = subtest_id[len(test_id):].strip().replace(".", "_")
        else:
            block_id = subtest_id
        if len(block_id) == 0:
            block_id = subtest_id

        if err is not None:
            self.add_subtest_failure(test_id, block_id)

            if issubclass(err[0], test.failureException):
                self.messages.subTestBlockOpened(block_id, subTestResult="Failure", flowId=test_id)
                self.messages.testStdErr(test_id, out="SubTest failure: %s\n" % convert_error_to_string(err), flowId=test_id)
                self.messages.blockClosed(block_id, flowId=test_id)
            else:
                self.messages.subTestBlockOpened(block_id, subTestResult="Error", flowId=test_id)
                self.messages.testStdErr(test_id, out="SubTest error: %s\n" % convert_error_to_string(err), flowId=test_id)
                self.messages.blockClosed(block_id, flowId=test_id)
        else:
            self.messages.subTestBlockOpened(block_id, subTestResult="Success", flowId=test_id)
            self.messages.blockClosed(block_id, flowId=test_id)

    def add_subtest_failure(self, test_id, subtest_block_id):
        fail_array = self.subtest_failures.get(test_id, [])
        fail_array.append(subtest_block_id)
        self.subtest_failures[test_id] = fail_array

    def get_subtest_failure(self, test_id):
        fail_array = self.subtest_failures.get(test_id, [])
        return ", ".join(fail_array)

    def report_fail(self, test, fail_type, err):
        test_id = self.get_test_id(test)

        if is_string(err):
            details = err
        elif get_class_fullname(err) == "twisted.python.failure.Failure":
            details = err.getTraceback()
        else:
            details = convert_error_to_string(err)

        subtest_failures = self.get_subtest_failure(test_id)
        if subtest_failures:
            details = "Failed subtests list: " + subtest_failures + "\n\n" + details.strip()
            details = details.strip()

        self.messages.testFailed(test_id, message=fail_type, details=details, flowId=test_id)
        self.failed_tests.add(test_id)

    def startTest(self, test):
        super(TeamcityTestResult, self).startTest(test)

        test_id = self.get_test_id(test)

        self.test_started_datetime_map[test_id] = datetime.datetime.now()
        self.messages.testStarted(test_id, captureStandardOutput='true', flowId=test_id)

    def stopTest(self, test):
        test_id = self.get_test_id(test)

        if getattr(self, 'buffer', None):
            # Do not allow super() method to print output by itself
            self._mirrorOutput = False

            output = sys.stdout.getvalue()
            if output:
                for chunk in split_output(limit_output(output)):
                    self.messages.testStdOut(test_id, chunk, flowId=test_id)

            error = sys.stderr.getvalue()
            if error:
                for chunk in split_output(limit_output(error)):
                    self.messages.testStdErr(test_id, chunk, flowId=test_id)

        super(TeamcityTestResult, self).stopTest(test)

        if test_id not in self.failed_tests:
            subtest_failures = self.get_subtest_failure(test_id)
            if subtest_failures:
                self.report_fail(test, "One or more subtests failed", "")

        time_diff = datetime.datetime.now() - self.test_started_datetime_map[test_id]
        self.messages.testFinished(test_id, testDuration=time_diff, flowId=test_id)

    def printErrors(self):
        pass


class TeamcityTestRunner(TextTestRunner):
    resultclass = TeamcityTestResult

    if sys.version_info < (2, 7):
        def _makeResult(self):
            return TeamcityTestResult(self.stream, self.descriptions, self.verbosity)

    def run(self, test):
        # noinspection PyBroadException
        try:
            total_tests = test.countTestCases()
            TeamcityServiceMessages(_real_stdout).testCount(total_tests)
        except:
            pass

        return super(TeamcityTestRunner, self).run(test)


if __name__ == '__main__':
    from unittest import main

    main(module=None, testRunner=TeamcityTestRunner())
