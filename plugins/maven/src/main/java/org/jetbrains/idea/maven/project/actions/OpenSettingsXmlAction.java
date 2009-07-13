package org.jetbrains.idea.maven.project.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

import java.util.Collections;
import java.util.List;

public class OpenSettingsXmlAction extends MavenOpenFilesAction {
  protected List<VirtualFile> getFiles(AnActionEvent e) {
    VirtualFile file = MavenActionUtil.getProjectsManager(e).getGeneralSettings().getEffectiveUserSettingsFile();
    return file != null ? Collections.singletonList(file) : Collections.<VirtualFile>emptyList();
  }
}
