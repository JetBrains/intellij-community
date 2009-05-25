package org.jetbrains.idea.maven.tasks.actions;

import org.jetbrains.idea.maven.tasks.MavenCompilerTask;
import org.jetbrains.idea.maven.tasks.MavenTasksManager;

import java.util.List;

public class ToggleAfterCompileTasksAction extends ToggleCompilerTasksAction {
  protected boolean hasTask(MavenTasksManager manager, MavenCompilerTask task) {
    return manager.isAfterCompileTask(task);
  }

  protected void addTasks(MavenTasksManager manager, List<MavenCompilerTask> tasks) {
    manager.addAfterCompileTasks(tasks);
  }

  protected void removeTasks(MavenTasksManager manager, List<MavenCompilerTask> tasks) {
    manager.removeAfterCompileTasks(tasks);
  }
}