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

  public void onTestStart(ITestResult result) {
    myListener.onTestStart(result);
  }

  public void onTestSuccess(ITestResult result) {
    myListener.onTestSuccess(result);
  }

  public void onTestFailure(ITestResult result) {
    myListener.onTestFailure(result);
  }

  public void onTestSkipped(ITestResult result) {
    myListener.onTestSkipped(result);
  }

  public void onTestFailedButWithinSuccessPercentage(ITestResult result) {
    myListener.onTestFailedButWithinSuccessPercentage(result);
  }

  public void onStart(ITestContext context) {
    myListener.onStart(context);
  }

  public void onFinish(ITestContext context) {
    myListener.onFinish(context);
  }
}
