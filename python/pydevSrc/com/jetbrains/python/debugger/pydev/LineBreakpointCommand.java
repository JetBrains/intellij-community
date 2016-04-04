package com.jetbrains.python.debugger.pydev;

import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public abstract class LineBreakpointCommand extends AbstractCommand {
  private final String myType;
  @NotNull protected final String myFile;
  protected final int myLine;


  public LineBreakpointCommand(@NotNull RemoteDebugger debugger,
                               String type, int commandCode,
                               @NotNull final String file,
                               final int line) {
    super(debugger, commandCode);
    myType = type;
    myFile = file;
    myLine = line;
  }

  @Override
  protected void buildPayload(Payload payload) {
    payload.add(myType).add(myFile).add(Integer.toString(myLine));
  }
}
