package org.jetbrains.idea.maven.tasks.actions;

import org.jetbrains.idea.maven.tasks.MavenCompilerTask;
import org.jetbrains.idea.maven.tasks.MavenTasksManager;

import java.util.List;

public class ToggleBeforeCompileTasksAction extends ToggleCompilerTasksAction {
  protected boolean hasTask(MavenTasksManager manager, MavenCompilerTask task) {
    return manager.isBeforeCompileTask(task);
  }

  protected void addTasks(MavenTasksManager manager, List<MavenCompilerTask> tasks) {
    manager.addBeforeCompileTasks(tasks);
  }

  protected void removeTasks(MavenTasksManager manager, List<MavenCompilerTask> tasks) {
    manager.removeBeforeCompileTasks(tasks);
  }
}
