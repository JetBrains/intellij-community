// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.rt.testng;

import com.intellij.execution.TestDiscoveryListener;
import com.intellij.rt.execution.junit.ComparisonFailureData;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestContext;
import org.testng.ITestResult;

public class TestNGTestDiscoveryListener extends TestDiscoveryListener implements IDEATestNGListener, ISuiteListener {
  @Override
  public void onTestStart(ITestResult result) {
    testStarted(result.getTestClass().getName(), result.getTestName());
  }

  @Override
  public void onTestSuccess(ITestResult result) {
    testFinished(result.getTestClass().getName(), result.getName(), true);
  }

  @Override
  public void onTestFailure(ITestResult result) {
    testFinished(result.getTestClass().getName(), result.getName(), ComparisonFailureData.isAssertionError(result.getThrowable().getClass()));
  }
  @Override
  public void onTestSkipped(ITestResult result) {
    testFinished(result.getTestClass().getName(), result.getName(), false);
  }
  @Override
  public void onTestFailedButWithinSuccessPercentage(ITestResult result) {
    testFinished(result.getTestClass().getName(), result.getName(), true);
  }

  @Override
  public void onStart(ITestContext context) {}

  @Override
  public void onFinish(ITestContext context) {}

  @Override
  public void onStart(ISuite suite) {
    testRunStarted(suite.getName());
  }

  @Override
  public void onFinish(ISuite suite) {
    testRunFinished(suite.getName());
  }

  @Override
  public String getFrameworkId() {
    return "g";
  }
}
