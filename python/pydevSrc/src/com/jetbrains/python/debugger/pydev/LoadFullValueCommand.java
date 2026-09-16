// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.pydev;

import com.jetbrains.python.debugger.IPyDebugProcess;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebuggerException;
import com.jetbrains.python.debugger.PyFrameAccessor;
import org.jetbrains.annotations.NotNull;

import java.util.List;


public class LoadFullValueCommand extends AbstractFrameCommand {
  public static final String NEXT_VALUE_SEPARATOR = "__pydev_val__";
  private final @NotNull IPyDebugProcess myDebugProcess;
  private final @NotNull List<? extends PyFrameAccessor.PyAsyncValue<String>> myVars;

  public LoadFullValueCommand(final @NotNull RemoteDebugger debugger,
                              final @NotNull String threadId,
                              final @NotNull String frameId,
                              final @NotNull List<? extends PyFrameAccessor.PyAsyncValue<String>> vars) {
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
      int answered = Math.min(debugValues.size(), myVars.size());
      for (int i = 0; i < answered; ++i) {
        PyDebugValue resultValue = debugValues.get(i);
        myVars.get(i).getCallback().ok(resultValue.getValue());
      }
      // A value the response leaves out is reported, not skipped: its node keeps the placeholder it holds
      // until a callback replaces it, and no later response repeats a value nobody asked for again.
      for (int i = answered; i < myVars.size(); ++i) {
        myVars.get(i).getCallback().error(new PyDebuggerException(
          "The debuggee answered with " + debugValues.size() + " of the " + myVars.size() + " values asked for"));
      }
    }
    catch (Exception e) {
      for (PyFrameAccessor.PyAsyncValue vars : myVars) {
        vars.getCallback().error(new PyDebuggerException(response.getPayload()));
      }
    }
  }

  private @NotNull String buildPayloadForVar(@NotNull PyDebugValue var) {
    StringBuilder sb = new StringBuilder();
    String varName = GetVariableCommand.composeName(var);
    if (varName.contains(GetVariableCommand.BY_ID)) {
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
