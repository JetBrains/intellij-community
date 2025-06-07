// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.navigator.actions;

import com.intellij.ide.actions.runAnything.RunAnythingManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import static org.jetbrains.idea.maven.execution.MavenRunAnythingProvider.HELP_COMMAND;

public class MavenExecuteGoalAction extends DumbAwareAction {
  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) return;
    RunAnythingManager runAnythingManager = RunAnythingManager.getInstance(project);
    runAnythingManager.show(HELP_COMMAND + " ", false, e);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
