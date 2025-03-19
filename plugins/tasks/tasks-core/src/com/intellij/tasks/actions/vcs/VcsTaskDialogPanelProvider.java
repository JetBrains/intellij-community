// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.actions.vcs;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.LocalTask;
import com.intellij.tasks.TaskManager;
import com.intellij.tasks.ui.TaskDialogPanel;
import com.intellij.tasks.ui.TaskDialogPanelProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public class VcsTaskDialogPanelProvider extends TaskDialogPanelProvider {

  @Override
  public @Nullable TaskDialogPanel getOpenTaskPanel(@NotNull Project project, @NotNull LocalTask task) {
    return TaskManager.getManager(project).isVcsEnabled() ? new VcsOpenTaskPanel(project, task) : null;
  }

  @Override
  public @Nullable TaskDialogPanel getCloseTaskPanel(@NotNull Project project, @NotNull LocalTask task) {
    return TaskManager.getManager(project).isVcsEnabled() ? new VcsCloseTaskPanel(project, task) : null;
  }
}
