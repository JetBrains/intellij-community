package org.jetbrains.idea.maven.project.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.utils.MavenAction;

import java.util.List;

public abstract class MavenProjectsAction extends MavenAction {
  @Override
  protected boolean isAvailable(AnActionEvent e) {
    return super.isAvailable(e) && !getMavenProjects(e).isEmpty();
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    perform(getProjectsManager(e), getMavenProjects(e));
  }

  protected abstract void perform(MavenProjectsManager manager, List<MavenProject> mavenProjects);
}