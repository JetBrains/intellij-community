package org.jetbrains.idea.maven.project.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class OpenOrCreateProfilesXmlAction extends MavenOpenOrCreateFilesAction {
  protected List<File> getFiles(AnActionEvent e) {
    List<File> result = new ArrayList<File>();
    for (MavenProject each : MavenActionUtil.getMavenProjects(e)) {
      result.add(each.getProfilesXmlIoFile());
    }
    return result;
  }
}