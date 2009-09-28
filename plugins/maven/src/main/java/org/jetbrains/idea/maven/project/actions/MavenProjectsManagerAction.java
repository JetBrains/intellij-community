package org.jetbrains.idea.maven.project.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.actions.MavenAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

public abstract class MavenProjectsManagerAction extends MavenAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    perform(MavenActionUtil.getProjectsManager(e));
  }

  protected abstract void perform(MavenProjectsManager manager);
}
