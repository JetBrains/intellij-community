package com.jetbrains.python.debugger.pydev;

import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public abstract class LineBreakpointCommand extends AbstractCommand {
  @NotNull protected final String myFile;
  protected final int myLine;

  public LineBreakpointCommand(RemoteDebugger debugger,
                               int commandCode,
                               @NotNull final String file,
                               final int line) {
    super(debugger, commandCode);
    myFile = file;
    myLine = line;
  }

  @Override
  protected void buildPayload(Payload payload) {
    payload.add(myFile).add(Integer.toString(myLine));
  }
}
