// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.pydev;


public class ResumeOrStepCommand extends AbstractThreadCommand {

  public enum Mode {
    RESUME(AbstractCommand.RESUME_THREAD), STEP_INTO(AbstractCommand.STEP_INTO), STEP_OVER(AbstractCommand.STEP_OVER),
    STEP_OUT(AbstractCommand.STEP_OUT), STEP_INTO_MY_CODE(AbstractCommand.STEP_INTO_MY_CODE);

    private final int code;

    Mode(int code) {
      this.code = code;
    }
  }

  public ResumeOrStepCommand(final RemoteDebugger debugger, final String threadId, final Mode mode) {
    super(debugger, mode.code, threadId);
  }
}
