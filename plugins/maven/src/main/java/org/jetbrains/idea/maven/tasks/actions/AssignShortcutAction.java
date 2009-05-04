package org.jetbrains.idea.maven.tasks.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.keymap.impl.ui.EditKeymapsDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.tasks.MavenTasksManager;
import org.jetbrains.idea.maven.utils.MavenAction;
import org.jetbrains.idea.maven.utils.MavenConstants;
import org.jetbrains.idea.maven.utils.MavenDataKeys;

import java.util.List;

public class AssignShortcutAction extends MavenAction {
  public void update(AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);
    String actionId = getGoalActionId(e, project);
    e.getPresentation().setEnabled(project != null && actionId != null);
  }

  public void actionPerformed(AnActionEvent e) {
    Project project = e.getData(PlatformDataKeys.PROJECT);

    String actionId = getGoalActionId(e, project);
    if (actionId != null) {
      new EditKeymapsDialog(project, actionId).show();
    }
  }

  @Nullable
  private static String getGoalActionId(AnActionEvent e, Project project) {
    VirtualFile file = PlatformDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
    if (file == null || !MavenConstants.POM_XML.equals(file.getName())) return null;

    List<String> goals = MavenDataKeys.MAVEN_GOALS_KEY.getData(e.getDataContext());
    String goal = (goals == null || goals.size() != 1) ? null : goals.get(0);
    return MavenTasksManager.getInstance(project).getActionId(file.getPath(), goal);
  }
}

