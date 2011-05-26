package com.jetbrains.python.debugger;

import org.jetbrains.annotations.NotNull;


/**
 * @author traff
 */
public class PyExceptionBreakpointHandler extends ExceptionBreakpointHandler<PyExceptionBreakpointProperties> {
   public PyExceptionBreakpointHandler(@NotNull final PyDebugProcess debugProcess) {
    super(debugProcess, PyExceptionBreakpointType.class);
  }
}
