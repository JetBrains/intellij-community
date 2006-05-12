package com.intellij.openapi.roots.impl;

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ModuleFileIndexImpl implements ModuleFileIndex {
  private final Module myModule;
  private final FileTypeManager myFileTypeManager;
  private final DirectoryIndex myDirectoryIndex;
  private final ContentFilter myContentFilter;

  public ModuleFileIndexImpl(Module module, DirectoryIndex directoryIndex) {
    myModule = module;
    myDirectoryIndex = directoryIndex;
    myFileTypeManager = FileTypeManager.getInstance();

    myContentFilter = new ContentFilter();
  }

  public boolean iterateContent(@NotNull ContentIterator iterator) {
    VirtualFile[] contentRoots = ModuleRootManager.getInstance(myModule).getContentRoots();
    for (VirtualFile contentRoot : contentRoots) {
      VirtualFile parent = contentRoot.getParent();
      if (parent != null) {
        DirectoryInfo parentInfo = myDirectoryIndex.getInfoForDirectory(parent);
        if (parentInfo != null && myModule.equals(parentInfo.module)) continue; // inner content - skip it
      }

      boolean finished = FileIndexImplUtil.iterateRecursively(contentRoot, myContentFilter, iterator);
      if (!finished) return false;
    }

    return true;
  }

  public boolean iterateContentUnderDirectory(@NotNull VirtualFile dir, @NotNull ContentIterator iterator) {
    return FileIndexImplUtil.iterateRecursively(dir, myContentFilter, iterator);
  }

  public boolean isContentJavaSourceFile(@NotNull VirtualFile file) {
    if (file.isDirectory()) return false;
    if (myFileTypeManager.getFileTypeByFile(file) != StdFileTypes.JAVA) return false;
    if (myFileTypeManager.isFileIgnored(file.getName())) return false;
    return isInSourceContent(file);
  }

  public boolean isInContent(@NotNull VirtualFile fileOrDir) {
    if (fileOrDir.isDirectory()) {
      DirectoryInfo info = myDirectoryIndex.getInfoForDirectory(fileOrDir);
      return info != null && myModule.equals(info.module);
    }
    else {
      VirtualFile parent = fileOrDir.getParent();
      if (parent == null) return false;
      return isInContent(parent);
    }
  }

  public boolean isInSourceContent(@NotNull VirtualFile fileOrDir) {
    if (fileOrDir.isDirectory()) {
      DirectoryInfo info = myDirectoryIndex.getInfoForDirectory(fileOrDir);
      return info != null && info.isInModuleSource && myModule.equals(info.module);
    }
    else {
      VirtualFile parent = fileOrDir.getParent();
      if (parent == null) return false;
      return isInSourceContent(parent);
    }
  }

  @NotNull
  public List<OrderEntry> getOrderEntriesForFile(@NotNull VirtualFile fileOrDir) {
    VirtualFile dir = fileOrDir.isDirectory() ? fileOrDir : fileOrDir.getParent();
    if (dir == null) return Collections.emptyList();
    final DirectoryInfo info = myDirectoryIndex.getInfoForDirectory(dir);
    if (info == null) return Collections.emptyList();
    final Collection<OrderEntry> orderEntries = info.getOrderEntries();
    List<OrderEntry> result = new LinkedList<OrderEntry>();
    for (OrderEntry orderEntry : orderEntries) {
      if (orderEntry.getOwnerModule() == myModule) {
        result.add(orderEntry);
      }
    }
    return Collections.unmodifiableList(result);
  }

  public OrderEntry getOrderEntryForFile(@NotNull VirtualFile fileOrDir) {
    VirtualFile dir = fileOrDir.isDirectory() ? fileOrDir : fileOrDir.getParent();
    if (dir == null) return null;
    final DirectoryInfo info = myDirectoryIndex.getInfoForDirectory(dir);
    if (info == null) return null;
    final Collection<OrderEntry> orderEntries = info.getOrderEntries();
    for (OrderEntry orderEntry : orderEntries) {
      if (orderEntry.getOwnerModule() == myModule) return orderEntry;
    }
    return null;
  }

  public boolean isInTestSourceContent(@NotNull VirtualFile fileOrDir) {
    if (fileOrDir.isDirectory()) {
      DirectoryInfo info = myDirectoryIndex.getInfoForDirectory(fileOrDir);
      return info != null && info.isInModuleSource && info.isTestSource && myModule.equals(info.module);
    }
    else {
      VirtualFile parent = fileOrDir.getParent();
      if (parent == null) return false;
      return isInTestSourceContent(parent);
    }
  }

  public VirtualFile[] getDirectoriesByPackageName(@NotNull String packageName, boolean includeLibrarySources) {
    VirtualFile[] allDirs = myDirectoryIndex.getDirectoriesByPackageName(packageName, includeLibrarySources);
    if (allDirs.length == 0) return allDirs;

    ArrayList<VirtualFile> list = new ArrayList<VirtualFile>();
    for (VirtualFile dir : allDirs) {
      if (getOrderEntryForFile(dir) != null) {
        list.add(dir);
      }
    }
    return list.toArray(new VirtualFile[list.size()]);
  }

  private class ContentFilter implements VirtualFileFilter {
    public boolean accept(@NotNull VirtualFile file) {
      if (file.isDirectory()) {
        DirectoryInfo info = myDirectoryIndex.getInfoForDirectory(file);
        if (info == null) return false;
        return myModule.equals(info.module);
      }
      else {
        return !myFileTypeManager.isFileIgnored(file.getName());
      }
    }
  }
}
