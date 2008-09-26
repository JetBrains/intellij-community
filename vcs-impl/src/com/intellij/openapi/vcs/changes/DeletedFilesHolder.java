package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vcs.FilePath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author max
 */
public class DeletedFilesHolder implements FileHolder {
  private List<FilePath> myFiles = new ArrayList<FilePath>();

  public synchronized void cleanAll() {
    myFiles.clear();
  }

  public synchronized void cleanScope(final VcsDirtyScope scope) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final List<FilePath> currentFiles = new ArrayList<FilePath>(myFiles);
        for (FilePath path : currentFiles) {
          if (scope.belongsTo(path)) {
            myFiles.remove(path);
          }
        }
      }
    });
  }

  public HolderType getType() {
    return HolderType.DELETED;
  }

  public synchronized void addFile(FilePath file) {
    myFiles.add(file);
  }

  public synchronized List<FilePath> getFiles() {
    return Collections.unmodifiableList(myFiles);
  }

  public synchronized DeletedFilesHolder copy() {
    final DeletedFilesHolder copyHolder = new DeletedFilesHolder();
    copyHolder.myFiles.addAll(myFiles);
    return copyHolder;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final DeletedFilesHolder that = (DeletedFilesHolder)o;

    if (!myFiles.equals(that.myFiles)) return false;

    return true;
  }

  public int hashCode() {
    return myFiles.hashCode();
  }
}
