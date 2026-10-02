// Licensed under the terms of the Eclipse Public License (EPL).
package com.jetbrains.python.debugger.pydev;

import org.jetbrains.annotations.ApiStatus;


@ApiStatus.Internal
public abstract class AbstractFrameCommand<T> extends AbstractThreadCommand<T> {

  private final String myFrameId;

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
