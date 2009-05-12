package org.jetbrains.idea.maven.project.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenAction;

public abstract class MavenProjectsManagerAction extends MavenAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    perform(getProjectsManager(e));
  }

  protected abstract void perform(MavenProjectsManager manager);
}
