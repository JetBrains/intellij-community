package com.jetbrains.python.debugger.pydev;

import com.jetbrains.python.debugger.PyDebugValue;


public class GetVariableCommand extends GetFrameCommand {

  private final String myVariableName;
  private final PyDebugValue myParent;

  public GetVariableCommand(final RemoteDebugger debugger, final String threadId, final String frameId, PyDebugValue var) {
    super(debugger, GET_VARIABLE, threadId, frameId);
    myVariableName = composeName(var);
    myParent = var;
  }

  public static String composeName(final PyDebugValue var) {
    final StringBuilder sb = new StringBuilder(var.getTempName());
    PyDebugValue p = var;
    while ((p = p.getParent()) != null) {
      sb.insert(0, '\t').insert(0, p.getTempName());
    }
    return sb.toString();
  }

  @Override
  protected void buildPayload(Payload payload) {
    super.buildPayload(payload);
    payload.add(myVariableName);
  }

  @Override
  protected PyDebugValue extend(final PyDebugValue value) {
    return new PyDebugValue(value.getName(), value.getType(), value.getValue(), value.isContainer(), value.isErrorOnEval(), myParent, myDebugProcess);
  }

}
