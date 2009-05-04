package org.jetbrains.idea.maven.project.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.ProjectManager;

public class EditTemplateSettingsAction extends EditSettingsAction {
  public void actionPerformed(AnActionEvent e) {
    showSettingsFor(ProjectManager.getInstance().getDefaultProject());
  }
}