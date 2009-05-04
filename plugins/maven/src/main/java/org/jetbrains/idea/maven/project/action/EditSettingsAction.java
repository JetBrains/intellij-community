package org.jetbrains.idea.maven.project.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import org.jetbrains.idea.maven.utils.MavenSettings;
import org.jetbrains.idea.maven.utils.MavenAction;

public class EditSettingsAction extends MavenAction {
  public void actionPerformed(AnActionEvent e) {
    showSettingsFor(e.getData(PlatformDataKeys.PROJECT));
  }

  protected void showSettingsFor(Project project) {
    MavenSettings configurable = ShowSettingsUtil.getInstance().findProjectConfigurable(project, MavenSettings.class);
    ShowSettingsUtil.getInstance().showSettingsDialog(project, configurable);
  }
}