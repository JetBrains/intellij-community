package com.intellij.openapi.roots.impl;

import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.Condition;
import com.intellij.util.Query;
import com.intellij.util.FilteredQuery;
import org.jetbrains.annotations.NotNull;

public class ModulePackageIndexImpl extends PackageIndex {
  private ModuleFileIndexImpl myModuleFileIndex;
  private DirectoryIndex myDirectoryIndex;

  public ModulePackageIndexImpl(ModuleFileIndexImpl moduleFileIndex, DirectoryIndex directoryIndex) {
    myModuleFileIndex = moduleFileIndex;
    myDirectoryIndex = directoryIndex;
  }

  private final Condition<VirtualFile> myDirCondition = new Condition<VirtualFile>() {
    public boolean value(final VirtualFile dir) {
      return myModuleFileIndex.getOrderEntryForFile(dir) != null;
    }
  };

  public Query<VirtualFile> getDirsByPackageName(@NotNull String packageName, boolean includeLibrarySources) {
    return new FilteredQuery<VirtualFile>(myDirectoryIndex.getDirectoriesByPackageName(packageName, includeLibrarySources), myDirCondition);
  }

  public VirtualFile[] getDirectoriesByPackageName(@NotNull String packageName, boolean includeLibrarySources) {
    return getDirsByPackageName(packageName, includeLibrarySources).toArray(VirtualFile.EMPTY_ARRAY);
  }
}
