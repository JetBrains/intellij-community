package org.jetbrains.idea.maven.tasks.actions;

import org.jetbrains.idea.maven.tasks.MavenTasksManager;
import org.jetbrains.idea.maven.tasks.MavenGoalTask;

import java.util.Collection;


public class BeforeCompileAction extends IncludeExcludeTaskAction {
  @Override
  protected Collection<MavenGoalTask> getAllTasks(MavenTasksManager tasksManager) {
    return tasksManager.getState().beforeCompile;
  }
}
