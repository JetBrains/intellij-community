package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.DirectoryIndex;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author yole
 */
public class ProjectExcludedFileIndex extends ExcludedFileIndex {
  private ProjectRootManager myRootManager;
  private DirectoryIndex myDirectoryIndex;

  public ProjectExcludedFileIndex(final ProjectRootManager rootManager, final DirectoryIndex directoryIndex) {
    myRootManager = rootManager;
    myDirectoryIndex = directoryIndex;
  }

  public boolean isInContent(final VirtualFile file) {
    return myRootManager.getFileIndex().isInContent(file);
  }

  public boolean isExcludedFile(final VirtualFile file) {
    return myRootManager.getFileIndex().isIgnored(file);
  }

  public boolean isValidAncestor(final VirtualFile baseDir, VirtualFile childDir) {
    while (true) {
      if (childDir == null) return false;
      if (childDir.equals(baseDir)) return true;
      if (myDirectoryIndex.getInfoForDirectory(childDir) == null) return false;
      childDir = childDir.getParent();
    }
  }
}