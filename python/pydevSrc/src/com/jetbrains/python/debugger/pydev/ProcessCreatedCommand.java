// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.pydev;

import org.jetbrains.annotations.NotNull;

/**
 * @author Alexander Koshevoy
 */
public class ProcessCreatedCommand extends AbstractCommand<ProcessCreatedCommand> {
  public ProcessCreatedCommand(@NotNull RemoteDebugger debugger, int commandCode) {
    super(debugger, commandCode);
  }

  @Override
  protected void buildPayload(Payload payload) {
  }

  public static boolean isProcessCreatedCommand(int command) {
    return command == AbstractCommand.PROCESS_CREATED;
  }
}
