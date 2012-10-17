package com.intellij.tasks.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.tasks.config.TaskRepositoriesConfigurable;

/**
 * User: Evgeny Zakrevsky
 */

public class ConfigureServersAction extends BaseTaskAction {

  public ConfigureServersAction() {
    super("Configure Servers...", null, AllIcons.General.Settings);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    TaskRepositoriesConfigurable configurable = new TaskRepositoriesConfigurable(getProject(e));
    if (ShowSettingsUtil.getInstance().editConfigurable(getProject(e), configurable)) {
      serversChanged();
    }
  }

  protected void serversChanged() {

  }
}
