package com.intellij.xdebugger.impl.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class DebuggerToggleActionHandler {

  public abstract boolean isEnabled(@NotNull Project project, AnActionEvent event);

  public abstract boolean isSelected(@NotNull Project project, AnActionEvent event);

  public abstract void setSelected(@NotNull Project project, AnActionEvent event, boolean state);

}