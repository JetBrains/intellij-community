package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author yole
 */
public class ProjectExcludedFileIndex extends ExcludedFileIndex {
  private ProjectRootManager myRootManager;

  public ProjectExcludedFileIndex(final ProjectRootManager rootManager) {
    myRootManager = rootManager;
  }

  public boolean isInContent(final VirtualFile file) {
    return myRootManager.getFileIndex().isInContent(file);
  }

  public boolean isExcludedFile(final VirtualFile file) {
    return myRootManager.getFileIndex().isIgnored(file);
  }
}