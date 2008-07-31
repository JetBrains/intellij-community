package org.jetbrains.plugins.ruby.testing.testunit.runner;

/**
 * @author: Roman Chernyatchik
 *
 * Processes events of test runner in general text-based form
 */
public interface GeneralTestEventsProcessor {
  void onTestsCount(final int count);

  void onTestStart(final String testName);
  void onTestFinish(final String testName);
  void onTestFailure(final String testName, final String localizedMessage, final String stackTrace);
  void onTestOutput(final String testName, final String text, final boolean stdOut);

  void onTestSuiteStart(final String suiteName);
  void onTestSuiteFinish(final String suiteName);
}