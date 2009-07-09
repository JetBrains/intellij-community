package org.jetbrains.idea.maven.project.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.actions.MavenAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtils;
import org.jetbrains.idea.maven.project.MavenProject;

public class EditProfilesXmlAction extends MavenAction {
  @Override
  protected boolean isAvailable(AnActionEvent e) {
    return super.isAvailable(e) && getProfilesFile(e) != null;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    new OpenFileDescriptor(MavenActionUtils.getProject(e), getProfilesFile(e)).navigate(true);
  }

  @Nullable
  private VirtualFile getProfilesFile(AnActionEvent e) {
    MavenProject project = MavenActionUtils.getMavenProject(e);
    return project == null ? null : project.getProfilesXmlFile();
  }
}