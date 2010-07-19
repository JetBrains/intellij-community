package com.jetbrains.python.debugger.pydev;

import com.jetbrains.python.debugger.IPyDebugProcess;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebuggerException;


public class EvaluateCommand extends AbstractFrameCommand {

  private final String myExpression;
  private final boolean myExecute;
  private final IPyDebugProcess myDebugProcess;
  private PyDebugValue myValue = null;

  public EvaluateCommand(final RemoteDebugger debugger, final String threadId, final String frameId, final String expression,
                         final boolean execute) {
    super(debugger, (execute ? EXECUTE : EVALUATE), threadId, frameId);
    myExpression = expression;
    myExecute = execute;
    myDebugProcess = debugger.getDebugProcess();
  }

  public String getPayload() {
    return new StringBuilder().append(myThreadId).append('\t').append(myFrameId).append('\t').append("FRAME\t")
      .append(ProtocolParser.encodeExpression(myExpression)).toString();
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
