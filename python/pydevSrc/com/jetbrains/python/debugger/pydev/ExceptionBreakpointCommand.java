package com.jetbrains.python.debugger.pydev;

import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class ExceptionBreakpointCommand extends AbstractCommand {

  protected String myException;

  private ExceptionBreakpointCommand(@NotNull final RemoteDebugger debugger, final int commandCode, String exception) {
    super(debugger, commandCode);
    myException = exception;
  }

  @Override
  public String getPayload() {
    return new StringBuilder().append(myException).toString();
  }

  public static ExceptionBreakpointCommand addExceptionBreakpointCommand(@NotNull final RemoteDebugger debugger, String exception) {
    return new ExceptionBreakpointCommand(debugger, ADD_EXCEPTION_BREAKPOINT, exception);
  }

  public static ExceptionBreakpointCommand removeExceptionBreakpointCommand(@NotNull final RemoteDebugger debugger, String exception) {
    return new ExceptionBreakpointCommand(debugger, REMOVE_EXCEPTION_BREAKPOINT, exception);
  }
}
