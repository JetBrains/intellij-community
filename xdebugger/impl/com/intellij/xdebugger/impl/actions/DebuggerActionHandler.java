package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class DebuggerActionHandler {

  public abstract void perform(@NotNull Project project, AnActionEvent event);

  public abstract boolean isEnabled(@NotNull Project project, AnActionEvent event);

}
