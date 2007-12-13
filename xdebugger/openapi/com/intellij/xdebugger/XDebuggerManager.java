package com.intellij.xdebugger;

import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class XDebuggerManager {

  public static XDebuggerManager getInstance(@NotNull Project project) {
    return project.getComponent(XDebuggerManager.class);
  }

  @NotNull
  public abstract XBreakpointManager getBreakpointManager();
}
