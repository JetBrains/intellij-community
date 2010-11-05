package com.jetbrains.python.debugger.pydev;

import com.jetbrains.python.debugger.PyDebuggerException;
import org.jetbrains.annotations.Nullable;


public abstract class AbstractCommand {

  public static final int RUN = 101;
  public static final int CREATE_THREAD = 103;
  public static final int KILL_THREAD = 104;
  public static final int SUSPEND_THREAD = 105;
  public static final int RESUME_THREAD = 106;
  public static final int STEP_INTO = 107;
  public static final int STEP_OVER = 108;
  public static final int STEP_OUT = 109;
  public static final int GET_VARIABLE = 110;
  public static final int SET_BREAKPOINT = 111;
  public static final int REMOVE_BREAKPOINT = 112;
  public static final int EVALUATE = 113;
  public static final int GET_FRAME = 114;
  public static final int EXECUTE = 115;
  public static final int CHANGE_VARIABLE = 117;
  public static final int GET_COMPLETIONS = 120;
  public static final int CONSOLE_EXEC = 121;
  public static final int VERSION = 501;
  public static final String NEW_LINE_CHAR = "@_@NEW_LINE_CHAR@_@";
  public static final String TAB_CHAR = "@_@TAB_CHAR@_@";

  private final RemoteDebugger myDebugger;
  private final int myCommandCode;

  protected AbstractCommand(final RemoteDebugger debugger, final int commandCode) {
    myDebugger = debugger;
    myCommandCode = commandCode;
  }

  @Nullable
  public abstract String getPayload();

  public boolean isResponseExpected() {
    return false;
  }

  // todo: pass debugger to constructor(?)
  public void execute() throws PyDebuggerException {
    int sequence = myDebugger.getNextSequence();
    if (isResponseExpected()) {
      myDebugger.placeResponse(sequence, null);
    }

    ProtocolFrame frame = new ProtocolFrame(myCommandCode, sequence, getPayload());
    myDebugger.sendFrame(frame);
    if (!isResponseExpected()) return;

    frame = myDebugger.waitForResponse(sequence);
    if (frame == null) {
      throw new PyDebuggerException("Timeout waiting for response on " + myCommandCode);
    }
    processResponse(frame);
  }

  protected void processResponse(final ProtocolFrame response) throws PyDebuggerException {
    if (response.getCommand() >= 900 && response.getCommand() < 1000) {
      throw new PyDebuggerException(response.getPayload());
    }
  }

}
