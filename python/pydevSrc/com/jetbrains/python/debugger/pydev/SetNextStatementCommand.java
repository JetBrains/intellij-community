package com.jetbrains.python.debugger.pydev;

import com.intellij.openapi.util.Pair;
import com.intellij.xdebugger.XSourcePosition;
import com.jetbrains.python.debugger.PyDebuggerException;

public class SetNextStatementCommand extends AbstractThreadCommand {
  private int myLine;
  private String myFunctionName;
  private final PyDebugCallback<Pair<Boolean, String>> myCallback;

  protected SetNextStatementCommand(RemoteDebugger debugger,
                                    String threadId,
                                    XSourcePosition sourcePosition,
                                    String functionName,
                                    PyDebugCallback<Pair<Boolean, String>> callback) {
    super(debugger, SET_NEXT_STATEMENT, threadId);
    myLine = sourcePosition.getLine();
    myFunctionName = functionName;
    myCallback = callback;
  }

  @Override
  public boolean isResponseExpected() {
    return true;
  }

  @Override
  protected void processResponse(ProtocolFrame response) throws PyDebuggerException {
    super.processResponse(response);
    try {
      Pair<Boolean, String> result = ProtocolParser.parseSetNextStatementCommand(response.getPayload());
      myCallback.ok(result);
    }
    catch (Exception e) {
      myCallback.error(new PyDebuggerException(response.getPayload()));
    }
  }

  @Override
  protected void buildPayload(Payload payload) {
    super.buildPayload(payload);
    payload.add(myLine + 1).add(buildCondition(myFunctionName));
  }
}
