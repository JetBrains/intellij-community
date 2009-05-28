package org.jetbrains.idea.maven.utils;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class MavenAction extends AnAction implements DumbAware {
  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation().setEnabled(isAvailable(e));
  }

  protected boolean isAvailable(AnActionEvent e) {
    return getProject(e) != null;
  }

  protected Project getProject(AnActionEvent e) {
    return e.getData(PlatformDataKeys.PROJECT);
  }

  protected MavenProjectsManager getProjectsManager(AnActionEvent e) {
    return MavenProjectsManager.getInstance(getProject(e));
  }

  protected List<VirtualFile> getFiles(AnActionEvent e) {
    VirtualFile[] result = e.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
    return result == null ? Collections.<VirtualFile>emptyList() : Arrays.asList(result);
  }

  protected VirtualFile getMavenProjectFile(AnActionEvent e) {
    VirtualFile file = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    return MavenUtil.isMavenProjectFile(file) ? file : null;
  }

  protected List<MavenProject> getMavenProjects(AnActionEvent e) {
    List<MavenProject> result = new ArrayList<MavenProject>();
    for (VirtualFile each : getFiles(e)) {
      if (MavenUtil.isMavenProjectFile(each)) {
        MavenProject project = getProjectsManager(e).findProject(each);
        if (project != null) result.add(project);
      }
    }
    return result;
  }
}