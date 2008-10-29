package org.jetbrains.idea.maven.project.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.ProjectManager;

public class EditDefaultSettingsAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    EditSettingsAction.showSettingsFor(ProjectManager.getInstance().getDefaultProject());
  }
}