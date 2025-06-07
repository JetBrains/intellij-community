// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.tasks.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.tasks.MavenCompilerTask;
import org.jetbrains.idea.maven.tasks.MavenTasksManager;
import org.jetbrains.idea.maven.utils.MavenDataKeys;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;
import org.jetbrains.idea.maven.utils.actions.MavenToggleAction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class ToggleCompilerTasksAction extends MavenToggleAction {

  private final MavenTasksManager.Phase myPhase;

  protected ToggleCompilerTasksAction(MavenTasksManager.Phase phase) {
    myPhase = phase;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  protected boolean isAvailable(@NotNull AnActionEvent e) {
    return super.isAvailable(e) && !getTasks(e.getDataContext()).isEmpty();
  }

  @Override
  protected boolean doIsSelected(AnActionEvent e) {
    final DataContext context = e.getDataContext();
    return hasTask(getTasksManager(context), getTasks(context).get(0));
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    final DataContext context = e.getDataContext();
    final MavenTasksManager tasksManager = getTasksManager(context);
    List<MavenCompilerTask> tasks = getTasks(context);
    if(tasksManager == null) return;
    if (state) {
      addTasks(tasksManager, tasks);
    }
    else {
      removeTasks(tasksManager, tasks);
    }
  }

  protected static List<MavenCompilerTask> getTasks(DataContext context) {
    final List<String> goals = MavenDataKeys.MAVEN_GOALS.getData(context);
    if (goals == null || goals.isEmpty()) return Collections.emptyList();

    MavenProject project = MavenActionUtil.getMavenProject(context);
    if (project == null) return Collections.emptyList();

    List<MavenCompilerTask> result = new ArrayList<>();
    for (String each : goals) {
      result.add(new MavenCompilerTask(project.getPath(), each));
    }
    return result;
  }

  protected boolean hasTask(@Nullable MavenTasksManager manager, MavenCompilerTask task) {
    if(manager == null) return false;
    return manager.isCompileTaskOfPhase(task, myPhase);
  }

  protected void addTasks(MavenTasksManager manager, List<MavenCompilerTask> tasks) {
    manager.addCompileTasks(tasks, myPhase);
  }

  protected void removeTasks(MavenTasksManager manager, List<MavenCompilerTask> tasks) {
    manager.removeCompileTasks(tasks, myPhase);
  }

  private static @Nullable MavenTasksManager getTasksManager(DataContext context) {
    final Project project = MavenActionUtil.getProject(context);
    if(project == null) return null;
    return MavenTasksManager.getInstance(project);
  }
}
