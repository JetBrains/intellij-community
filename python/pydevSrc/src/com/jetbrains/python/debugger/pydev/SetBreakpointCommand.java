// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// Licensed under the terms of the Eclipse Public License (EPL).
package com.jetbrains.python.debugger.pydev;


import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SetBreakpointCommand extends LineBreakpointCommand {
  private final @Nullable String myCondition;
  private final @Nullable String myLogExpression;
  private final @Nullable String myFuncName;
  private final @NotNull SuspendPolicy mySuspendPolicy;

  public SetBreakpointCommand(final @NotNull RemoteDebugger debugger,
                              final @NotNull String type,
                              final @NotNull String file,
                              final int line) {
    this(debugger, type, file, line, null, null, null, SuspendPolicy.NONE);
  }


  public SetBreakpointCommand(final @NotNull RemoteDebugger debugger,
                              final @NotNull String type,
                              final @NotNull String file,
                              final int line,
                              final @Nullable String condition,
                              final @Nullable String logExpression,
                              final @Nullable String funcName,
                              final @NotNull SuspendPolicy policy) {
    super(debugger, type, SET_BREAKPOINT, file, line);
    myCondition = condition;
    myLogExpression = logExpression;
    myFuncName = funcName;
    mySuspendPolicy = policy;
  }

  @Override
  protected void buildPayload(Payload payload) {
    super.buildPayload(payload);
    payload.add(buildCondition(myFuncName)).add(mySuspendPolicy.name()).add(buildCondition(myCondition))
      .add(buildCondition(myLogExpression));
  }
}
