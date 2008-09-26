package org.jetbrains.plugins.ruby.testing.sm.runner;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.Nullable;

/**
 * @author: Roman Chernyatchik
 *
 * Processes events of test runner in general text-based form
 *
 * NB: Test name should be unique for all suites. E.g. it can consist of suite name
 * and name of test method
 */
public interface GeneralTestEventsProcessor extends Disposable {
  void onTestsCountInSuite(final int count);

  void onTestStarted(final String testName, @Nullable final String locationUrl);
  void onTestFinished(final String testName, final int duration);
  void onTestFailure(final String testName, final String localizedMessage, final String stackTrace,
                     final boolean testError);
  void onTestIgnored(final String testName, final String ignoreComment);
  void onTestOutput(final String testName, final String text, final boolean stdOut);

  void onSuiteStarted(final String suiteName, @Nullable final String locationUrl);
  void onSuiteFinished(final String suiteName);

  void onUncapturedOutput(final String text, final Key outputType);
}