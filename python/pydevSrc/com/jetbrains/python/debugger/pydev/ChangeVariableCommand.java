package com.jetbrains.python.debugger.pydev;


public class ChangeVariableCommand extends AbstractFrameCommand {

  private final String myVariableName;
  private final String myValue;

  public ChangeVariableCommand(final RemoteDebugger debugger, final String threadId, final String frameId, final String variableName,
                               final String value) {
    super(debugger, CHANGE_VARIABLE, threadId, frameId);
    myVariableName = variableName;
    myValue = value;
  }

  public String getPayload() {
    return new StringBuilder().append(myThreadId).append('\t').append(myFrameId).append('\t').append("FRAME\t").append(myVariableName)
      .append('\t').append(ProtocolParser.encodeExpression(myValue)).toString();
  }

  @Override
  public boolean isResponseExpected() {
    return true;
  }

}
