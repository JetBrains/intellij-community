package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.actionSystem.AnActionEvent;

/**
 * @author nik
 */
public class XDebuggerSteppingActionHandler extends DebuggerActionHandler {
  public void perform(final Project project, final AnActionEvent event) {
  }

  public boolean isEnabled(final Project project, final AnActionEvent event) {
    return false;
  }
}
