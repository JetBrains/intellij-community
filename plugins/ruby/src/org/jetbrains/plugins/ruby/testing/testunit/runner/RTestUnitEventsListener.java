package org.jetbrains.plugins.ruby.testing.testunit.runner;

import org.jetbrains.annotations.NotNull;

/**
 * @author Roman Chernyatchik
 *
 * Handles Test Runner events
*/
public interface RTestUnitEventsListener {
  /**
   * On start testing, before tests and suits launching
   */
  void onTestingStarted();
  /**
   * After test framework finish testing
   */
  void onTestingFinished();
  void onTestsCount(final int count);

  void onTestStarted(@NotNull final RTestUnitTestProxy test);
  void onTestFinished(@NotNull final RTestUnitTestProxy test);
  void onTestFailed(@NotNull final RTestUnitTestProxy test);

  void onSuiteFinished(@NotNull final RTestUnitTestProxy suite);
  void onSuiteStarted(@NotNull final RTestUnitTestProxy suite);
}
