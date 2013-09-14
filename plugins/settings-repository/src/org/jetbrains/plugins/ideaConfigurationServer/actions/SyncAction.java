package org.jetbrains.plugins.ideaConfigurationServer.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.plugins.ideaConfigurationServer.IcsManager;

public class SyncAction extends DumbAwareAction {
  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setVisible(IcsManager.getInstance().getRepositoryManager().getRemoteRepositoryUrl() != null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    IcsManager.getInstance().sync();
  }
}
