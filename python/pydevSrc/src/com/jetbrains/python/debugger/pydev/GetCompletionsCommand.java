// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.pydev;

import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.debugger.PyDebuggerException;
import com.jetbrains.python.debugger.PydevXmlUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class GetCompletionsCommand extends AbstractFrameCommand {

  private final String myActionToken;
  private List<PydevCompletionVariant> myCompletions = null;

  public GetCompletionsCommand(final RemoteDebugger debugger,
                               String threadId,
                               String frameId,
                               final String myActionToken) {
    super(debugger, GET_COMPLETIONS, threadId, frameId);
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
      myCompletions = PydevXmlUtils.xmlToCompletions(response.getPayload(), myActionToken);
    }
    catch (Exception e) {
      throw new PyDebuggerException("cant obtain completions", e);
    }
  }


  @Override
  protected void buildPayload(Payload payload) {
    super.buildPayload(payload);
    payload.add("FRAME").add(myActionToken);
  }

  public @NotNull List<PydevCompletionVariant> getCompletions() {
    if (myCompletions != null) {
      return myCompletions;
    }
    else {
      return new ArrayList<>();
    }
  }
}
