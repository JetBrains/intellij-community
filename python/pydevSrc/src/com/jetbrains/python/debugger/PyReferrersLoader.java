// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import com.intellij.xdebugger.frame.XValueChildrenList;
import com.jetbrains.python.debugger.pydev.PyDebugCallback;

public class PyReferrersLoader  {
  private final IPyDebugProcess myProcess;

  public PyReferrersLoader(IPyDebugProcess process) {
    myProcess = process;
  }

  public void loadReferrers(PyReferringObjectsValue value, PyDebugCallback<XValueChildrenList> callback) {
    myProcess.loadReferrers(value, callback);
  }
}
