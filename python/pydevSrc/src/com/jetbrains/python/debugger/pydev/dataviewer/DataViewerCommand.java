// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.pydev.dataviewer;

import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.pydev.AbstractFrameCommand;
import com.jetbrains.python.debugger.pydev.GetVariableCommand;
import com.jetbrains.python.debugger.pydev.ProtocolFrame;
import com.jetbrains.python.debugger.pydev.RemoteDebugger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.jetbrains.python.debugger.pydev.dataviewer.DataViewerCommandResult.ResultType.UNHANDLED_ERROR;

public class DataViewerCommand extends AbstractFrameCommand {
  private final @NotNull String myVariableName;
  private final @NotNull DataViewerCommandAction myAction;
  private final String @Nullable[] myArgs;
  private @Nullable DataViewerCommandResult myResult;

  public DataViewerCommand(final @NotNull RemoteDebugger debugger,
                           final @NotNull String threadId,
                           final @NotNull String frameId,
                           @NotNull PyDebugValue var,
                           @NotNull DataViewerCommandAction action,
                           String @Nullable[] args) {
    super(debugger, CMD_DATAVIEWER_ACTION, threadId, frameId);
    myVariableName = GetVariableCommand.composeName(var);
    myAction = action;
    myArgs = args;
  }

  @Override
  protected void buildPayload(Payload payload) {
    super.buildPayload(payload);

    payload.add(myVariableName);

    payload.add(myAction.name());

    if (myArgs != null) {
      for (String arg : myArgs) {
        payload.add(arg);
      }
    }
  }

  public @NotNull DataViewerCommandResult getResult() {
    if (myResult == null)
      return DataViewerCommandResult.makeErrorResult(UNHANDLED_ERROR, "No response received");
    return myResult;
  }

  @Override
  protected void processResponse(@NotNull ProtocolFrame response) {
    if (isErrorCommand(response.getCommand())) {
      myResult = DataViewerCommandResult.errorFromExportTraceback(response.getPayload());
    }
    else {
      myResult = DataViewerCommandResult.makeSuccessResult(response.getPayload());
    }
  }

  @Override
  public boolean isResponseExpected() {
    return true;
  }
}
