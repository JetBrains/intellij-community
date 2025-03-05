// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// Licensed under the terms of the Eclipse Public License (EPL).
package com.jetbrains.python.debugger.pydev;


import org.jetbrains.annotations.NotNull;

public class RemoveBreakpointCommand extends LineBreakpointCommand {

  public RemoveBreakpointCommand(final RemoteDebugger debugger, final @NotNull String type, final @NotNull String file, final int line) {
    super(debugger, type, REMOVE_BREAKPOINT, file, line);
  }
}
