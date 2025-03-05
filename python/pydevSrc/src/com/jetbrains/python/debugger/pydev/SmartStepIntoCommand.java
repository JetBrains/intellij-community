// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.pydev;


import org.jetbrains.annotations.NotNull;

public class SmartStepIntoCommand extends AbstractFrameCommand {
  private final String myFuncName;
  private final int myCallOrder;
  private final int myContextStartLine;
  private final int myContextEndLine;


  public SmartStepIntoCommand(final @NotNull RemoteDebugger debugger, String threadId, String frameId,
                              String funcName, int callOrder, int contextStartLine, int contextEndLine) {
    super(debugger, SMART_STEP_INTO, threadId, frameId);
    myFuncName = funcName;
    myCallOrder = callOrder;
    myContextStartLine = contextStartLine;
    myContextEndLine = contextEndLine;
  }

  @Override
  protected void buildPayload(Payload payload) {
    super.buildPayload(payload);
    payload.add("0").add(myFuncName).add(myCallOrder).add(myContextStartLine).add(myContextEndLine);
  }
}
