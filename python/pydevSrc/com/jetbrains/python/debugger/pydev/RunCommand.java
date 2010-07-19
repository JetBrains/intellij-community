package com.jetbrains.python.debugger.pydev;


public class RunCommand extends AbstractCommand {

  public RunCommand(final RemoteDebugger debugger) {
    super(debugger, RUN);
  }

  public String getPayload() {
    return null;
  }

}
