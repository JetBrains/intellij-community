package org.jetbrains.idea.maven.tasks.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.idea.maven.tasks.MavenGoalTask;
import org.jetbrains.idea.maven.tasks.MavenTasksManager;
import org.jetbrains.idea.maven.utils.MavenToggleAction;

import java.util.Collection;

public abstract class IncludeExcludeTaskAction extends MavenToggleAction {
  @Override
  protected boolean isAvailable(AnActionEvent e) {
    return super.isAvailable(e) && getAllTasks(e) != null && getTaskToChange(e) != null;
  }

  @Override
  protected boolean doIsSelected(AnActionEvent e) {
    return getAllTasks(e).contains(getTaskToChange(e));
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    Collection<MavenGoalTask> all = getAllTasks(e);
    MavenGoalTask task = getTaskToChange(e);
    if (state) {
      all.add(task);
    }
    else {
      all.remove(task);
    }
    getTasksManager(e).fireTaskShortcutsUpdated(task);
  }

  protected MavenGoalTask getTaskToChange(AnActionEvent e) {
    return MavenTasksManager.getMavenTask(e.getDataContext());
  }

  protected Collection<MavenGoalTask> getAllTasks(AnActionEvent e) {
    return getAllTasks(getTasksManager(e));
  }

  protected abstract Collection<MavenGoalTask> getAllTasks(MavenTasksManager tasksManager);
}
