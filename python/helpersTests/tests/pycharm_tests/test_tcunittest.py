import sys
import unittest

import six

from tcunittest import TeamcityTestResult, class_level_error_suite, strclass
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


def _class_level_error_tests():
    """The classes are local, so that the test discovery does not run them."""

    class SetUpClassErrorTests(unittest.TestCase):
        @classmethod
        def setUpClass(cls):
            raise ValueError("broken test data")

        def test_one(self):
            pass

    class TearDownClassErrorTests(unittest.TestCase):
        @classmethod
        def tearDownClass(cls):
            raise ValueError("broken cleanup")

        def test_one(self):
            pass

    # unittest names a class by its qualified name, and the helpers by its name. A module-level class has equal names.
    for test_class in (SetUpClassErrorTests, TearDownClassErrorTests):
        test_class.__qualname__ = test_class.__name__
    return SetUpClassErrorTests, TearDownClassErrorTests


class _NamedTest(object):
    """A test that is not a ``TestCase`` and has a description with no space."""

    def __str__(self):
        return "doctest_name"


class _DjangoLikeTestResult(TeamcityTestResult):
    """Gives the suite of a class-level error as ``DjangoTeamcityTestResult`` does. The helper tests have no Django."""

    def _getSuite(self, test):
        return class_level_error_suite(test, "django_testid://") or super(_DjangoLikeTestResult, self)._getSuite(test)


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


# PY-93718
class ClassLevelErrorTest(HelpersTestCase):
    def setUp(self):
        super(ClassLevelErrorTest, self).setUp()
        self.SetUpClassErrorTests, self.TearDownClassErrorTests = _class_level_error_tests()

    @staticmethod
    def run_tests(tests):
        output = six.StringIO()
        result = _DjangoLikeTestResult(output)
        unittest.TestSuite(tests)(result)
        result.endLastSuite()
        return result, output.getvalue()

    def test_set_up_class_error_is_reported_under_the_suite_of_its_class(self):
        result, output = self.run_tests([self.SetUpClassErrorTests("test_one")])
        class_id = strclass(self.SetUpClassErrorTests)

        self.assertEqual(len(result.errors), 1)
        self.assertIn("##teamcity[testSuiteStarted name='{0}' locationHint='django_testid://{0}'".format(class_id), output)
        self.assertIn("##teamcity[testStarted name='setUpClass' locationHint='django_testid://{0}'".format(class_id), output)
        self.assertIn("##teamcity[testFailed name='setUpClass'", output)
        self.assertNotIn("_ErrorHolder", output)

    def test_tear_down_class_error_stays_in_the_suite_of_its_class(self):
        result, output = self.run_tests([self.TearDownClassErrorTests("test_one")])
        class_id = strclass(self.TearDownClassErrorTests)

        self.assertEqual(len(result.errors), 1)
        self.assertEqual(output.count("##teamcity[testSuiteStarted "), 1)
        self.assertIn("##teamcity[testSuiteStarted name='{0}'".format(class_id), output)
        self.assertIn("##teamcity[testFinished name='test_one'", output)
        self.assertIn("##teamcity[testStarted name='tearDownClass' locationHint='django_testid://{0}'".format(class_id), output)
        self.assertIn("##teamcity[testFailed name='tearDownClass'", output)

    def test_set_up_module_error_has_the_module_as_suite_and_location(self):
        holder = unittest.suite._ErrorHolder("setUpModule (app.tests)")

        self.assertEqual(class_level_error_suite(holder, "django_testid://"),
                         ("app.tests", "django_testid://app.tests", "django_testid://app.tests"))

    def test_test_method_is_not_a_class_level_error(self):
        self.assertIsNone(class_level_error_suite(self.SetUpClassErrorTests("test_one"), "django_testid://"))

    def test_object_with_other_description_is_not_a_class_level_error(self):
        self.assertIsNone(class_level_error_suite(_NamedTest(), "django_testid://"))

    def test_test_name_without_space_is_the_whole_name(self):
        result = TeamcityTestResult(six.StringIO())

        self.assertEqual(result.getTestName(_NamedTest()), "doctest_name")
