package com.jetbrains.python.debugger.pydev;


public class RemoveBreakpointCommand extends AbstractCommand {

  private final String myFile;
  private final int myLine;

  public RemoveBreakpointCommand(final RemoteDebugger debugger, final String file, final int line) {
    super(debugger, REMOVE_BREAKPOINT);
    myFile = file;
    myLine = line;
  }

  public String getPayload() {
    return new StringBuilder().append(myFile).append('\t').append(Integer.toString(myLine)).toString();
  }

}
