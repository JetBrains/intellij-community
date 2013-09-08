package com.jetbrains.python.debugger.pydev;


import com.jetbrains.python.debugger.IPyDebugProcess;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebuggerException;

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

  protected void processResponse(final ProtocolFrame response) throws PyDebuggerException {
    super.processResponse(response);
    myNewValue = ProtocolParser.parseValue(response.getPayload(), myDebugProcess).setName(myVariableName);
  }

  public PyDebugValue getNewValue() {
    return myNewValue;
  }
}
