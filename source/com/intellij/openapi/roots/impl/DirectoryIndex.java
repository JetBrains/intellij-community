package com.intellij.openapi.roots.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public abstract class DirectoryIndex {
  public static DirectoryIndex getInstance(Project project) {
    return project.getComponent(DirectoryIndex.class);
  }

  // for tests
  public abstract void checkConsistency();

  public abstract DirectoryInfo getInfoForDirectory(VirtualFile dir);

  public abstract VirtualFile[] getDirectoriesByPackageName(String packageName, boolean includeLibrarySources);
}
