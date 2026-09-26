// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.rt.testng;

import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

public class IDEATestNGTestListener implements ITestListener {
  private final IDEATestNGRemoteListener myListener;

  public IDEATestNGTestListener(IDEATestNGRemoteListener listener) {
    myListener = listener;
  }

  @Override
  public void onTestStart(ITestResult result) {
    myListener.onTestStart(result);
  }

  @Override
  public void onTestSuccess(ITestResult result) {
    myListener.onTestSuccess(result);
  }

  @Override
  public void onTestFailure(ITestResult result) {
    myListener.onTestFailure(result);
  }

  @Override
  public void onTestSkipped(ITestResult result) {
    myListener.onTestSkipped(result);
  }

  @Override
  public void onTestFailedButWithinSuccessPercentage(ITestResult result) {
    myListener.onTestFailedButWithinSuccessPercentage(result);
  }

  @Override
  public void onStart(ITestContext context) {
    myListener.onStart(context);
  }

  @Override
  public void onFinish(ITestContext context) {
    myListener.onFinish(context);
  }
}
