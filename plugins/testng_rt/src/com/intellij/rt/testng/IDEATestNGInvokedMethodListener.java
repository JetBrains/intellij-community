// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.rt.testng;

import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestResult;

public class IDEATestNGInvokedMethodListener implements IInvokedMethodListener {
  private final IDEATestNGRemoteListener myListener;

  public IDEATestNGInvokedMethodListener(IDEATestNGRemoteListener listener) {
    myListener = listener;
  }

  @Override
  public void beforeInvocation(IInvokedMethod method, ITestResult testResult) {
    synchronized (myListener) {
      if (!testResult.getMethod().isTest()) {
        myListener.onConfigurationStart(myListener.createDelegated(testResult));
      }
    }
  }

  //should be covered by test listeners
  @Override
  public void afterInvocation(IInvokedMethod method, ITestResult testResult) {}
}
