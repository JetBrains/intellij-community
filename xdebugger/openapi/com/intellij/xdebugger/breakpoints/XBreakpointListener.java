package com.intellij.xdebugger.breakpoints;

import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * @author nik
 */
public interface XBreakpointListener<B extends XBreakpoint<?>> extends EventListener {

  void breakpointAdded(@NotNull B breakpoint);

  void breakpointRemoved(@NotNull B breakpoint);

  void breakpointChanged(@NotNull B breakpoint);
}
