package com.intellij.openapi.vcs.readOnlyHandler;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class VcsHandleTypeFactory implements HandleTypeFactory {
  private Project myProject;

  public VcsHandleTypeFactory(final Project project) {
    myProject = project;
  }

  @Nullable
  public HandleType createHandleType(final VirtualFile file) {
    AbstractVcs vcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(file);
    if (vcs != null) {
      boolean fileExistsInVcs = vcs.fileExistsInVcs(new FilePathImpl(file));
      if (fileExistsInVcs && vcs.getEditFileProvider() != null) {
        return new VcsHandleType(vcs);
      }
    }
    return null;
  }
}
