package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;


public class CommonCheckinProjectAction extends AbstractCommonCheckinAction {

  protected FilePath[] getRoots(final VcsContext context) {
    Project project = context.getProject();
    ArrayList<FilePath> virtualFiles = new ArrayList<FilePath>();
    final ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    for(AbstractVcs vcs: vcsManager.getAllActiveVcss()) {
      if (vcs.getCheckinEnvironment() != null) {
        VirtualFile[] roots = vcsManager.getRootsUnderVcs(vcs);
        for (VirtualFile root : roots) {
          virtualFiles.add(new FilePathImpl(root));
        }
      }
    }
    return virtualFiles.toArray(new FilePath[virtualFiles.size()]);
  }

  protected String getActionName(VcsContext dataContext) {
    return VcsBundle.message("action.name.commit.project");
  }

  protected boolean filterRootsBeforeAction() {
    return false;
  }
}
