package com.jetbrains.python.debugger;

import org.jetbrains.annotations.NotNull;


public class PyLineBreakpointHandler extends AbstractLineBreakpointHandler {

  public PyLineBreakpointHandler(@NotNull final PyDebugProcess debugProcess) {
    super(PyLineBreakpointType.class, debugProcess);
  }
}
