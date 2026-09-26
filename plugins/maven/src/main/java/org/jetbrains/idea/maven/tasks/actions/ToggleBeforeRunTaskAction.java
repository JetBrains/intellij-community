// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.tasks.actions;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerEx;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.tasks.MavenBeforeRunTask;
import org.jetbrains.idea.maven.tasks.MavenBeforeRunTasksProvider;
import org.jetbrains.idea.maven.utils.MavenDataKeys;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;
import org.jetbrains.idea.maven.utils.actions.MavenToggleAction;

import java.util.List;

public class ToggleBeforeRunTaskAction extends MavenToggleAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  protected boolean isAvailable(@NotNull AnActionEvent e) {
    return super.isAvailable(e) && getTaskDesc(e.getDataContext()) != null;
  }

  @Override
  protected boolean doIsSelected(AnActionEvent e) {
    final DataContext context = e.getDataContext();
    final Pair<MavenProject, String> desc = getTaskDesc(context);
    if (desc != null) {
      final RunManagerEx runManager = (RunManagerEx)getRunManager(context);
      if(runManager == null) return false;
      for (MavenBeforeRunTask each : runManager.getBeforeRunTasks(MavenBeforeRunTasksProvider.ID)) {
        if (each.isFor(desc.first, desc.second)) return true;
      }
    }
    return false;
  }

  @Override
  public void setSelected(final @NotNull AnActionEvent e, boolean state) {
    final DataContext context = e.getDataContext();
    final Pair<MavenProject, String> desc = getTaskDesc(context);
    if (desc != null) {
      new MavenExecuteBeforeRunDialog(MavenActionUtil.getProject(context), desc.first, desc.second).show();
    }
  }

  protected static @Nullable Pair<MavenProject, String> getTaskDesc(DataContext context) {
    List<String> goals = MavenDataKeys.MAVEN_GOALS.getData(context);
    if (goals == null || goals.size() != 1) return null;

    MavenProject mavenProject = MavenActionUtil.getMavenProject(context);
    if (mavenProject == null) return null;


    return Pair.create(mavenProject, goals.get(0));
  }

  private static @Nullable RunManager getRunManager(DataContext context) {
    final Project project = MavenActionUtil.getProject(context);
    if(project == null) return null;
    return RunManager.getInstance(project);
  }
}
