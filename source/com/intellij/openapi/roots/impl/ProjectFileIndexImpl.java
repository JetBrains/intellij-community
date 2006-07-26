package com.intellij.openapi.roots.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.openapi.editor.impl.injected.VirtualFileDelegate;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class ProjectFileIndexImpl implements ProjectFileIndex {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.ProjectFileIndexImpl");

  private final Project myProject;
  private final FileTypeManager myFileTypeManager;
  private final DirectoryIndex myDirectoryIndex;
  private final ContentFilter myContentFilter;

  public ProjectFileIndexImpl(Project project, DirectoryIndex directoryIndex, FileTypeManager fileTypeManager) {
    myProject = project;

    myDirectoryIndex = directoryIndex;
    myFileTypeManager = fileTypeManager;
    myContentFilter = new ContentFilter();
  }

  public boolean iterateContent(@NotNull ContentIterator iterator) {
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
      for (VirtualFile contentRoot : contentRoots) {
        DirectoryInfo info = myDirectoryIndex.getInfoForDirectory(contentRoot);
        if (info == null) continue; // is excluded or ignored
        if (!module.equals(info.module)) continue; // maybe 2 modules have the same content root?

        VirtualFile parent = contentRoot.getParent();
        if (parent != null) {
          DirectoryInfo parentInfo = myDirectoryIndex.getInfoForDirectory(parent);
          if (parentInfo != null && parentInfo.module != null) continue; // inner content - skip it
        }

        boolean finished = FileIndexImplUtil.iterateRecursively(contentRoot, myContentFilter, iterator);
        if (!finished) return false;
      }
    }

    return true;
  }

  public boolean iterateContentUnderDirectory(@NotNull VirtualFile dir, @NotNull ContentIterator iterator) {
    return FileIndexImplUtil.iterateRecursively(dir, myContentFilter, iterator);
  }

  public boolean isIgnored(@NotNull VirtualFile file) {
    if (myFileTypeManager.isFileIgnored(file.getName())) return true;
    VirtualFile dir = file.isDirectory() ? file : file.getParent();
    if (dir == null) return false;

    DirectoryInfo info = myDirectoryIndex.getInfoForDirectory(dir);
    if (info != null) return false;

    VirtualFile parent = dir.getParent();
    while (true) {
      if (parent == null) return false;
      DirectoryInfo parentInfo = myDirectoryIndex.getInfoForDirectory(parent);
      if (parentInfo != null) return true;
      parent = parent.getParent();
    }
  }

  public Module getModuleForFile(@NotNull VirtualFile file) {
    if (file instanceof VirtualFileDelegate) file = ((VirtualFileDelegate)file).getDelegate();
    VirtualFile dir = file.isDirectory() ? file : file.getParent();
    if (dir == null) return null;
    DirectoryIndex directoryIndex = myDirectoryIndex;
    DirectoryInfo info = directoryIndex.getInfoForDirectory(dir);
    if (info == null) return null;
    return info.module;
  }

  public VirtualFile[] getDirectoriesByPackageName(@NotNull String packageName, boolean includeLibrarySources) {
    return getDirsByPackageName(packageName, includeLibrarySources).toArray(VirtualFile.EMPTY_ARRAY);
  }

  public Query<VirtualFile> getDirsByPackageName(@NotNull String packageName, boolean includeLibrarySources) {
    return myDirectoryIndex.getDirectoriesByPackageName(packageName, includeLibrarySources);
  }

  @NotNull
  public List<OrderEntry> getOrderEntriesForFile(@NotNull VirtualFile file) {
    VirtualFile dir = file.isDirectory() ? file : file.getParent();
    if (dir == null) return Collections.emptyList();
    DirectoryIndex directoryIndex = myDirectoryIndex;
    final DirectoryInfo info = directoryIndex.getInfoForDirectory(dir);
    if (info == null) return Collections.emptyList();
    return Collections.unmodifiableList(info.getOrderEntries());
  }

  public VirtualFile getClassRootForFile(@NotNull VirtualFile file) {
    VirtualFile dir = file.isDirectory() ? file : file.getParent();
    if (dir == null) return null;
    final DirectoryInfo info = myDirectoryIndex.getInfoForDirectory(dir);
    if (info == null) return null;
    return info.libraryClassRoot;
  }

  public VirtualFile getSourceRootForFile(@NotNull VirtualFile file) {
    final VirtualFile dir = file.isDirectory() ? file : file.getParent();
    if (dir == null) return null;
    final DirectoryInfo info = myDirectoryIndex.getInfoForDirectory(dir);
    if (info == null) return null;
    return info.sourceRoot;
  }

  public VirtualFile getContentRootForFile(@NotNull VirtualFile file) {
    VirtualFile dir = file.isDirectory() ? file : file.getParent();
    if (dir == null) return null;
    final DirectoryInfo info = myDirectoryIndex.getInfoForDirectory(dir);
    if (info == null) return null;
    return info.contentRoot;
  }

  public String getPackageNameByDirectory(@NotNull VirtualFile dir) {
    LOG.assertTrue(dir.isDirectory());
    DirectoryInfo info = myDirectoryIndex.getInfoForDirectory(dir);
    if (info == null) return null;
    return info.packageName;
  }

  public boolean isJavaSourceFile(@NotNull VirtualFile file) {
    if (file.isDirectory()) return false;
    if (myFileTypeManager.getFileTypeByFile(file) != StdFileTypes.JAVA) return false;
    if (myFileTypeManager.isFileIgnored(file.getName())) return false;
    return isInSource(file);
  }

  public boolean isContentJavaSourceFile(@NotNull VirtualFile file) {
    if (file.isDirectory()) return false;
    if (myFileTypeManager.getFileTypeByFile(file) != StdFileTypes.JAVA) return false;
    if (myFileTypeManager.isFileIgnored(file.getName())) return false;
    return isInSourceContent(file);
  }

  public boolean isLibraryClassFile(@NotNull VirtualFile file) {
    if (file.isDirectory()) return false;
    if (myFileTypeManager.getFileTypeByFile(file) != StdFileTypes.CLASS) return false;
    if (myFileTypeManager.isFileIgnored(file.getName())) return false;
    VirtualFile parent = file.getParent();
    DirectoryInfo parentInfo = myDirectoryIndex.getInfoForDirectory(parent);
    if (parentInfo == null) return false;
    return parentInfo.libraryClassRoot != null;
  }

  public boolean isInSource(@NotNull VirtualFile fileOrDir) {
    if (fileOrDir.isDirectory()) {
      DirectoryInfo info = myDirectoryIndex.getInfoForDirectory(fileOrDir);
      if (info == null) return false;
      return info.isInModuleSource || info.isInLibrarySource;
    }
    else {
      VirtualFile parent = fileOrDir.getParent();
      return parent != null && isInSource(parent);
    }
  }

  public boolean isInLibraryClasses(@NotNull VirtualFile fileOrDir) {
    if (fileOrDir.isDirectory()) {
      DirectoryInfo info = myDirectoryIndex.getInfoForDirectory(fileOrDir);
      if (info == null) return false;
      return info.libraryClassRoot != null;
    }
    else {
      VirtualFile parent = fileOrDir.getParent();
      return parent != null && isInLibraryClasses(parent);
    }
  }

  public boolean isInLibrarySource(@NotNull VirtualFile fileOrDir) {
    if (fileOrDir.isDirectory()) {
      DirectoryInfo info = myDirectoryIndex.getInfoForDirectory(fileOrDir);
      if (info == null) return false;
      return info.isInLibrarySource;
    }
    else {
      VirtualFile parent = fileOrDir.getParent();
      return parent != null && isInLibrarySource(parent);
    }
  }

  public boolean isInContent(@NotNull VirtualFile fileOrDir) {
    if (fileOrDir.isDirectory()) {
      DirectoryInfo info = myDirectoryIndex.getInfoForDirectory(fileOrDir);
      return info != null && info.module != null;
    }
    else {
      VirtualFile parent = fileOrDir.getParent();
      return parent != null && isInContent(parent);
    }
  }

  public boolean isInSourceContent(@NotNull VirtualFile fileOrDir) {
    if (fileOrDir.isDirectory()) {
      DirectoryInfo info = myDirectoryIndex.getInfoForDirectory(fileOrDir);
      return info != null && info.isInModuleSource;
    }
    else {
      VirtualFile parent = fileOrDir.getParent();
      return parent != null && isInSourceContent(parent);
    }
  }

  public boolean isInTestSourceContent(@NotNull VirtualFile fileOrDir) {
    if (fileOrDir.isDirectory()) {
      DirectoryInfo info = myDirectoryIndex.getInfoForDirectory(fileOrDir);
      return info != null && info.isInModuleSource && info.isTestSource;
    }
    else {
      VirtualFile parent = fileOrDir.getParent();
      return parent != null && isInTestSourceContent(parent);
    }
  }

  private class ContentFilter implements VirtualFileFilter {
    public boolean accept(@NotNull VirtualFile file) {
      if (file.isDirectory()) {
        DirectoryInfo info = myDirectoryIndex.getInfoForDirectory(file);
        if (info == null) return false;
        return info.module != null;
      }
      else {
        return !myFileTypeManager.isFileIgnored(file.getName());
      }
    }
  }
}
