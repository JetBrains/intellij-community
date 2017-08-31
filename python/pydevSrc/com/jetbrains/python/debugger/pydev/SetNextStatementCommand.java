package com.jetbrains.python.debugger.pydev;

public class SetNextStatementCommand extends AbstractThreadCommand {
  private int myLine;
  private String myFunctionName;

  protected SetNextStatementCommand(RemoteDebugger debugger, String threadId, int line, String functionName) {
    super(debugger, SET_NEXT_STATEMENT, threadId);
    myLine = line;
    myFunctionName = functionName;
  }

  @Override
  protected void buildPayload(Payload payload) {
    super.buildPayload(payload);
    payload.add(myLine + 1).add(myFunctionName);
  }
}
