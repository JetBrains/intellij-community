// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.rt.testng;

import org.testng.IConfigurationListener;
import org.testng.ITestResult;

public class IDEATestNGConfigurationListener implements IConfigurationListener {
  private final IDEATestNGRemoteListener myListener;
  private boolean myStarted = false;

  public IDEATestNGConfigurationListener(IDEATestNGRemoteListener listener) {
    myListener = listener;
  }

  @Override
  public void onConfigurationSuccess(ITestResult itr) {
    myListener.onConfigurationSuccess(itr, !myStarted);
  }

  @Override
  public void onConfigurationFailure(ITestResult itr) {
    myListener.onConfigurationFailure(itr, !myStarted);
  }

  @Override
  public void onConfigurationSkip(ITestResult itr) {
    myListener.onConfigurationSkip(itr);
  }

  public void setIgnoreStarted() {
    myStarted = true;
  }
}
