package org.jetbrains.idea.maven.utils.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenConstants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MavenActionUtils {
  public static Project getProject(AnActionEvent e) {
    return e.getData(PlatformDataKeys.PROJECT);
  }

  public static MavenProjectsManager getProjectsManager(AnActionEvent e) {
    return MavenProjectsManager.getInstance(getProject(e));
  }

  public static List<VirtualFile> getFiles(AnActionEvent e) {
    VirtualFile[] result = e.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
    return result == null ? Collections.<VirtualFile>emptyList() : Arrays.asList(result);
  }

  public static MavenProject getMavenProject(AnActionEvent e) {
    VirtualFile file = getMavenProjectFile(e);
    return file == null ? null : getProjectsManager(e).findProject(file);
  }

  public static boolean isMavenProjectFile(AnActionEvent e) {
    VirtualFile file = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    return file == null ? false : isMavenProjectFile(file);
  }

  public static boolean isMavenProjectFile(VirtualFile file) {
    return file != null && !file.isDirectory() && MavenConstants.POM_XML.equals(file.getName());
  }

  public static VirtualFile getMavenProjectFile(AnActionEvent e) {
    VirtualFile file = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    return isMavenProjectFile(file) ? file : null;
  }

  public static List<MavenProject> getMavenProjects(AnActionEvent e) {
    List<MavenProject> result = new ArrayList<MavenProject>();
    for (VirtualFile each : getFiles(e)) {
      if (isMavenProjectFile(each)) {
        MavenProject project = getProjectsManager(e).findProject(each);
        if (project != null) result.add(project);
      }
    }
    return result;
  }
}
