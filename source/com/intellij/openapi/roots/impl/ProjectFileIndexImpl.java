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

import java.util.Set;

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

  public boolean iterateContent(ContentIterator iterator) {
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (int i = 0; i < modules.length; i++) {
      Module module = modules[i];

      VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
      for (int j = 0; j < contentRoots.length; j++) {
        VirtualFile contentRoot = contentRoots[j];
        DirectoryInfo info = myDirectoryIndex.getInfoForDirectory(contentRoot);
        if (info == null) continue; // is exluded or ignored
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

  public boolean iterateContentUnderDirectory(VirtualFile dir, ContentIterator iterator) {
    return FileIndexImplUtil.iterateRecursively(dir, myContentFilter, iterator);
  }

  public boolean isIgnored(VirtualFile file) {
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

  public Module getModuleForFile(VirtualFile file) {
    VirtualFile dir = file.isDirectory() ? file : file.getParent();
    if (dir == null) return null;
    DirectoryIndex directoryIndex = myDirectoryIndex;
    DirectoryInfo info = directoryIndex.getInfoForDirectory(dir);
    if (info == null) return null;
    return info.module;
  }

  public VirtualFile[] getDirectoriesByPackageName(String packageName, boolean includeLibrarySources) {
    return myDirectoryIndex.getDirectoriesByPackageName(packageName, includeLibrarySources);
  }

  public OrderEntry[] getOrderEntriesForFile(VirtualFile file) {
    VirtualFile dir = file.isDirectory() ? file : file.getParent();
    if (dir == null) return OrderEntry.EMPTY_ARRAY;
    DirectoryIndex directoryIndex = myDirectoryIndex;
    final DirectoryInfo info = directoryIndex.getInfoForDirectory(dir);
    if (info == null) return OrderEntry.EMPTY_ARRAY;
    final Set<OrderEntry> orderEntries = info.orderEntries;
    return orderEntries.toArray(new OrderEntry[orderEntries.size()]);
  }

  public VirtualFile getClassRootForFile(VirtualFile file) {
    VirtualFile dir = file.isDirectory() ? file : file.getParent();
    if (dir == null) return null;
    final DirectoryInfo info = myDirectoryIndex.getInfoForDirectory(dir);
    if (info == null) return null;
    return info.libraryClassRoot;
  }

  public VirtualFile getSourceRootForFile(VirtualFile file) {
    final VirtualFile dir = file.isDirectory() ? file : file.getParent();
    if (dir == null) return null;
    final DirectoryInfo info = myDirectoryIndex.getInfoForDirectory(dir);
    if (info == null) return null;
    return info.sourceRoot;
  }

  public VirtualFile getContentRootForFile(VirtualFile file) {
    VirtualFile dir = file.isDirectory() ? file : file.getParent();
    if (dir == null) return null;
    final DirectoryInfo info = myDirectoryIndex.getInfoForDirectory(dir);
    if (info == null) return null;
    return info.contentRoot;
  }

  public String getPackageNameByDirectory(VirtualFile dir) {
    LOG.assertTrue(dir.isDirectory());
    DirectoryInfo info = myDirectoryIndex.getInfoForDirectory(dir);
    if (info == null) return null;
    return info.packageName;
  }

  public boolean isJavaSourceFile(VirtualFile file) {
    if (file.isDirectory()) return false;
    if (myFileTypeManager.getFileTypeByFile(file) != StdFileTypes.JAVA) return false;
    if (myFileTypeManager.isFileIgnored(file.getName())) return false;
    return isInSource(file);
  }

  public boolean isContentJavaSourceFile(VirtualFile file) {
    if (file.isDirectory()) return false;
    if (myFileTypeManager.getFileTypeByFile(file) != StdFileTypes.JAVA) return false;
    if (myFileTypeManager.isFileIgnored(file.getName())) return false;
    return isInSourceContent(file);
  }

  public boolean isLibraryClassFile(VirtualFile file) {
    if (file.isDirectory()) return false;
    if (myFileTypeManager.getFileTypeByFile(file) != StdFileTypes.CLASS) return false;
    if (myFileTypeManager.isFileIgnored(file.getName())) return false;
    VirtualFile parent = file.getParent();
    DirectoryInfo parentInfo = myDirectoryIndex.getInfoForDirectory(parent);
    if (parentInfo == null) return false;
    return parentInfo.libraryClassRoot != null;
  }

  public boolean isInSource(VirtualFile fileOrDir) {
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

  public boolean isInLibraryClasses(VirtualFile fileOrDir) {
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

  public boolean isInLibrarySource(VirtualFile fileOrDir) {
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

  public boolean isInContent(VirtualFile fileOrDir) {
    if (fileOrDir.isDirectory()) {
      DirectoryInfo info = myDirectoryIndex.getInfoForDirectory(fileOrDir);
      return info != null && info.module != null;
    }
    else {
      VirtualFile parent = fileOrDir.getParent();
      return parent != null && isInContent(parent);
    }
  }

  public boolean isInSourceContent(VirtualFile fileOrDir) {
    if (fileOrDir.isDirectory()) {
      DirectoryInfo info = myDirectoryIndex.getInfoForDirectory(fileOrDir);
      return info != null && info.isInModuleSource;
    }
    else {
      VirtualFile parent = fileOrDir.getParent();
      return parent != null && isInSourceContent(parent);
    }
  }

  public boolean isInTestSourceContent(VirtualFile fileOrDir) {
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
    public boolean accept(VirtualFile file) {
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
