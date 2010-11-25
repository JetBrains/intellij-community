package com.jetbrains.python.debugger.pydev;


public class RemoveBreakpointCommand extends LineBreakpointCommand {

  public RemoveBreakpointCommand(final RemoteDebugger debugger, final String file, final int line) {
    super(debugger, REMOVE_BREAKPOINT, file, line);
  }
}
