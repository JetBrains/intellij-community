package com.intellij.openapi.roots.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public abstract class DirectoryIndex {
  public static DirectoryIndex getInstance(Project project) {
    return project.getComponent(DirectoryIndex.class);
  }

  // for tests
  public abstract void checkConsistency();

  public abstract DirectoryInfo getInfoForDirectory(VirtualFile dir);

  public abstract @NotNull VirtualFile[] getDirectoriesByPackageName(@NotNull String packageName, boolean includeLibrarySources);
}
