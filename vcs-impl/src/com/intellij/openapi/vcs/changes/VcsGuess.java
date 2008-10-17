package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.impl.ExcludedFileIndex;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

class VcsGuess {
  private final Project myProject;
  private final ProjectLevelVcsManagerImpl myVcsManager;
  private final ExcludedFileIndex myExcludedFileIndex;

  VcsGuess(final Project project) {
    myProject = project;
    myVcsManager = (ProjectLevelVcsManagerImpl) ProjectLevelVcsManagerImpl.getInstance(myProject);
    myExcludedFileIndex = ExcludedFileIndex.getInstance(myProject);
  }

  @Nullable
  AbstractVcs getVcsForDirty(final VirtualFile file) {
    if (!file.isInLocalFileSystem()) {
      return null;
    }
    if (myExcludedFileIndex.isInContent(file) || isFileInBaseDir(file) ||
        myVcsManager.hasExplicitMapping(file)) {
      if (myExcludedFileIndex.isExcludedFile(file)) {
        return null;
      }
      return myVcsManager.getVcsFor(file);
    }
    return null;
  }

  @Nullable
  AbstractVcs getVcsForDirty(final FilePath filePath) {
    if (filePath.isNonLocal()) {
      return null;
    }
    final VirtualFile validParent = ChangesUtil.findValidParent(filePath);
    if (validParent == null) {
      return null;
    }
    if (myExcludedFileIndex.isInContent(validParent) || isFileInBaseDir(filePath) ||
        myVcsManager.hasExplicitMapping(filePath)) {
      if (myExcludedFileIndex.isExcludedFile(validParent)) {
        return null;
      }
      return myVcsManager.getVcsFor(validParent);
    }
    return null;
  }

  private boolean isFileInBaseDir(final VirtualFile file) {
    VirtualFile parent = file.getParent();
    return !file.isDirectory() && parent != null && parent.equals(myProject.getBaseDir());
  }

  private boolean isFileInBaseDir(final FilePath filePath) {
    final VirtualFile parent = filePath.getVirtualFileParent();
    return !filePath.isDirectory() && parent != null && parent.equals(myProject.getBaseDir());
  }
}
