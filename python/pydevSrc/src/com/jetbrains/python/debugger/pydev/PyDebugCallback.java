// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.pydev;

import com.jetbrains.python.debugger.PyDebuggerException;

public interface PyDebugCallback<T> {
  void ok(T value);

  void error(PyDebuggerException exception);
}
