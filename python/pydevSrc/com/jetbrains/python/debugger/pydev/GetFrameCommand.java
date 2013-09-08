package com.jetbrains.python.debugger.pydev;

import com.intellij.xdebugger.frame.XValueChildrenList;
import com.jetbrains.python.debugger.IPyDebugProcess;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebuggerException;

import java.util.List;


public class GetFrameCommand extends AbstractFrameCommand {

  protected final IPyDebugProcess myDebugProcess;
  private XValueChildrenList myFrameVariables = null;

  public GetFrameCommand(final RemoteDebugger debugger, final String threadId, final String frameId) {
    this(debugger, GET_FRAME, threadId, frameId);
  }

  protected GetFrameCommand(final RemoteDebugger debugger, final int command, final String threadId, final String frameId) {
    super(debugger, command, threadId, frameId);
    myDebugProcess = debugger.getDebugProcess();
  }

  @Override
  protected void buildPayload(Payload payload) {
    super.buildPayload(payload);
    payload.add("FRAME");
  }

  @Override
  public boolean isResponseExpected() {
    return true;
  }

  @Override
  protected void processResponse(final ProtocolFrame response) throws PyDebuggerException {
    super.processResponse(response);
    final List<PyDebugValue> values = ProtocolParser.parseValues(response.getPayload(), myDebugProcess);
    myFrameVariables = new XValueChildrenList(values.size());
    for (PyDebugValue value : values) {
      if (!value.getName().startsWith(RemoteDebugger.TEMP_VAR_PREFIX)) {
        final PyDebugValue debugValue = extend(value);
        myFrameVariables.add(debugValue.getName(), debugValue);
      }
    }
  }

  protected PyDebugValue extend(final PyDebugValue value) {
    return new PyDebugValue(value.getName(), value.getType(), value.getValue(), value.isContainer(), value.isErrorOnEval(), null, myDebugProcess);
  }

  public XValueChildrenList getVariables() {
    return myFrameVariables;
  }

}