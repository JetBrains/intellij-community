package com.jetbrains.python.debugger;

import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.breakpoints.XBreakpointHandler;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import org.jetbrains.annotations.NotNull;


public class PyLineBreakpointHandler extends XBreakpointHandler<XLineBreakpoint<XBreakpointProperties>> {

  private final PyDebugProcess myDebugProcess;

  public PyLineBreakpointHandler(@NotNull final PyDebugProcess debugProcess) {
    super(PyLineBreakpointType.class);
    myDebugProcess = debugProcess;
  }

  public void registerBreakpoint(@NotNull final XLineBreakpoint<XBreakpointProperties> breakpoint) {
    final XSourcePosition position = breakpoint.getSourcePosition();
    if (position != null) {
      myDebugProcess.addBreakpoint(myDebugProcess.getPositionConverter().convert(position), breakpoint);
    }
  }

  public void unregisterBreakpoint(@NotNull final XLineBreakpoint<XBreakpointProperties> breakpoint, final boolean temporary) {
    final XSourcePosition position = breakpoint.getSourcePosition();
    if (position != null) {
      myDebugProcess.removeBreakpoint(myDebugProcess.getPositionConverter().convert(position));
    }
  }

}
