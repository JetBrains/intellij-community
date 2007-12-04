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

  public abstract boolean isExcludedFile(VirtualFile file);
}