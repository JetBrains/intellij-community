package com.jetbrains.python.debugger;

import com.google.common.collect.Maps;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import org.jetbrains.annotations.NotNull;

import java.util.Map;


/**
 * @author traff
 */
public class PyExceptionBreakpointHandler extends XBreakpointHandler<XBreakpoint<PyExceptionBreakpointProperties>> {

  private final PyDebugProcess myDebugProcess;

  private final Map<XLineBreakpoint<XBreakpointProperties>, XSourcePosition> myBreakPointPositions = Maps.newHashMap();

  public PyExceptionBreakpointHandler(@NotNull final PyDebugProcess debugProcess) {
    super(PyExceptionBreakpointType.class);
    myDebugProcess = debugProcess;
  }

  @Override
  public void registerBreakpoint(@NotNull XBreakpoint<PyExceptionBreakpointProperties> breakpoint) {
    myDebugProcess.addExceptionBreakpoint(breakpoint.getProperties());
  }

  @Override
  public void unregisterBreakpoint(@NotNull XBreakpoint<PyExceptionBreakpointProperties> breakpoint, boolean temporary) {
    myDebugProcess.removeExceptionBreakpoint(breakpoint.getProperties());
  }
}
