package com.jetbrains.python.debugger.pydev;


public class ResumeOrStepCommand extends AbstractThreadCommand {

  public static enum Mode {
    RESUME(AbstractCommand.RESUME_THREAD), STEP_INTO(AbstractCommand.STEP_INTO), STEP_OVER(AbstractCommand.STEP_OVER),
    STEP_OUT(AbstractCommand.STEP_OUT), STEP_INTO_MY_CODE(AbstractCommand.STEP_INTO_MY_CODE);

    private final int code;

    private Mode(int code) {
      this.code = code;
    }
  }

  public ResumeOrStepCommand(final RemoteDebugger debugger, final String threadId, final Mode mode) {
    super(debugger, mode.code, threadId);
  }
}
