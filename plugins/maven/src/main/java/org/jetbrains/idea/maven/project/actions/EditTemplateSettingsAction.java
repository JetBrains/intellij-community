package org.jetbrains.idea.maven.project.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.ProjectManager;

public class EditTemplateSettingsAction extends EditSettingsAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    showSettingsFor(ProjectManager.getInstance().getDefaultProject());
  }
}