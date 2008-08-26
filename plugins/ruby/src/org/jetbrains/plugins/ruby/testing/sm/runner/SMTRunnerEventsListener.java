package org.jetbrains.plugins.ruby.testing.sm.runner;

import org.jetbrains.annotations.NotNull;

/**
 * @author Roman Chernyatchik
 *
 * Handles Test Runner events
*/
public interface SMTRunnerEventsListener {
  /**
   * On start testing, before tests and suits launching
   */
  void onTestingStarted();
  /**
   * After test framework finish testing
   */
  void onTestingFinished();
  /*
   * Tests count in next suite. For several suites this method will be invoked several times
   */
  void onTestsCountInSuite(final int count);

  void onTestStarted(@NotNull final SMTestProxy test);
  void onTestFinished(@NotNull final SMTestProxy test);
  void onTestFailed(@NotNull final SMTestProxy test);
  void onTestIgnored(@NotNull final SMTestProxy test);

  void onSuiteFinished(@NotNull final SMTestProxy suite);
  void onSuiteStarted(@NotNull final SMTestProxy suite);
}
