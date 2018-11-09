// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.pydev;


import com.jetbrains.python.debugger.IPyDebugProcess;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebuggerException;
import org.jetbrains.annotations.NotNull;

public class ChangeVariableCommand extends AbstractFrameCommand {

  private final String myVariableName;
  private final String myValue;
  private PyDebugValue myNewValue = null;
  private final IPyDebugProcess myDebugProcess;

  public ChangeVariableCommand(final RemoteDebugger debugger, final String threadId, final String frameId, final String variableName,
                               final String value) {
    super(debugger, CHANGE_VARIABLE, threadId, frameId);
    myVariableName = variableName;
    myValue = value;
    myDebugProcess = debugger.getDebugProcess();
  }


  @Override
  protected void buildPayload(Payload payload) {
    super.buildPayload(payload);
    payload.add("FRAME").add(myVariableName).add(ProtocolParser.encodeExpression(myValue));
  }

  @Override
  public boolean isResponseExpected() {
    return true;
  }

  @Override
  protected void processResponse(@NotNull final ProtocolFrame response) throws PyDebuggerException {
    super.processResponse(response);
    PyDebugValue returnedValue = ProtocolParser.parseValue(response.getPayload(), myDebugProcess);
    myNewValue = new PyDebugValue(returnedValue, myVariableName);
  }

  public PyDebugValue getNewValue() {
    return myNewValue;
  }
}
