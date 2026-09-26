// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger;

import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import org.jetbrains.annotations.NotNull;


public abstract class ExceptionBreakpointHandler<T extends ExceptionBreakpointProperties<?>> extends XBreakpointHandler<XBreakpoint<T>> {

  private final PyDebugProcess myDebugProcess;

  public ExceptionBreakpointHandler(final @NotNull PyDebugProcess debugProcess, @NotNull Class<? extends XBreakpointType<XBreakpoint<T>, T>> breakpointTypeClass) {
    super(breakpointTypeClass);
    myDebugProcess = debugProcess;
  }

  @Override
  public void registerBreakpoint(@NotNull XBreakpoint<T> breakpoint) {
    myDebugProcess.addExceptionBreakpoint(breakpoint);
  }

  @Override
  public void unregisterBreakpoint(@NotNull XBreakpoint<T> breakpoint, boolean temporary) {
    myDebugProcess.removeExceptionBreakpoint(breakpoint);
  }
}
