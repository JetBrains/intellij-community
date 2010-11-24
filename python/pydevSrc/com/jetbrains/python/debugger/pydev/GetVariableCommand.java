package com.jetbrains.python.debugger.pydev;

import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebuggerException;

import java.util.List;


public class GetVariableCommand extends GetFrameCommand {

  private final String myVariableName;
  private final PyDebugValue myParent;

  public GetVariableCommand(final RemoteDebugger debugger, final String threadId, final String frameId, final String variableName,
                            PyDebugValue parent) {
    super(debugger, GET_VARIABLE, threadId, frameId);
    myVariableName = variableName;
    myParent = parent;
  }

  @Override
  protected void buildPayload(Payload payload) {
    super.buildPayload(payload);
    payload.add(myVariableName);
  }

  @Override
  protected PyDebugValue extend(final PyDebugValue value) {
    return new PyDebugValue(value.getName(), value.getType(), value.getValue(), value.isContainer(), myParent, myDebugProcess);
  }

}
