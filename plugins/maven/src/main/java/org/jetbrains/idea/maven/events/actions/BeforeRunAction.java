package org.jetbrains.idea.maven.events.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.idea.maven.events.MavenEventsHandler;
import org.jetbrains.idea.maven.events.MavenEventsState;
import org.jetbrains.idea.maven.events.MavenExecuteOnRunDialog;
import org.jetbrains.idea.maven.events.MavenTask;

/**
 * @author Vladislav.Kaznacheev
 */
public class BeforeRunAction extends ToggleAction {

  public boolean isSelected(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final MavenTask task = MavenEventsState.getMavenTask(e.getDataContext());
    return project != null && task != null && project.getComponent(MavenEventsHandler.class).getState().hasAssginments(task);
  }

  public void setSelected(AnActionEvent e, boolean ignoredState) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final MavenTask task = MavenEventsState.getMavenTask(e.getDataContext());
    if (project != null && task != null) {
      DialogWrapper dialog = new MavenExecuteOnRunDialog(project, task);
      dialog.show();
      if (dialog.isOK()) {
        project.getComponent(MavenEventsHandler.class).updateTaskShortcuts(task);
        update(e);
      }
    }
  }
}