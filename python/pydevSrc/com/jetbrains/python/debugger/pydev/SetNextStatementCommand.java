package com.jetbrains.python.debugger.pydev;

import com.intellij.openapi.util.Pair;
import com.intellij.xdebugger.XSourcePosition;
import com.jetbrains.python.debugger.PyDebuggerException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SetNextStatementCommand extends AbstractThreadCommand {
  private final int myLine;
  @NotNull private final PyDebugCallback<Pair<Boolean, String>> myCallback;
  @Nullable private final String myFunctionName;

  protected SetNextStatementCommand(@NotNull RemoteDebugger debugger,
                                    @NotNull String threadId,
                                    @NotNull XSourcePosition sourcePosition,
                                    @Nullable String functionName,
                                    @NotNull PyDebugCallback<Pair<Boolean, String>> callback) {
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
  protected void processResponse(@NotNull ProtocolFrame response) throws PyDebuggerException {
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
