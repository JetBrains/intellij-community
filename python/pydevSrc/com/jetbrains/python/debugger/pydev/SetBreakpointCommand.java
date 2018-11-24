package com.jetbrains.python.debugger.pydev;


import com.intellij.xdebugger.breakpoints.SuspendPolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SetBreakpointCommand extends LineBreakpointCommand {
  private @Nullable final String myCondition;
  private @Nullable final String myLogExpression;
  private @Nullable final String myFuncName;
  private @NotNull final SuspendPolicy mySuspendPolicy;

  public SetBreakpointCommand(@NotNull final RemoteDebugger debugger,
                              @NotNull final String type,
                              @NotNull final String file,
                              final int line) {
    this(debugger, type, file, line, null, null, null, SuspendPolicy.NONE);
  }


  public SetBreakpointCommand(@NotNull final RemoteDebugger debugger,
                              @NotNull final String type,
                              @NotNull final String file,
                              final int line,
                              @Nullable final String condition,
                              @Nullable final String logExpression,
                              @Nullable final String funcName,
                              @NotNull final SuspendPolicy policy) {
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

  @NotNull
  private static String buildCondition(String expression) {
    String condition;

    if (expression != null) {
      condition = expression.replaceAll("\n", NEW_LINE_CHAR);
      condition = condition.replaceAll("\t", TAB_CHAR);
    }
    else {
      condition = "None";
    }
    return condition;
  }
}
