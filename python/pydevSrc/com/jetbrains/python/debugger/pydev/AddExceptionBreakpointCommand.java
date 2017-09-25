package com.jetbrains.python.debugger.pydev;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author traff
 */
public class AddExceptionBreakpointCommand extends ExceptionBreakpointCommand {
  final ExceptionBreakpointNotifyPolicy myNotifyPolicy;
  final String myCondition;
  final String myLogExpression;

  public AddExceptionBreakpointCommand(@NotNull final RemoteDebugger debugger,
                                       @NotNull String exception,
                                       @Nullable String condition,
                                       @Nullable String logExpression,
                                       @NotNull ExceptionBreakpointNotifyPolicy notifyPolicy) {
    super(debugger, ADD_EXCEPTION_BREAKPOINT, exception);
    myNotifyPolicy = notifyPolicy;
    myCondition = condition;
    myLogExpression = logExpression;
  }

  @Override
  protected void buildPayload(Payload payload) {
    super.buildPayload(payload);
    payload.add(buildCondition(myCondition))
      .add(buildCondition(myLogExpression))
      .add(myNotifyPolicy.isNotifyOnlyOnFirst() ? 2 : 0)
      .add(myNotifyPolicy.isNotifyOnTerminate())
      .add(myNotifyPolicy.isIgnoreLibraries());
  }

  public static class ExceptionBreakpointNotifyPolicy {
    private final boolean myNotifyOnTerminate;
    private final boolean myNotifyOnlyOnFirst;
    private final boolean myIgnoreLibraries;

    public ExceptionBreakpointNotifyPolicy(boolean notifyOnTerminate, boolean notifyOnlyOnFirst,
                                           boolean ignoreLibraries) {
      myNotifyOnTerminate = notifyOnTerminate;
      myNotifyOnlyOnFirst = notifyOnlyOnFirst;
      myIgnoreLibraries = ignoreLibraries;
    }

    public boolean isNotifyOnTerminate() {
      return myNotifyOnTerminate;
    }

    public boolean isNotifyOnlyOnFirst() {
      return myNotifyOnlyOnFirst;
    }

    public boolean isIgnoreLibraries() {
      return myIgnoreLibraries;
    }
  }
}
