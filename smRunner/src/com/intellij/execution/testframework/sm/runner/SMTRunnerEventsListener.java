package com.intellij.execution.testframework.sm.runner;

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
  void onTestsCountInSuite(int count);

  void onTestStarted(@NotNull SMTestProxy test);
  void onTestFinished(@NotNull SMTestProxy test);
  void onTestFailed(@NotNull SMTestProxy test);
  void onTestIgnored(@NotNull SMTestProxy test);

  void onSuiteFinished(@NotNull SMTestProxy suite);
  void onSuiteStarted(@NotNull SMTestProxy suite);
}
