package com.jetbrains.python.debugger.pydev;


public abstract class AbstractFrameCommand extends AbstractThreadCommand {

  protected final String myFrameId;

  protected AbstractFrameCommand(final RemoteDebugger debugger, final int commandCode, final String threadId, final String frameId) {
    super(debugger, commandCode, threadId);
    myFrameId = frameId;
  }

  @Override
  protected void buildPayload(Payload payload) {
    super.buildPayload(payload);
    payload.add(myFrameId);
  }
}
