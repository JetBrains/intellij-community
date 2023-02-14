// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import com.intellij.xdebugger.frame.XValueChildrenList;

public interface PyFrameListener {

  void frameChanged();

  default void sessionStopped() { }

  default void updateVariables(PyFrameAccessor communication, XValueChildrenList values) { }

  default void sessionStopped(PyFrameAccessor communication) { }
}
