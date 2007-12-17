package com.intellij.xdebugger.breakpoints;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class XBreakpointAdapter<T extends XBreakpointProperties> implements XBreakpointListener<T>{
  public void breakpointAdded(@NotNull final XBreakpoint<T> breakpoint) {
  }

  public void breakpointRemoved(@NotNull final XBreakpoint<T> breakpoint) {
  }
}
