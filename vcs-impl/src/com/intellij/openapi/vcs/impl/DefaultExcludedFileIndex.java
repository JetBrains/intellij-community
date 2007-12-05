package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.PlatformProjectOpenProcessor;

/**
 * @author yole
 */
public class DefaultExcludedFileIndex extends ExcludedFileIndex {
  private VirtualFile myBaseDir;

  public DefaultExcludedFileIndex(final Project project) {
    myBaseDir = project.getBaseDir();
  }

  public boolean isInContent(final VirtualFile file) {
    return VfsUtil.isAncestor(getBaseDir(), file, false);
  }

  public boolean isExcludedFile(final VirtualFile file) {
    return false;
  }

  private VirtualFile getBaseDir() {
    if (PlatformProjectOpenProcessor.BASE_DIR != null) {
      return PlatformProjectOpenProcessor.BASE_DIR;
    }
    return myBaseDir;
  }
}
