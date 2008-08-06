package org.jetbrains.plugins.ruby.testing.testunit.runner;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.Disposable;

/**
 * @author: Roman Chernyatchik
 *
 * Processes events of test runner in general text-based form
 */
public interface GeneralTestEventsProcessor extends Disposable {
  void onTestsCountInSuite(final int count);

  void onTestStarted(final String testName);
  void onTestFinished(final String testName);
  void onTestFailure(final String testName, final String localizedMessage, final String stackTrace);
  void onTestOutput(final String testName, final String text, final boolean stdOut);

  void onSuiteStarted(final String suiteName);
  void onSuiteFinished(final String suiteName);

  void onUncapturedOutput(final String text, final Key outputType);
}