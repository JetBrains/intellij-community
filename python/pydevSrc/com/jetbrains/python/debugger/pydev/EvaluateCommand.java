package com.jetbrains.python.debugger.pydev;

import com.jetbrains.python.debugger.IPyDebugProcess;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebuggerException;
import org.jetbrains.annotations.NotNull;


public class EvaluateCommand extends AbstractFrameCommand {

  private final String myExpression;
  private final boolean myExecute;
  private final IPyDebugProcess myDebugProcess;
  private final boolean myTrimResult;
  private PyDebugValue myValue = null;
  private final String myTempName;


  public EvaluateCommand(final RemoteDebugger debugger, final String threadId, final String frameId, final String expression,
                         final boolean execute, final boolean trimResult) {
    super(debugger, (execute ? EXECUTE : EVALUATE), threadId, frameId);
    myExpression = expression;
    myExecute = execute;
    myDebugProcess = debugger.getDebugProcess();
    myTrimResult = trimResult;
    myTempName = myDebugProcess.canSaveToTemp(expression)? debugger.generateSaveTempName(threadId, frameId): "";
  }

  @Override
  protected void buildPayload(Payload payload) {
    super.buildPayload(payload);
    payload.add("FRAME").add(myExpression).add(myTrimResult).add(myTempName);
  }

  @Override
  public boolean isResponseExpected() {
    return true;
  }

  @Override
  protected void processResponse(@NotNull final ProtocolFrame response) throws PyDebuggerException {
    super.processResponse(response);
    final PyDebugValue value = ProtocolParser.parseValue(response.getPayload(), myDebugProcess);
    myValue = new PyDebugValue(value, myExecute ? "" : myExpression);
    if (!myTempName.isEmpty()) {
      myValue.setTempName(myTempName);
    }
  }

  public PyDebugValue getValue() {
    return myValue;
  }
}
