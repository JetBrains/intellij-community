
package com.intellij.openapi.vfs.ex;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;

public abstract class VirtualFileManagerEx extends VirtualFileManager {
  public abstract ProgressIndicator getRefreshIndicator();
  // Used by Fabrique:
  public abstract void setRefreshIndicator(ProgressIndicator refreshIndicator);

  public abstract void addVirtualFileManagerListener(VirtualFileManagerListener listener);
  public abstract void removeVirtualFileManagerListener(VirtualFileManagerListener listener);

  public abstract void beforeRefreshStart(boolean asynchronous, ModalityState modalityState, Runnable postAction);
  public abstract void afterRefreshFinish(boolean asynchronous, ModalityState modalityState);
  public abstract void addEventToFireByRefresh(Runnable action, boolean asynchronous, ModalityState modalityState);

  public abstract void registerFileContentProvider(FileContentProvider provider);
  public abstract void unregisterFileContentProvider(FileContentProvider provider);

  public abstract void registerRefreshUpdater(CacheUpdater updater);
  public abstract void unregisterRefreshUpdater(CacheUpdater updater);

  public abstract ProvidedContent getProvidedContent(VirtualFile file);
}