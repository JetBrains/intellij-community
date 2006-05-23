
package com.intellij.openapi.vfs.ex;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileManagerListener;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

public abstract class VirtualFileManagerEx extends VirtualFileManager {
  /**
   * @param asynchronous whether this indicator wiol be used by async refresh
   */
  @NotNull
  public abstract ProgressIndicator getRefreshIndicator(final boolean asynchronous);

  public abstract void beforeRefreshStart(boolean asynchronous, ModalityState modalityState, Runnable postAction);
  public abstract void afterRefreshFinish(boolean asynchronous, ModalityState modalityState);
  public abstract void addEventToFireByRefresh(Runnable action, boolean asynchronous, ModalityState modalityState);

  public abstract void registerFileContentProvider(FileContentProvider provider);
  public abstract void unregisterFileContentProvider(FileContentProvider provider);

  public abstract void registerRefreshUpdater(CacheUpdater updater);
  public abstract void unregisterRefreshUpdater(CacheUpdater updater);

  @Nullable
  public abstract ProvidedContent getProvidedContent(VirtualFile file);
}
