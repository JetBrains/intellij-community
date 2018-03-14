package org.jetbrains.plugins.ruby.ruby.actions;

import com.intellij.execution.Executor;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public abstract class RunAnythingItem<T> {
  protected abstract void runInner(@NotNull Executor executor,
                                   @Nullable VirtualFile workDirectory,
                                   @Nullable Component component,
                                   @NotNull Project project,
                                   @Nullable AnActionEvent event);

  @NotNull
  public abstract String getText();

  @NotNull
  public abstract Icon getIcon();

  @NotNull
  public abstract T getValue();

  protected void triggerUsage() {}

  @NotNull
  public abstract Component getComponent(boolean isSelected);

  public void run(@NotNull Executor executor,
                  @Nullable VirtualFile workDirectory,
                  @Nullable Component component,
                  @NotNull Project project,
                  @Nullable AnActionEvent event) {
    triggerUsage();
    runInner(executor, workDirectory, component, project, event);
  }
}
