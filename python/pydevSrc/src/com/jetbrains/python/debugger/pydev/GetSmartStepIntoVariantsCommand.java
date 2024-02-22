// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.pydev;

import com.intellij.openapi.util.Pair;
import com.jetbrains.python.debugger.PyDebuggerException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GetSmartStepIntoVariantsCommand extends AbstractFrameCommand {

  private final int myContextStartLine;
  private final int myContextEndLine;

  private List<Pair<String, Boolean>> myVariants;

  protected GetSmartStepIntoVariantsCommand(RemoteDebugger debugger, String threadId, String frameId,
                                            int contextStartLine, int contextEndLine) {
    super(debugger, CMD_GET_SMART_STEP_INTO_VARIANTS, threadId, frameId);
    myContextStartLine = contextStartLine;
    myContextEndLine = contextEndLine;
  }

  @Override
  protected void buildPayload(Payload payload) {
    super.buildPayload(payload);
    payload.add(myContextStartLine).add(myContextEndLine);
  }

  @Override
  public boolean isResponseExpected() {
    return true;
  }

  @Override
  protected void processResponse(@NotNull ProtocolFrame response) throws PyDebuggerException {
    super.processResponse(response);
    String payload = response.getPayload();
    myVariants = ProtocolParser.parseSmartStepIntoVariants(payload);
  }

  public @Nullable List<Pair<String, Boolean>> getVariants() {
    return myVariants;
  }
}
