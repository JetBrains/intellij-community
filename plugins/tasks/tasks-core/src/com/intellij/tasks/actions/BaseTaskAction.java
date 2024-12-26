// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  protected BaseTaskAction(final @Nullable @NlsActions.ActionText String text,
                           final @Nullable @NlsActions.ActionDescription String description,
                           final @Nullable Icon icon) {
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

  public static @Nullable Project getProject(@Nullable AnActionEvent event) {
    return event == null ? null : event.getProject();
  }

  public static @Nullable TaskManager getTaskManager(AnActionEvent event) {
    Project project = getProject(event);
    if (project == null) {
      return null;
    }
    return TaskManager.getManager(project);
  }

  public static @Nullable LocalTask getActiveTask(AnActionEvent event) {
    TaskManager manager = getTaskManager(event);
    return manager == null ? null : manager.getActiveTask();
  }
}
