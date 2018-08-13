// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.pydev;

import com.jetbrains.python.debugger.PyDebuggerException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author traff
 */
public class LoadSourceCommand extends AbstractCommand {
  private final String myPath;

  private String myContent = null;

  protected LoadSourceCommand(@NotNull final RemoteDebugger debugger, String path) {
    super(debugger, LOAD_SOURCE);
    myPath = path;
  }

  @Override
  public boolean isResponseExpected() {
    return true;
  }

  @Override
  protected void processResponse(@NotNull final ProtocolFrame response) throws PyDebuggerException {
    super.processResponse(response);
    myContent = ProtocolParser.parseSourceContent(response.getPayload());
  }

  @Override
  protected void buildPayload(Payload payload) {
    payload.add(myPath);
  }

  @Nullable
  public String getContent() {
    return myContent;
  }
}
