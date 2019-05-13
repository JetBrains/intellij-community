package com.jetbrains.python.debugger.pydev;


import org.jetbrains.annotations.NotNull;

public class RemoveBreakpointCommand extends LineBreakpointCommand {

  public RemoveBreakpointCommand(final RemoteDebugger debugger, @NotNull final String type, @NotNull final String file, final int line) {
    super(debugger, type, REMOVE_BREAKPOINT, file, line);
  }
}
