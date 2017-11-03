package com.jetbrains.python.debugger.pydev;

import com.jetbrains.python.debugger.IPyDebugProcess;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebuggerException;
import com.jetbrains.python.debugger.PyFrameAccessor;
import org.jetbrains.annotations.NotNull;

import java.util.List;


public class LoadFullValueCommand extends AbstractFrameCommand {
  private static String NEXT_VALUE_SEPARATOR = "__pydev_val__";
  private final @NotNull IPyDebugProcess myDebugProcess;
  private final @NotNull List<PyFrameAccessor.PyAsyncValue<String>> myVars;

  public LoadFullValueCommand(final @NotNull RemoteDebugger debugger,
                              final @NotNull String threadId,
                              final @NotNull String frameId,
                              final @NotNull List<PyFrameAccessor.PyAsyncValue<String>> vars) {
    super(debugger, LOAD_FULL_VALUE, threadId, frameId);
    myDebugProcess = debugger.getDebugProcess();
    myVars = vars;
  }

  @Override
  public boolean isResponseExpected() {
    return true;
  }

  @Override
  protected void processResponse(@NotNull ProtocolFrame response) throws PyDebuggerException {
    super.processResponse(response);
    try {
      List<PyDebugValue> debugValues = ProtocolParser.parseValues(response.getPayload(), myDebugProcess);
      for (int i = 0; i < debugValues.size(); ++i) {
        PyDebugValue resultValue = debugValues.get(i);
        myVars.get(i).getCallback().ok(resultValue.getValue());
      }
    }
    catch (Exception e) {
      for (PyFrameAccessor.PyAsyncValue vars : myVars) {
        vars.getCallback().error(new PyDebuggerException(response.getPayload()));
      }
    }
  }

  @NotNull
  private String buildPayloadForVar(@NotNull PyDebugValue var) {
    StringBuilder sb = new StringBuilder();
    String varName = GetVariableCommand.composeName(var);
    if (var.getVariableLocator() != null) {
      sb.append(var.getVariableLocator().getThreadId()).append(var.getVariableLocator().getPyDBLocation());
    }
    else if (varName.contains(GetVariableCommand.BY_ID)) {
      sb.append(getThreadId()).append(varName);
    }
    else {
      sb.append(varName);
    }
    return sb.toString();
  }

  @Override
  protected void buildPayload(Payload payload) {
    super.buildPayload(payload);
    for (PyFrameAccessor.PyAsyncValue<String> var : myVars) {
      PyDebugValue debugValue = var.getDebugValue();
      payload.add("FRAME").add(buildPayloadForVar(debugValue)).add(NEXT_VALUE_SEPARATOR);
    }
  }
}
