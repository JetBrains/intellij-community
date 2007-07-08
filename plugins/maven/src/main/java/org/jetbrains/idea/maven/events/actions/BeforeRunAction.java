package org.jetbrains.idea.maven.events.actions;

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.idea.maven.events.*;

/**
 * @author Vladislav.Kaznacheev
 */
public class BeforeRunAction extends ToggleAction {

  public boolean isSelected(final AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    final MavenTask task = MavenEventsState.getMavenTask(e.getDataContext());
    return project != null && task != null && project.getComponent(MavenEventsHandler.class).getState().hasAssginments(task);
  }

  public void setSelected(AnActionEvent e, boolean ignoredState) {
    final Project project = e.getData(DataKeys.PROJECT);
    final MavenTask task = MavenEventsState.getMavenTask(e.getDataContext());
    if (project != null && task != null) {
      final MavenEventsHandler eventsHandler = project.getComponent(MavenEventsHandler.class);
      final MavenEventsState state = eventsHandler.getState();

      final DialogWrapper dialog = new ExecuteOnRunDialog(project, EventsBundle.message("maven.event.execute.before.run.debug")) {
        protected boolean isAssigned(ConfigurationType type, String configurationName) {
          return task.equals(state.getAssignedTask(type, configurationName));
        }

        protected void assign(final ConfigurationType type, final String configurationName) {
          state.assignTask(type, configurationName, task);
        }

        protected void clearAll() {
          state.clearAssignments(task);
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