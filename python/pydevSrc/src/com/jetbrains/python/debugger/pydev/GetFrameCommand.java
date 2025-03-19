// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// Licensed under the terms of the Eclipse Public License (EPL).
package com.jetbrains.python.debugger.pydev;

import com.intellij.xdebugger.frame.XValueChildrenList;
import com.jetbrains.python.debugger.IPyDebugProcess;
import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebuggerException;
import org.jetbrains.annotations.NotNull;

import java.util.List;


public class GetFrameCommand extends AbstractFrameCommand {

  protected final IPyDebugProcess myDebugProcess;
  private XValueChildrenList myFrameVariables = null;
  private ProcessDebugger.GROUP_TYPE myGroupType = ProcessDebugger.GROUP_TYPE.DEFAULT;

  public GetFrameCommand(final RemoteDebugger debugger, final String threadId, final String frameId, ProcessDebugger.GROUP_TYPE groupType) {
    this(debugger, GET_FRAME, threadId, frameId);
    myGroupType = groupType;
  }

  protected GetFrameCommand(final RemoteDebugger debugger, final int command, final String threadId, final String frameId) {
    super(debugger, command, threadId, frameId);
    myDebugProcess = debugger.getDebugProcess();
  }

  @Override
  protected void buildPayload(Payload payload) {
    super.buildPayload(payload);
    payload.add("FRAME");
    payload.add(myGroupType.ordinal());
  }

  @Override
  public boolean isResponseExpected() {
    return true;
  }

  @Override
  protected void processResponse(final @NotNull ProtocolFrame response) throws PyDebuggerException {
    super.processResponse(response);
    final List<PyDebugValue> values = ProtocolParser.parseValues(response.getPayload(), myDebugProcess);
    myFrameVariables = new XValueChildrenList(values.size());
    for (PyDebugValue value : values) {
      if (!value.getName().startsWith(RemoteDebugger.TEMP_VAR_PREFIX)) {
        final PyDebugValue debugValue = extend(value);
        myFrameVariables.add(debugValue.getVisibleName(), debugValue);
      }
    }
  }

  protected PyDebugValue extend(final PyDebugValue value) {
    PyDebugValue debugValue = new PyDebugValue(value);
    debugValue.setParent(null);
    debugValue.setFrameAccessor(myDebugProcess);
    return debugValue;
  }

  public XValueChildrenList getVariables() {
    return myFrameVariables;
  }
}