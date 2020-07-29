// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.rt.testng;

import org.testng.ISuite;
import org.testng.ISuiteListener;

public class IDEATestNGSuiteListener implements ISuiteListener {
  private final IDEATestNGRemoteListener myListener;

  public IDEATestNGSuiteListener(IDEATestNGRemoteListener listener) {
    myListener = listener;
  }

  @Override
  public void onStart(ISuite suite) {
    myListener.onStart(suite);
  }

  @Override
  public void onFinish(ISuite suite) {
    myListener.onFinish(suite);
  }
}
