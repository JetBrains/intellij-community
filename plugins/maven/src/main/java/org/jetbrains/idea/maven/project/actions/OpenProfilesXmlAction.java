package org.jetbrains.idea.maven.project.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

import java.util.ArrayList;
import java.util.List;

public class OpenProfilesXmlAction extends MavenOpenFilesAction {
  protected List<VirtualFile> getFiles(AnActionEvent e) {
    List<VirtualFile> result = new ArrayList<VirtualFile>();
    for (MavenProject each : MavenActionUtil.getMavenProjects(e)) {
      VirtualFile file = each.getProfilesXmlFile();
      if (file != null) result.add(file);
    }
    return result;
  }
}