package com.jetbrains.python.debugger.pydev;


public class ResumeCommand extends AbstractThreadCommand {

  public static enum Mode {
    RESUME(RESUME_THREAD), STEP_INTO(AbstractCommand.STEP_INTO), STEP_OVER(AbstractCommand.STEP_OVER), STEP_OUT(AbstractCommand.STEP_OUT);

    private final int code;

    private Mode(int code) {
      this.code = code;
    }
  }

  public ResumeCommand(final RemoteDebugger debugger, final String threadId, final Mode mode) {
    super(debugger, mode.code, threadId);
  }

  public String getThreadId() {
    return myThreadId;
  }
}
