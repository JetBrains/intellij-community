package com.jetbrains.python.debugger.pydev;


import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SetBreakpointCommand extends LineBreakpointCommand {
  private @Nullable final String myCondition;
  private @Nullable final String myLogExpression;

  public SetBreakpointCommand(@NotNull final RemoteDebugger debugger,
                              @NotNull final String type,
                              @NotNull final String file,
                              @NotNull final int line) {
    this(debugger, type, file, line, null, null);
  }

  public SetBreakpointCommand(@NotNull final RemoteDebugger debugger,
                              @NotNull final String type,
                              @NotNull final String file,
                              @NotNull final int line,
                              @Nullable final String condition,
                              @Nullable final String logExpression) {
    super(debugger, type, SET_BREAKPOINT, file, line);
    myCondition = condition;
    myLogExpression = logExpression;
  }

  @Override
  protected void buildPayload(Payload payload) {
    super.buildPayload(payload);
    payload.add(buildCondition(myCondition)).add(buildCondition(myLogExpression));
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
