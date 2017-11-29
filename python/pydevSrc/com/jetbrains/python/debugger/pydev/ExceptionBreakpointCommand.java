package com.jetbrains.python.debugger.pydev;

import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class ExceptionBreakpointCommand extends AbstractCommand {

  @NotNull
  protected final String myException;


  public ExceptionBreakpointCommand(@NotNull final RemoteDebugger debugger,
                                     final int commandCode,
                                     @NotNull String exception) {
    super(debugger, commandCode);
    myException = exception;
  }

  @Override
  protected void buildPayload(Payload payload) {
    payload.add(myException);
  }

  public static ExceptionBreakpointCommand
  addExceptionBreakpointCommand(@NotNull final RemoteDebugger debugger,
                                String exception,
                                String condition,
                                String logExpression,
                                AddExceptionBreakpointCommand.ExceptionBreakpointNotifyPolicy notifyPolicy) {
    return new AddExceptionBreakpointCommand(debugger, exception, condition, logExpression, notifyPolicy);
  }

  public static ExceptionBreakpointCommand removeExceptionBreakpointCommand(@NotNull final RemoteDebugger debugger, String exception) {
    return new ExceptionBreakpointCommand(debugger, REMOVE_EXCEPTION_BREAKPOINT, exception);
  }
}
