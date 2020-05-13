// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.pydev;

import org.jetbrains.annotations.NotNull;

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
