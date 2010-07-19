package com.jetbrains.python.debugger.pydev;


public class SetBreakpointCommand extends AbstractCommand {

  private final String myFile;
  private final int myLine;

  public SetBreakpointCommand(final RemoteDebugger debugger, final String file, final int line) {
    super(debugger, SET_BREAKPOINT);
    myFile = file;
    myLine = line;
  }

  public String getPayload() {
    return new StringBuilder().append(myFile).append('\t').append(Integer.toString(myLine)).append("\tNone").toString();
  }

}
