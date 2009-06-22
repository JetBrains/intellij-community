package org.jetbrains.idea.maven.project.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.utils.actions.MavenAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtils;

import java.util.List;

public abstract class MavenProjectsAction extends MavenAction {
  @Override
  protected boolean isAvailable(AnActionEvent e) {
    return super.isAvailable(e) && !MavenActionUtils.getMavenProjects(e).isEmpty();
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    perform(MavenActionUtils.getProjectsManager(e), MavenActionUtils.getMavenProjects(e));
  }

  protected abstract void perform(MavenProjectsManager manager, List<MavenProject> mavenProjects);
}