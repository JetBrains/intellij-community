package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.List;

public class RootsCalculator {
  private final static Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.committed.RootsCalculator");
  private final Project myProject;
  private final AbstractVcs myVcs;
  private final ProjectLevelVcsManager myPlManager;
  private VirtualFile[] myContentRoots;

  public RootsCalculator(final Project project, final AbstractVcs vcs) {
    myProject = project;
    myPlManager = ProjectLevelVcsManager.getInstance(myProject);
    myVcs = vcs;
  }

  public List<VirtualFile> getRoots() {
    myContentRoots = myPlManager.getRootsUnderVcs(myVcs);

    List<VirtualFile> roots = new ArrayList<VirtualFile>();
    final List<VcsDirectoryMapping> mappings = myPlManager.getDirectoryMappings(myVcs);
    for (VcsDirectoryMapping mapping : mappings) {
      final VirtualFile newFile = mapping.isDefaultMapping() ? myProject.getBaseDir() :
                                  LocalFileSystem.getInstance().findFileByPath(mapping.getDirectory());
      roots.add(newFile);
    }
    for (VirtualFile contentRoot : myContentRoots) {
      roots.add(contentRoot);
    }
    roots = myVcs.filterUniqueRoots(roots);

    logRoots(roots);
    return roots;
  }

  private void logRoots(final List<VirtualFile> roots) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Roots for committed changes load:\n");
      for (VirtualFile root : roots) {
        LOG.debug(root.getPath() + ", ");
      }
    }
  }

  public VirtualFile[] getContentRoots() {
    return myContentRoots;
  }
}
