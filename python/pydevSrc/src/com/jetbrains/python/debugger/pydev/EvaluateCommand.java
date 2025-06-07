// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// Licensed under the terms of the Eclipse Public License (EPL).
package com.jetbrains.python.debugger.pydev;

import com.jetbrains.python.debugger.IPyDebugProcess;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebuggerException;
import org.jetbrains.annotations.NotNull;


public class EvaluateCommand extends AbstractFrameCommand {

  private final String myExpression;
  private final boolean myExecute;
  private final IPyDebugProcess myDebugProcess;
  private final boolean myTrimResult;
  private PyDebugValue myValue = null;
  private final String myTempName;
  private final int myEvaluationTimeout;


  public EvaluateCommand(final RemoteDebugger debugger, final String threadId, final String frameId, final String expression,
                         final boolean execute, final boolean trimResult, final int evaluationTimeout) {
    super(debugger, (execute ? EXECUTE : EVALUATE), threadId, frameId);
    myExpression = expression;
    myExecute = execute;
    myDebugProcess = debugger.getDebugProcess();
    myTrimResult = trimResult;
    myTempName = myDebugProcess.canSaveToTemp(expression) ? debugger.generateSaveTempName(threadId, frameId) : "";
    myEvaluationTimeout = evaluationTimeout;
  }

  @Override
  protected void buildPayload(Payload payload) {
    super.buildPayload(payload);
    payload.add("FRAME").add(myExpression).add(myTrimResult).add(myTempName);
  }

  @Override
  public boolean isResponseExpected() {
    return true;
  }

  @Override
  protected void processResponse(final @NotNull ProtocolFrame response) throws PyDebuggerException {
    super.processResponse(response);
    final PyDebugValue value = ProtocolParser.parseValue(response.getPayload(), myDebugProcess);
    myValue = new PyDebugValue(value, myExecute ? "" : myExpression);
    if (!myTempName.isEmpty()) {
      myValue.setTempName(myTempName);
    }
  }

  @Override
  protected long getResponseTimeout() {
    return myEvaluationTimeout;
  }

  public PyDebugValue getValue() {
    return myValue;
  }
}
