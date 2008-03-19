package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author yole
 */
public abstract class ExcludedFileIndex {
  public static ExcludedFileIndex getInstance(Project project) {
    return ServiceManager.getService(project, ExcludedFileIndex.class);
  }

  public abstract boolean isInContent(VirtualFile file);
  public abstract boolean isExcludedFile(VirtualFile file);

  /**
   * Checks if <code>file</code> is an ancestor of <code>baseDir</code> and none of the files
   * between them are excluded from the project.
   *
   * @param baseDir the parent directory to check for ancestry.
   * @param childDir the child directory to check for ancestry.
   * @return true if it's a valid ancestor, false otherwise.
   */
  public abstract boolean isValidAncestor(final VirtualFile baseDir, final VirtualFile childDir);
}