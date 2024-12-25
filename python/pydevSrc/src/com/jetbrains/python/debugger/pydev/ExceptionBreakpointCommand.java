// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.pydev;

import org.jetbrains.annotations.NotNull;

public class ExceptionBreakpointCommand extends AbstractCommand {

  protected final @NotNull String myException;


  public ExceptionBreakpointCommand(final @NotNull RemoteDebugger debugger,
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
  addExceptionBreakpointCommand(final @NotNull RemoteDebugger debugger,
                                String exception,
                                String condition,
                                String logExpression,
                                AddExceptionBreakpointCommand.ExceptionBreakpointNotifyPolicy notifyPolicy) {
    return new AddExceptionBreakpointCommand(debugger, exception, condition, logExpression, notifyPolicy);
  }

  public static ExceptionBreakpointCommand removeExceptionBreakpointCommand(final @NotNull RemoteDebugger debugger, String exception) {
    return new ExceptionBreakpointCommand(debugger, REMOVE_EXCEPTION_BREAKPOINT, exception);
  }
}
