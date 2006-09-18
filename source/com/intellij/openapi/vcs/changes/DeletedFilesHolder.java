package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vcs.FilePath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author max
 */
public class DeletedFilesHolder {
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
}
