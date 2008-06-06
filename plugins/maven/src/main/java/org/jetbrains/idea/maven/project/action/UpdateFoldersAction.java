package org.jetbrains.idea.maven.project.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import org.jetbrains.idea.maven.state.MavenProjectsManager;

public class UpdateFoldersAction extends AnAction {
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabled(e.getData(PlatformDataKeys.PROJECT) != null);
  }

  public void actionPerformed(AnActionEvent e) {
    MavenProjectsManager.getInstance(e.getData(PlatformDataKeys.PROJECT)).updateFolders();
  }
}