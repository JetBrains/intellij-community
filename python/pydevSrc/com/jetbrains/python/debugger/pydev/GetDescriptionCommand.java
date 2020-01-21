// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.pydev;

import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebuggerException;
import org.jetbrains.annotations.NotNull;

public class GetDescriptionCommand extends AbstractFrameCommand {

  private final String myActionToken;
  private String result = null;

  public GetDescriptionCommand(final RemoteDebugger debugger, String threadId, String frameId, final String myActionToken) {
    super(debugger, GET_DESCRIPTION, threadId, frameId);
    this.myActionToken = myActionToken;
  }


  @Override
  public boolean isResponseExpected() {
    return true;
  }

  @Override
  protected long getResponseTimeout() {
    return RemoteDebugger.SHORT_TIMEOUT;
  }

  @Override
  protected void processResponse(@NotNull ProtocolFrame response) throws PyDebuggerException {
    super.processResponse(response);
    try {
      PyDebugValue pyDebugValue = ProtocolParser.parseValue(response.getPayload(), getDebugger().getDebugProcess());
      result = pyDebugValue.getValue();
    }
    catch (Exception e) {
      throw new PyDebuggerException("cant obtain completions", e);
    }
  }


  @Override
  protected void buildPayload(Payload payload) {
    super.buildPayload(payload);
    payload.add(myActionToken);
  }

  public String getResult() {
    return result;
  }
}
