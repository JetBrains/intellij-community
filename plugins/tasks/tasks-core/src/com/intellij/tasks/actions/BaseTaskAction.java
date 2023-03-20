// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tasks.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.TaskManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public abstract class BaseTaskAction extends AnAction implements DumbAware {

  protected BaseTaskAction() {
  }

  protected BaseTaskAction(@NlsActions.ActionText String text) {
    super(text);
  }

  protected BaseTaskAction(@Nullable @NlsActions.ActionText final String text,
                           @Nullable @NlsActions.ActionDescription final String description,
                           @Nullable final Icon icon) {
    super(text, description, icon);
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    event.getPresentation().setEnabled(getProject(event) != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Nullable
  public static Project getProject(@Nullable AnActionEvent event) {
    return event == null ? null : event.getProject();
  }

  @Nullable
  public static TaskManager getTaskManager(AnActionEvent event) {
    Project project = getProject(event);
    if (project == null) {
      return null;
    }
    return TaskManager.getManager(project);
  }

  @Nullable
  public static LocalTask getActiveTask(AnActionEvent event) {
    TaskManager manager = getTaskManager(event);
    return manager == null ? null : manager.getActiveTask();
  }
}
