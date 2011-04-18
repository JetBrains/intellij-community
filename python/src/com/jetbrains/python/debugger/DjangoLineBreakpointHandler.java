package com.jetbrains.python.debugger;

import org.jetbrains.annotations.NotNull;


public class DjangoLineBreakpointHandler extends AbstractLineBreakpointHandler {
  public DjangoLineBreakpointHandler(@NotNull final PyDebugProcess debugProcess) {
    super(DjangoTemplateLineBreakpointType.class, debugProcess);
  }
}
