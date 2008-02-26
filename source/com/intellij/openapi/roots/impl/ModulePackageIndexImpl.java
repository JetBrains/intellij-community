package com.intellij.openapi.roots.impl;

import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModulePackageIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.FilteredQuery;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

public class ModulePackageIndexImpl extends ModulePackageIndex {
  private final ModuleFileIndex myModuleFileIndex;
  private final DirectoryIndex myDirectoryIndex;

  public ModulePackageIndexImpl(ModuleRootManager moduleRootManager, DirectoryIndex directoryIndex) {
    myModuleFileIndex = moduleRootManager.getFileIndex();
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
