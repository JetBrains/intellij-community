/*
 * @author max
 */
package com.intellij.psi.impl.search;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

public class JavaSourceFilterScope extends GlobalSearchScope {
  private final GlobalSearchScope myDelegate;
  private final ProjectFileIndex myIndex;

  public JavaSourceFilterScope(final GlobalSearchScope delegate, final Project project) {
    myDelegate = delegate;
    myIndex = ProjectRootManager.getInstance(project).getFileIndex();
  }

  public boolean contains(final VirtualFile file) {
    final FileType fileType = file.getFileType();
    return (myDelegate == null || myDelegate.contains(file)) &&
           (StdFileTypes.JAVA == fileType && myIndex.isInSourceContent(file) ||
            StdFileTypes.CLASS == fileType && myIndex.isInLibraryClasses(file));
  }

  public int compare(final VirtualFile file1, final VirtualFile file2) {
    return myDelegate != null ? myDelegate.compare(file1, file2) : 0;
  }

  public boolean isSearchInModuleContent(@NotNull final Module aModule) {
    return myDelegate == null || myDelegate.isSearchInModuleContent(aModule);
  }

  public boolean isSearchInLibraries() {
    return myDelegate == null || myDelegate.isSearchInLibraries();
  }
}