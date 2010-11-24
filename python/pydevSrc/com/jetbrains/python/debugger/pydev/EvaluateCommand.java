package com.jetbrains.python.debugger.pydev;

import com.jetbrains.python.debugger.IPyDebugProcess;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebuggerException;


public class EvaluateCommand extends AbstractFrameCommand {

  private final String myExpression;
  private final boolean myExecute;
  private final IPyDebugProcess myDebugProcess;
  private final boolean myTrimResult;
  private PyDebugValue myValue = null;


  public EvaluateCommand(final RemoteDebugger debugger, final String threadId, final String frameId, final String expression,
                         final boolean execute, final boolean trimResult) {
    super(debugger, (execute ? EXECUTE : EVALUATE), threadId, frameId);
    myExpression = expression;
    myExecute = execute;
    myDebugProcess = debugger.getDebugProcess();
    myTrimResult = trimResult;
  }

  public String getPayload() {
    return new StringBuilder().append(myThreadId).append('\t').append(myFrameId).append('\t').append("FRAME\t")
      .append(ProtocolParser.encodeExpression(myExpression)).append('\t').append(myTrimResult ? "1" : "0").toString();
  }

  @Override
  public boolean isResponseExpected() {
    return true;
  }

  @Override
  protected void processResponse(final ProtocolFrame response) throws PyDebuggerException {
    super.processResponse(response);
    final PyDebugValue value = ProtocolParser.parseValue(response.getPayload());
    final String name = (myExecute ? "" : myExpression);
    myValue = new PyDebugValue(name, value.getType(), value.getValue(), value.isContainer(), null, myDebugProcess);
  }

  public PyDebugValue getValue() {
    return myValue;
  }
}
