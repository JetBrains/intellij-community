package org.jetbrains.idea.maven.tasks.actions;

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.idea.maven.tasks.ExecuteOnRunDialog;
import org.jetbrains.idea.maven.tasks.MavenGoalTask;
import org.jetbrains.idea.maven.tasks.MavenTasksManager;
import org.jetbrains.idea.maven.tasks.TasksBundle;
import org.jetbrains.idea.maven.utils.MavenToggleAction;

public class BeforeRunAction extends MavenToggleAction {
  @Override
  protected boolean isAvailable(AnActionEvent e) {
    return super.isAvailable(e) && getMavenTask(e) != null;
  }

  @Override
  protected boolean doIsSelected(AnActionEvent e) {
    return getTasksManager(e).hasAssginments(getMavenTask(e));
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    final MavenGoalTask task = getMavenTask(e);
    final MavenTasksManager tasksManager = getTasksManager(e);

    DialogWrapper dialog = new ExecuteOnRunDialog(getProject(e), TasksBundle.message("maven.event.execute.before.run.debug")) {
      protected boolean isAssigned(ConfigurationType type, RunConfiguration configuration) {
        return task.equals(tasksManager.getAssignedTask(type, configuration));
      }

      protected void assign(ConfigurationType type, RunConfiguration configuration) {
        tasksManager.assignTask(type, configuration, task);
      }

      protected void clearAll() {
        tasksManager.clearAssignments(task);
      }
    };

    dialog.show();
    if (dialog.isOK()) {
      tasksManager.updateTaskShortcuts(task);
    }
  }

  private MavenGoalTask getMavenTask(AnActionEvent e) {
    return MavenTasksManager.getMavenTask(e.getDataContext());
  }
}
