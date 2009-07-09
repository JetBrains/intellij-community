package org.jetbrains.idea.maven.project.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.actions.MavenAction;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtils;

public class EditSettingsXmlAction extends MavenAction {
  @Override
  protected boolean isAvailable(AnActionEvent e) {
    return super.isAvailable(e) && getSettingsFile(e) != null;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    new OpenFileDescriptor(MavenActionUtils.getProject(e), getSettingsFile(e)).navigate(true);
  }

  @Nullable
  private VirtualFile getSettingsFile(AnActionEvent e) {
    return MavenActionUtils.getProjectsManager(e).getGeneralSettings().getEffectiveUserSettingsFile();
  }
}
