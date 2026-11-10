import sys
import unittest

import six

from tcunittest import TeamcityTestResult
from testing import HelpersTestCase
from testing import python3_only


def _sample_cases():
    """The classes are local, so that the test discovery does not run them."""

    class SubTests(unittest.TestCase):
        def test_values(self):
            for value in (1, 2):
                with self.subTest(value=value):
                    self.assertEqual(value, 1)

    class ExpectedFailureTests(unittest.TestCase):
        @unittest.expectedFailure
        def test_fixed(self):
            self.assertTrue(True)

        @unittest.expectedFailure
        def test_broken(self):
            self.assertTrue(False)

    class PassingTests(unittest.TestCase):
        def test_one(self):
            pass

        def test_two(self):
            pass

    class OutputTests(unittest.TestCase):
        def test_output(self):
            sys.stdout.write("captured output\n")
            self.fail("boom")

    return SubTests, ExpectedFailureTests, PassingTests, OutputTests


# PY-93717
class TeamcityTestResultTest(HelpersTestCase):
    def setUp(self):
        super(TeamcityTestResultTest, self).setUp()
        self.SubTests, self.ExpectedFailureTests, self.PassingTests, self.OutputTests = _sample_cases()

    @staticmethod
    def run_tests(tests, **result_options):
        output = six.StringIO()
        result = TeamcityTestResult(output, **result_options)
        unittest.TestSuite(tests)(result)
        result.endLastSuite()
        return result, output.getvalue()

    def assert_message(self, output, message_name, **attributes):
        for line in output.splitlines():
            if line.startswith("##teamcity[" + message_name + " ") and all(
                    " {}='{}".format(key, value) in line for key, value in attributes.items()):
                return
        self.fail("No {} message with {} in:\n{}".format(message_name, attributes, output))

    @python3_only
    def test_failed_subtest_fails_result(self):
        result, output = self.run_tests([self.SubTests("test_values")])

        self.assertFalse(result.wasSuccessful())
        self.assertEqual(len(result.failures), 1)
        self.assertEqual(result.testsRun, 1)
        self.assert_message(output, "testFinished", name="test_values (value=1)")
        self.assert_message(output, "testFailed", name="test_values (value=2)", message="Failure")

    def test_unexpected_success_is_failure(self):
        result, output = self.run_tests([self.ExpectedFailureTests("test_fixed")])

        self.assertEqual(len(result.unexpectedSuccesses), 1)
        if six.PY3:
            self.assertFalse(result.wasSuccessful())
        self.assert_message(output, "testStarted", name="test_fixed")
        self.assert_message(output, "testFailed", name="test_fixed", message="Unexpected success")

    def test_expected_failure_is_ignored(self):
        result, output = self.run_tests([self.ExpectedFailureTests("test_broken")])

        self.assertTrue(result.wasSuccessful())
        self.assertEqual(len(result.expectedFailures), 1)
        self.assert_message(output, "testIgnored", name="test_broken", message="Expected failure: ")
        self.assertNotIn("testFailed", output)

    def test_tests_run(self):
        result, output = self.run_tests([self.PassingTests("test_one"), self.PassingTests("test_two")])

        self.assertTrue(result.wasSuccessful())
        self.assertEqual(result.testsRun, 2)
        self.assert_message(output, "testFinished", name="test_one")
        self.assert_message(output, "testFinished", name="test_two")

    @python3_only
    def test_failfast_stops_after_failed_subtest(self):
        result, output = self.run_tests([self.SubTests("test_values"), self.PassingTests("test_one")], failfast=True)

        self.assertTrue(result.shouldStop)
        self.assertEqual(result.testsRun, 1)
        self.assertNotIn("name='test_one'", output)

    def test_buffered_output_only_in_failure_details(self):
        original_stdout, original_stderr = sys.stdout, sys.stderr
        stdout, stderr = six.StringIO(), six.StringIO()
        sys.stdout, sys.stderr = stdout, stderr
        try:
            result, output = self.run_tests([self.OutputTests("test_output")], buffer=True)
            stdout_after_run = sys.stdout
        finally:
            sys.stdout, sys.stderr = original_stdout, original_stderr

        self.assertIs(stdout_after_run, stdout)
        self.assertEqual(len(result.failures), 1)
        self.assert_message(output, "testFailed", name="test_output")
        self.assertIn("Stdout:|ncaptured output", output)
        self.assertNotIn("captured output", stdout.getvalue())
        self.assertNotIn("captured output", stderr.getvalue())
