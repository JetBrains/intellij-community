package org.jetbrains.idea.maven.events.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.keymap.impl.ui.EditKeymapsDialog;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.events.MavenEventsHandler;
import org.jetbrains.idea.maven.events.MavenEventsState;
import org.jetbrains.idea.maven.events.MavenTask;

/**
 * @author Vladislav.Kaznacheev
 */
public class AssignShortcutAction extends AnAction {

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    if(project!=null){
      final String actionId = getGoalActionId(e, project);
      if(actionId != null){
        new EditKeymapsDialog(project, actionId).show();
      }
    }
  }

  public void update(AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    final String actionId = getGoalActionId(e, project);
    e.getPresentation().setEnabled(project != null && actionId != null);
  }

  @Nullable
  private static String getGoalActionId(final AnActionEvent e, final Project project) {
    final MavenTask task = MavenEventsState.getMavenTask(e.getDataContext());
    if(task!=null){
      return project.getComponent(MavenEventsHandler.class).getActionId(task.pomPath, task.goal);
    }
    else {
      return null;
    }
  }
}

