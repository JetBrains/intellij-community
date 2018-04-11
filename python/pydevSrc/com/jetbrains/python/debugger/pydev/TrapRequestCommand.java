// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.pydev;

import org.jetbrains.annotations.NotNull;

public class TrapRequestCommand extends AbstractCommand {
  @NotNull private final String myThreadId;

  public TrapRequestCommand(@NotNull RemoteDebugger remoteDebugger,
                            @NotNull String threadId) {
    super(remoteDebugger, SET_TRAP_REQUEST);
    myThreadId = threadId;
  }

  @Override
  protected void buildPayload(Payload payload) {
    payload.add(myThreadId);
  }
}
