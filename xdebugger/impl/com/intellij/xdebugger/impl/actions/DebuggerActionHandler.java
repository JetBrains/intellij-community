package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;

/**
 * @author nik
 */
public abstract class DebuggerActionHandler {

  public abstract void perform(final Project project, AnActionEvent event);

  public abstract boolean isEnabled(final Project project, AnActionEvent event);

}
