// Licensed under the terms of the Eclipse Public License (EPL).
package com.jetbrains.python.debugger.pydev;


public class RunCommand extends AbstractCommand {

  public RunCommand(final RemoteDebugger debugger) {
    super(debugger, RUN);
  }

  @Override
  protected void buildPayload(Payload payload) {
  }
}
