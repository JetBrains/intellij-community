// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;

public abstract class PyConcurrencyService {
  public static PyConcurrencyService getInstance(Project project) {
    return project.getService(PyConcurrencyService.class);
  }

  public abstract void recordEvent(XDebugSession session, PyConcurrencyEvent event, boolean isAsyncIo);

  public abstract void initSession(XDebugSession session);

  public abstract void removeSession(XDebugSession session);
}
