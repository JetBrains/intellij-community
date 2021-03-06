// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.tasks.actions;

import com.intellij.execution.impl.BaseExecuteBeforeRunDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.tasks.MavenBeforeRunTask;
import org.jetbrains.idea.maven.tasks.MavenBeforeRunTasksProvider;
import org.jetbrains.idea.maven.tasks.TasksBundle;

public class MavenExecuteBeforeRunDialog extends BaseExecuteBeforeRunDialog<MavenBeforeRunTask> {
  private final MavenProject myMavenProject;
  private final String myGoal;

  public MavenExecuteBeforeRunDialog(Project project, MavenProject mavenProject, String goal) {
    super(project);
    myMavenProject = mavenProject;
    myGoal = goal;
    init();
  }

  @Override
  protected String getTargetDisplayString() {
    return TasksBundle.message("maven.tasks.goal");
  }

  @Override
  protected Key<MavenBeforeRunTask> getTaskId() {
    return MavenBeforeRunTasksProvider.ID;
  }

  @Override
  protected boolean isRunning(MavenBeforeRunTask task) {
    return task.isFor(myMavenProject, myGoal);
  }

  @Override
  protected void update(@NotNull MavenBeforeRunTask task) {
    task.setProjectPath(myMavenProject.getPath());
    task.setGoal(myGoal);
  }

  @Override
  protected void clear(MavenBeforeRunTask task) {
    task.setProjectPath(null);
    task.setGoal(null);
  }
}
