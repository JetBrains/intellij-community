package org.jetbrains.idea.maven.tasks.actions;

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.idea.maven.tasks.ExecuteOnRunDialog;
import org.jetbrains.idea.maven.tasks.MavenTask;
import org.jetbrains.idea.maven.tasks.MavenTasksManager;
import org.jetbrains.idea.maven.tasks.TasksBundle;
import org.jetbrains.idea.maven.utils.MavenToggleAction;

/**
 * @author Vladislav.Kaznacheev
 */
public class BeforeRunAction extends MavenToggleAction {
  public boolean isSelected(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final MavenTask task = MavenTasksManager.getMavenTask(e.getDataContext());
    return project != null && task != null && MavenTasksManager.getInstance(project).hasAssginments(task);
  }

  public void setSelected(AnActionEvent e, boolean ignoredState) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final MavenTask task = MavenTasksManager.getMavenTask(e.getDataContext());
    if (project != null && task != null) {
      final MavenTasksManager eventsHandler = MavenTasksManager.getInstance(project);

      final DialogWrapper dialog = new ExecuteOnRunDialog(project, TasksBundle.message("maven.event.execute.before.run.debug")) {
        protected boolean isAssigned(ConfigurationType type, RunConfiguration configuration) {
          return task.equals(eventsHandler.getAssignedTask(type, configuration));
        }

        protected void assign(final ConfigurationType type, RunConfiguration configuration) {
          eventsHandler.assignTask(type, configuration, task);
        }

        protected void clearAll() {
          eventsHandler.clearAssignments(task);
        }
      };

      dialog.show();
      if (dialog.isOK()) {
        eventsHandler.updateTaskShortcuts(task);
        update(e);
      }
    }
  }
}
