// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.pydev;

public class SuspendCommand extends AbstractThreadCommand {
  protected SuspendCommand(final RemoteDebugger debugger, final String threadId) {
    super(debugger, SUSPEND_THREAD, threadId);
  }
}
