package com.jetbrains.python.debugger;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.xdebugger.breakpoints.XBreakpointProperties;
import com.jetbrains.python.debugger.pydev.ExceptionBreakpointCommand;
import com.jetbrains.python.debugger.pydev.RemoteDebugger;

/**
 * @author traff
 */
public abstract class ExceptionBreakpointProperties<T> extends XBreakpointProperties<T> {
  @Attribute("exception")
  public String myException;

  public String getException() {
    return myException;
  }

  abstract public ExceptionBreakpointCommand createAddCommand(RemoteDebugger debugger);

  abstract public ExceptionBreakpointCommand createRemoveCommand(RemoteDebugger debugger);
}
