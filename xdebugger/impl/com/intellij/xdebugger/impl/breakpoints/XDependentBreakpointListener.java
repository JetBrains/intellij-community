package com.intellij.xdebugger.impl.breakpoints;

import com.intellij.xdebugger.breakpoints.XBreakpoint;

import java.util.EventListener;

/**
 * @author nik
 */
public interface XDependentBreakpointListener extends EventListener {

  void dependencySet(XBreakpoint<?> slave, XBreakpoint<?> master);

  void dependencyCleared(XBreakpoint<?> breakpoint);
}
