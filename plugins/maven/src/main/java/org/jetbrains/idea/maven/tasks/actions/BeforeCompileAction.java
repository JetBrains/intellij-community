package org.jetbrains.idea.maven.tasks.actions;

import org.jetbrains.idea.maven.tasks.MavenTasksManager;
import org.jetbrains.idea.maven.tasks.MavenTask;

import java.util.Collection;


public class BeforeCompileAction extends IncludeExcludeTaskAction {
  @Override
  protected Collection<MavenTask> getAllTasks(MavenTasksManager tasksManager) {
    return tasksManager.getState().beforeCompile;
  }
}
