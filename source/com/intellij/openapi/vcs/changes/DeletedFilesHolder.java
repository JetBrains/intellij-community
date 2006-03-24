package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.peer.PeerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author max
 */
public class DeletedFilesHolder {
  private List<File> myFiles = new ArrayList<File>();

  public synchronized void cleanScope(final VcsDirtyScope scope) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final List<File> currentFiles = new ArrayList<File>(myFiles);
        for (File file : currentFiles) {
          if (scope.belongsTo(PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(file))) {
            myFiles.remove(file);
          }
        }
      }
    });
  }

  public synchronized void addFile(File file) {
    myFiles.add(file);
  }

  public synchronized List<File> getFiles() {
    return Collections.unmodifiableList(myFiles);
  }

  public synchronized DeletedFilesHolder copy() {
    final DeletedFilesHolder copyHolder = new DeletedFilesHolder();
    copyHolder.myFiles.addAll(myFiles);
    return copyHolder;
  }
}
