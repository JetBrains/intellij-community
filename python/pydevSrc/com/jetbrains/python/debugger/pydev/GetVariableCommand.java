package com.jetbrains.python.debugger.pydev;

import com.jetbrains.python.debugger.PyDebugValue;


public class GetVariableCommand extends GetFrameCommand {

  public static final String BY_ID = "BY_ID";
  private final String myVariableName;
  private final PyDebugValue myParent;

  public GetVariableCommand(final RemoteDebugger debugger, final String threadId, final String frameId, PyDebugValue var) {
    super(debugger, GET_VARIABLE, threadId, frameId);
    myVariableName = composeName(var);
    myParent = var;
  }

  public static String composeName(final PyDebugValue var) {
    final StringBuilder sb = new StringBuilder();
    PyDebugValue p = var;
    while (p != null) {
      if (sb.length() > 0 ) {
        sb.insert(0, '\t');
      }
      if (p.getId() != null) {
        sb.insert(0, BY_ID).insert(0, '\t').insert(0, p.getId());
        break;
      } else {
        sb.insert(0, p.getTempName().replaceAll("\t", TAB_CHAR));
      }
      p = p.getParent();
    }
    return sb.toString();
  }

  @Override
  protected void buildPayload(Payload payload) {
    if (myParent.getVariableLocator() != null) {
      payload.add(myParent.getVariableLocator().getThreadId()).add(myParent.getVariableLocator().getPyDBLocation());
    }
    else if (myVariableName.contains(BY_ID)) {
      //id instead of frame_id
      payload.add(getThreadId()).add(myVariableName);
    }
    else {
      super.buildPayload(payload);
      payload.add(myVariableName);
    }
  }

  @Override
  protected PyDebugValue extend(final PyDebugValue value) {
    return new PyDebugValue(value.getName(), value.getType(), value.getValue(), value.isContainer(), value.isErrorOnEval(), myParent,
                            myDebugProcess);
  }
}
