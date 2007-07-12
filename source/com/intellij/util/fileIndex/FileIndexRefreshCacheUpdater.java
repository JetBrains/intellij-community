/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.fileIndex;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.ide.startup.FileContent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;

import java.util.HashSet;
import java.util.Set;

/**
 * @author nik
 */
public class FileIndexRefreshCacheUpdater extends VirtualFileAdapter implements CacheUpdater, VirtualFileManagerListener, Disposable {
  private AbstractFileIndex myFileIndex;
  private VirtualFileManagerEx myVirtualFileManager;
  private Set<VirtualFile> myChangedFiles = new HashSet<VirtualFile>();
  private Set<VirtualFile> myRemovedFiles = new HashSet<VirtualFile>();

  public FileIndexRefreshCacheUpdater(final AbstractFileIndex fileIndex) {
    myFileIndex = fileIndex;
    myVirtualFileManager = (VirtualFileManagerEx)VirtualFileManagerEx.getInstance();
    myVirtualFileManager.addVirtualFileManagerListener(this);
    myVirtualFileManager.addVirtualFileListener(this);
    myVirtualFileManager.registerRefreshUpdater(this);
  }

  public void dispose() {
    myVirtualFileManager.removeVirtualFileManagerListener(this);
    myVirtualFileManager.removeVirtualFileListener(this);
    myVirtualFileManager.unregisterRefreshUpdater(this);
  }

  public void beforeRefreshStart(boolean asynchonous) {
  }

  public void afterRefreshFinish(boolean asynchonous) {
    if (!asynchonous) {
      for (VirtualFile file : myChangedFiles) {
        myFileIndex.updateIndexEntry(file);
      }
      updatingDone();
    }
  }

  public VirtualFile[] queryNeededFiles() {
    return myChangedFiles.toArray(new VirtualFile[myChangedFiles.size()]);
  }

  public void processFile(FileContent fileContent) {
    myFileIndex.updateIndexEntry(fileContent.getVirtualFile());
  }

  public void updatingDone() {
    myChangedFiles.clear();
    for (VirtualFile file : myRemovedFiles) {
      myFileIndex.removeIndexEntry(file);
    }
    myRemovedFiles.clear();
  }

  public void canceled() {
  }

  public void fileCreated(VirtualFileEvent event) {
    final VirtualFile file = event.getFile();
    handleCreateDeleteFile(file, event.isFromRefresh(), true);
  }

  public void contentsChanged(VirtualFileEvent event) {
    final VirtualFile file = event.getFile();
    if (myFileIndex.belongs(file)) {
      if (event.isFromRefresh()) {
        myChangedFiles.add(file);
      }
      else {
        myFileIndex.queueEntryUpdate(file);
      }
    }
  }

  private void handleCreateDeleteFile(final VirtualFile file, final boolean fromRefresh, final boolean create) {
    if (file.isDirectory()) {
      final VirtualFile[] children = file.getChildren();
      for (VirtualFile child : children) {
        handleCreateDeleteFile(child, fromRefresh, create);
      }
      return;
    }

    if (myFileIndex.belongs(file)) {
      if (fromRefresh) {
        if (create) {
          myChangedFiles.add(file);
        }
        else {
          myRemovedFiles.add(file);
        }
      }
      else {
        if (create) {
          myFileIndex.updateIndexEntry(file);
        }
        else {
          myFileIndex.removeIndexEntry(file);
        }
      }
    }
  }

  public void beforePropertyChange(VirtualFilePropertyEvent event) {
    if (VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
      beforeFileDeletion(event);
    }
  }

  public void beforeFileMovement(VirtualFileMoveEvent event) {
    beforeFileDeletion(event);
  }


  public void fileMoved(VirtualFileMoveEvent event) {
    fileCreated(event);
  }

  public void propertyChanged(VirtualFilePropertyEvent event) {
    final VirtualFile file = event.getFile();
    if (VirtualFile.PROP_NAME.equals(event.getPropertyName()) && myFileIndex.belongs(file)) {
      fileCreated(event);
    }
  }

  public void beforeFileDeletion(VirtualFileEvent event) {
    final VirtualFile file = event.getFile();
    handleCreateDeleteFile(file, event.isFromRefresh(), false);
  }
}
