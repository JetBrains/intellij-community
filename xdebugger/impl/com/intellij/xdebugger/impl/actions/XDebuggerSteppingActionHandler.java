package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.actions.handlers.XDebuggerActionHandler;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class XDebuggerSteppingActionHandler extends XDebuggerActionHandler {
  public void perform(@NotNull final Project project, final AnActionEvent event) {
  }

  public boolean isEnabled(@NotNull final Project project, final AnActionEvent event) {
    return false;
  }
}
