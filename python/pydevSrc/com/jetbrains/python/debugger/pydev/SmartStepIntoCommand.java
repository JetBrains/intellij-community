package com.jetbrains.python.debugger.pydev;


import org.jetbrains.annotations.NotNull;

public class SmartStepIntoCommand extends AbstractThreadCommand {
  private final String myFuncName;

  public SmartStepIntoCommand(@NotNull final RemoteDebugger debugger, String threadId,
                              String funcName) {
    super(debugger, SMART_STEP_INTO, threadId);
    myFuncName = funcName;
  }

  @Override
  protected void buildPayload(Payload payload) {
    super.buildPayload(payload);
    payload.add("0").add(myFuncName);
  }

}
