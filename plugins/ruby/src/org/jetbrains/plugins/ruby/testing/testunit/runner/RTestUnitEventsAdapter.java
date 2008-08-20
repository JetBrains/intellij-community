package org.jetbrains.plugins.ruby.testing.testunit.runner;

import org.jetbrains.annotations.NotNull;

/**
 * @author Roman Chernyatchik
*/
public class RTestUnitEventsAdapter implements RTestUnitEventsListener {
  public void onTestingStarted(){}
  public void onTestingFinished(){}
  public void onTestsCountInSuite(final int count) {}

  public void onTestStarted(@NotNull final RTestUnitTestProxy test) {}
  public void onTestFinished(@NotNull final RTestUnitTestProxy test) {}
  public void onTestFailed(@NotNull final RTestUnitTestProxy test) {}
  public void onTestIgnored(@NotNull final RTestUnitTestProxy test) {}

  public void onSuiteStarted(@NotNull final RTestUnitTestProxy suite) {}
  public void onSuiteFinished(@NotNull final RTestUnitTestProxy suite) {}
}
