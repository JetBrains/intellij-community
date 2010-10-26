package com.jetbrains.python.debugger.pydev;


import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SetBreakpointCommand extends AbstractCommand {
  private @NotNull final String myFile;
  private @NotNull final int myLine;
  private @Nullable final String myCondition;

  public SetBreakpointCommand(@NotNull final RemoteDebugger debugger,
                              @NotNull final String file,
                              @NotNull final int line,
                              @Nullable final String condition) {
    super(debugger, SET_BREAKPOINT);
    myFile = file;
    myLine = line;
    myCondition = condition;
  }

  public String getPayload() {
    return new StringBuilder().append(myFile).append('\t').append(Integer.toString(myLine)).append("\t").append(buildCondition()).toString();
  }

  @NotNull
  private String buildCondition() {
    String condition;

    if (myCondition != null) {
      condition = myCondition.replaceAll("\n", NEW_LINE_CHAR);
      condition = condition.replaceAll("\t", TAB_CHAR);
    }
    else {
      condition = "None";
    }
    return condition;
  }
}
