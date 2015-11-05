package com.jetbrains.python.debugger;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;

/**
 * @author traff
 */
public abstract class PyConcurrencyService {
  public static PyConcurrencyService getInstance(Project project) {
    return ServiceManager.getService(project, PyConcurrencyService.class);
  }

  public abstract void recordEvent(XDebugSession session, PyConcurrencyEvent event, boolean isAsyncIo);
}
