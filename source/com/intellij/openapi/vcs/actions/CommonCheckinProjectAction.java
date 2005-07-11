package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;


public class CommonCheckinProjectAction extends AbstractCommonCheckinAction {

  protected FilePath[] getRoots(final VcsContext context) {
    Project project = context.getProject();
    ArrayList<FilePath> virtualFiles = new ArrayList<FilePath>();
    VirtualFile[] roots = ProjectRootManager.getInstance(project).getContentRoots();
    for (int i = 0; i < roots.length; i++) {
      VirtualFile root = roots[i];
      AbstractVcs vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(root);
      if (vcs == null) continue;
      CheckinEnvironment checkinEnvironment = vcs.getCheckinEnvironment();
      if (checkinEnvironment == null) continue;
      virtualFiles.add(new FilePathImpl(root));
    }
    return virtualFiles.toArray(new FilePath[virtualFiles.size()]);
  }

  protected String getActionName(VcsContext dataContext) {
    return "Commit Project";
  }

  protected boolean shouldShowDialog(VcsContext context) {
    return true;
  }

  protected boolean filterRootsBeforeAction() {
    return false;
  }
}
