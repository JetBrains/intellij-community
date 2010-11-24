package com.jetbrains.python.debugger.pydev;


public abstract class AbstractThreadCommand extends AbstractCommand {
  protected final String myThreadId;

  protected AbstractThreadCommand(final RemoteDebugger debugger, final int commandCode, final String threadId) {
    super(debugger, commandCode);
    myThreadId = threadId;
  }

  @Override
  protected void buildPayload(Payload payload) {
    payload.add(myThreadId);
  }

  public static boolean isThreadCommand(int command) {
    return command == CREATE_THREAD ||
           command == KILL_THREAD ||
           command == RESUME_THREAD ||
           command == SUSPEND_THREAD;
  }

}
