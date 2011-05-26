package com.jetbrains.python.debugger;

import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import org.jetbrains.annotations.NotNull;


/**
 * @author traff
 */
abstract public class ExceptionBreakpointHandler<T extends ExceptionBreakpointProperties> extends XBreakpointHandler<XBreakpoint<T>> {

  private final PyDebugProcess myDebugProcess;

  public ExceptionBreakpointHandler(@NotNull final PyDebugProcess debugProcess, @NotNull Class<? extends XBreakpointType<XBreakpoint<T>, ?>> breakpointTypeClass) {
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
