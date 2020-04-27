// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import org.jetbrains.annotations.NotNull;


public class PyExceptionBreakpointHandler extends ExceptionBreakpointHandler<PyExceptionBreakpointProperties> {
   public PyExceptionBreakpointHandler(@NotNull final PyDebugProcess debugProcess) {
    super(debugProcess, PyExceptionBreakpointType.class);
  }
}
