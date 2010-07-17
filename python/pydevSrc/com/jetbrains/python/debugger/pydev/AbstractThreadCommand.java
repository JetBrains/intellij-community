package com.jetbrains.python.debugger.pydev;


public abstract class AbstractThreadCommand extends AbstractCommand {

  protected final String myThreadId;

  protected AbstractThreadCommand(final RemoteDebugger debugger, final int commandCode, final String threadId) {
    super(debugger, commandCode);
    myThreadId = threadId;
  }

}
