package com.jetbrains.python.debugger.pydev;

import org.jetbrains.annotations.NotNull;

public class ShowReturnValuesCommand extends AbstractCommand {
  private final boolean myShowReturnValues;

  public ShowReturnValuesCommand(@NotNull RemoteDebugger debugger, boolean showReturnValues) {
    super(debugger, AbstractCommand.SHOW_RETURN_VALUES);
    myShowReturnValues = showReturnValues;
  }

  @Override
  protected void buildPayload(Payload payload) {
    payload.add("SHOW_RETURN_VALUES").add(myShowReturnValues);
  }
}
