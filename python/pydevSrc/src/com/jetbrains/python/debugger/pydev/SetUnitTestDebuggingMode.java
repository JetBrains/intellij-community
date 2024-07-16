// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.pydev;

import org.jetbrains.annotations.NotNull;

public class SetUnitTestDebuggingMode extends AbstractCommand {
  public SetUnitTestDebuggingMode(@NotNull RemoteDebugger debugger) {
    super(debugger, AbstractCommand.CMD_SET_UNIT_TESTS_DEBUGGING_MODE);
  }

  @Override
  protected void buildPayload(Payload payload) {
    payload.add("SET_UNIT_TESTS_DEBUGGING_MODE");
  }
}
