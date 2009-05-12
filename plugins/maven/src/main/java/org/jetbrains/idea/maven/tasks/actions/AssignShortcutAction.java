package org.jetbrains.idea.maven.tasks.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.keymap.impl.ui.EditKeymapsDialog;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.MavenAction;
import org.jetbrains.idea.maven.utils.MavenConstants;
import org.jetbrains.idea.maven.utils.MavenDataKeys;

import java.util.List;

public class AssignShortcutAction extends MavenAction {
  @Override
  protected boolean isAvailable(AnActionEvent e) {
    return super.isAvailable(e) && getGoalActionId(e) != null;
  }

  public void actionPerformed(AnActionEvent e) {
    String actionId = getGoalActionId(e);
    if (actionId != null) {
      new EditKeymapsDialog(getProject(e), actionId).show();
    }
  }

  @Nullable
  private String getGoalActionId(AnActionEvent e) {
    VirtualFile file = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    if (file == null || !MavenConstants.POM_XML.equals(file.getName())) return null;

    List<String> goals = e.getData(MavenDataKeys.MAVEN_GOALS);
    String goal = (goals == null || goals.size() != 1) ? null : goals.get(0);

    return getTasksManager(e).getActionId(file.getPath(), goal);
  }
}

