package org.jetbrains.idea.maven.events.actions;

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.idea.maven.events.*;

/**
 * @author Vladislav.Kaznacheev
 */
public class BeforeRunAction extends ToggleAction {
  public boolean isSelected(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final MavenTask task = MavenEventsManager.getMavenTask(e.getDataContext());
    return project != null && task != null && MavenEventsManager.getInstance(project).hasAssginments(task);
  }

  public void setSelected(AnActionEvent e, boolean ignoredState) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final MavenTask task = MavenEventsManager.getMavenTask(e.getDataContext());
    if (project != null && task != null) {
      final MavenEventsManager eventsHandler = MavenEventsManager.getInstance(project);

      final DialogWrapper dialog = new ExecuteOnRunDialog(project, EventsBundle.message("maven.event.execute.before.run.debug")) {
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
