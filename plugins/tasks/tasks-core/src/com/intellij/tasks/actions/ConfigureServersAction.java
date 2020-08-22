package com.intellij.tasks.actions;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.config.TaskRepositoriesConfigurable;
import org.jetbrains.annotations.NotNull;


public class ConfigureServersAction extends BaseTaskAction {

  public ConfigureServersAction() {
    super(TaskBundle.message("configure.servers.action.menu.text"), null, AllIcons.General.Settings);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    TaskRepositoriesConfigurable configurable = new TaskRepositoriesConfigurable(getProject(e));
    if (ShowSettingsUtil.getInstance().editConfigurable(getProject(e), configurable)) {
      serversChanged();
    }
  }

  protected void serversChanged() {

  }
}
