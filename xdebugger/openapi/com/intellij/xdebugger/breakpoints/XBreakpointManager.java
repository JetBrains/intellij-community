package com.intellij.xdebugger.breakpoints;

/**
 * @author nik
 */
public interface XBreakpointManager {

  <T extends XBreakpointProperties> XBreakpoint<T> addBreakpoint(XBreakpointType<T> type, T properties);
  void removeBreakpoint(XBreakpoint<?> breakpoint);

  XBreakpoint[] getBreakpoints();

}
