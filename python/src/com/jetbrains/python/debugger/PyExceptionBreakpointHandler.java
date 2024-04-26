// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger;

import org.jetbrains.annotations.NotNull;


public class PyExceptionBreakpointHandler extends ExceptionBreakpointHandler<PyExceptionBreakpointProperties> {
   public PyExceptionBreakpointHandler(final @NotNull PyDebugProcess debugProcess) {
    super(debugProcess, PyExceptionBreakpointType.class);
  }
}
