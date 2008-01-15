package com.intellij.openapi.roots.impl;

import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

public class ProjectPackageIndexImpl extends PackageIndex {
  private DirectoryIndex myDirectoryIndex;

  public ProjectPackageIndexImpl(DirectoryIndex directoryIndex) {
    myDirectoryIndex = directoryIndex;
  }

  public VirtualFile[] getDirectoriesByPackageName(@NotNull String packageName, boolean includeLibrarySources) {
    return getDirsByPackageName(packageName, includeLibrarySources).toArray(VirtualFile.EMPTY_ARRAY);
  }

  public Query<VirtualFile> getDirsByPackageName(@NotNull String packageName, boolean includeLibrarySources) {
    return myDirectoryIndex.getDirectoriesByPackageName(packageName, includeLibrarySources);
  }

}
