package com.intellij.openapi.roots.impl;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

public abstract class DirectoryIndex {
  public static DirectoryIndex getInstance(Project project) {
    return ServiceManager.getService(project, DirectoryIndex.class);
  }

  @TestOnly
  public abstract void checkConsistency();

  public abstract DirectoryInfo getInfoForDirectory(VirtualFile dir);
  public abstract DirectoryInfo getInfoForDirectoryId(int dirId);

  @NotNull
  public abstract
  Query<VirtualFile> getDirectoriesByPackageName(@NotNull String packageName, boolean includeLibrarySources);
}
