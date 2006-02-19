package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.peer.PeerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author max
 */
public class UnversionedFilesHolder {
  private List<VirtualFile> myFiles = new ArrayList<VirtualFile>();

  public synchronized void cleanScope(final VcsDirtyScope scope) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final List<VirtualFile> currentFiles = new ArrayList<VirtualFile>(myFiles);
        for (VirtualFile file : currentFiles) {
          if (!file.isValid() || scope.belongsTo(PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(file))) {
            myFiles.remove(file);
          }
        }
      }
    });
  }

  public synchronized void addFile(VirtualFile file) {
    myFiles.add(file);
  }

  public synchronized List<VirtualFile> getFiles() {
    return Collections.unmodifiableList(myFiles);
  }
}
