// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.pydev;

public interface ExceptionBreakpointCommandFactory {
  ExceptionBreakpointCommand createAddCommand(RemoteDebugger debugger);

  ExceptionBreakpointCommand createRemoveCommand(RemoteDebugger debugger);
}
