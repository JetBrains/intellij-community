package com.intellij.openapi.vfs.encoding;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;

public class ConfigureFileEncodingAction extends AnAction {
  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);

    ConfigureFileEncodingConfigurable configurable = ConfigureFileEncodingConfigurable.getInstance(project);
    ShowSettingsUtil.getInstance().editConfigurable(project, configurable);
  }

  public void update(final AnActionEvent e) {
    e.getPresentation().setEnabled(e.getData(PlatformDataKeys.PROJECT) != null);
  }
}
