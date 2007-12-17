package com.intellij.xdebugger.breakpoints;

import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * @author nik
 */
public interface XBreakpointListener<T extends XBreakpointProperties> extends EventListener {

  void breakpointAdded(@NotNull XBreakpoint<T> breakpoint);

  void breakpointRemoved(@NotNull XBreakpoint<T> breakpoint);

}
