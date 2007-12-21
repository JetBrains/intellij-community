package com.intellij.xdebugger.breakpoints;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class XBreakpointAdapter<B extends XBreakpoint<?>> implements XBreakpointListener<B>{
  public void breakpointAdded(@NotNull final B breakpoint) {
  }

  public void breakpointRemoved(@NotNull final B breakpoint) {
  }
}
