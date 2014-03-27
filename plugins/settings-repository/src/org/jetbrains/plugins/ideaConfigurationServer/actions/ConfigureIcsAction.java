package org.jetbrains.plugins.ideaConfigurationServer.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.plugins.ideaConfigurationServer.IcsSettingsPanel;

class ConfigureIcsAction extends DumbAwareAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    new IcsSettingsPanel(e.getProject()).show();
  }
}
