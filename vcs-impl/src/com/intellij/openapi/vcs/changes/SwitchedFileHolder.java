package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vfs.VirtualFile;

public class SwitchedFileHolder extends RecursiveFileHolder {
  public SwitchedFileHolder(Project project, HolderType holderType) {
    super(project, holderType);
  }

  public void takeFrom(final SwitchedFileHolder holder) {
    myFiles.clear();
    myFiles.putAll(holder.myFiles);

    if (mySwitchRoots != null) {
      mySwitchRoots.clear();
      mySwitchRoots.addAll(holder.mySwitchRoots);
    }
  }

  public SwitchedFileHolder copy() {
    final SwitchedFileHolder copyHolder = new SwitchedFileHolder(myProject, myHolderType);
    copyHolder.myFiles.putAll(myFiles);
    return copyHolder;
  }

  @Override
  protected boolean isFileDirty(final VcsDirtyScope scope, final VirtualFile file) {
    if (scope == null) return true;
    if (fileDropped(file)) return true;
    final VirtualFile parent = file.getParent();
    return (parent != null) && (scope.belongsTo(new FilePathImpl(parent)));
  }
}
