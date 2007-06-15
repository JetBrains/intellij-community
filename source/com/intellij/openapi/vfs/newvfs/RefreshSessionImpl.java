/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.ide.startup.FileSystemSynchronizer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsBundle;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.persistent.RefreshWorker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

public class RefreshSessionImpl extends RefreshSession {
  private final boolean myIsAsync;
  private final boolean myIsRecursive;
  private final Runnable myFinishRunnable;
  private final List<CacheUpdater> myRefreshParticipants;
  
  private List<VirtualFile> myWorkQueue = new ArrayList<VirtualFile>();
  private List<VFileEvent> myEvents = new ArrayList<VFileEvent>();

  public RefreshSessionImpl(final boolean isAsync, boolean reqursively,
                            final Runnable finishRunnable,
                            final List<CacheUpdater> refreshParticipants) {
    myRefreshParticipants = refreshParticipants;
    myIsRecursive = reqursively;
    myFinishRunnable = finishRunnable;
    myIsAsync = isAsync;
  }

  public RefreshSessionImpl(final List<VFileEvent> events, final List<CacheUpdater> refreshParticipants) {
    myIsAsync = false;
    myIsRecursive = false;
    myFinishRunnable = null;
    myEvents = new ArrayList<VFileEvent>(events);
    myRefreshParticipants = refreshParticipants;
  }

  public void addAllFiles(final Collection<VirtualFile> files) {
    myWorkQueue.addAll(files);
  }

  public void addFile(final VirtualFile file) {
    myWorkQueue.add(file);
  }

  public boolean isAsynchronous() {
    return myIsAsync;
  }

  public void launch() {
    ((RefreshQueueImpl)RefreshQueue.getInstance()).execute(this);
  }

  public void scan() {
    // TODO: indicator in the status bar...
    List<VirtualFile> workQueue = myWorkQueue;
    myWorkQueue = new ArrayList<VirtualFile>();

    if (!workQueue.isEmpty()) {
      ((LocalFileSystemImpl)LocalFileSystem.getInstance()).markSuspicousFilesDirty(workQueue);

      for (VirtualFile file : workQueue) {
        final NewVirtualFile nvf = (NewVirtualFile)file;
        if (!myIsAsync && !myIsRecursive) { // We're unable to definitely refresh synchronously by means of file watcher.
          nvf.markDirty();
        }

        RefreshWorker worker = new RefreshWorker(file, myIsRecursive);
        worker.scan();
        myEvents.addAll(worker.getEvents());
      }
    }
  }

  public void fireEvents() {
    if (myEvents.isEmpty() && myFinishRunnable == null) return;

    final VirtualFileManagerEx manager = (VirtualFileManagerEx)VirtualFileManager.getInstance();
    manager.fireBeforeRefreshStart(myIsAsync);

    while (!myWorkQueue.isEmpty() || !myEvents.isEmpty()) {
      ManagingFS.getInstance().processEvents(mergeEventsAndReset());

      scan();
    }

    manager.fireAfterRefreshFinish(myIsAsync);

    if (myFinishRunnable != null) {
      myFinishRunnable.run();
    }

    notifyCacheUpdaters();
  }

  private List<VFileEvent> mergeEventsAndReset() {
    LinkedHashSet<VFileEvent> mergedEvents = new LinkedHashSet<VFileEvent>(myEvents);
    final List<VFileEvent> events = new ArrayList<VFileEvent>();
    for (VFileEvent event : mergedEvents) {
      events.add(event);
    }
    myEvents = new ArrayList<VFileEvent>();
    return events;
  }

  private void notifyCacheUpdaters() {
    final FileSystemSynchronizer synchronizer = new FileSystemSynchronizer();
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < myRefreshParticipants.size(); i++) {
      CacheUpdater participant = myRefreshParticipants.get(i);
      synchronizer.registerCacheUpdater(participant);
    }

    int filesCount = synchronizer.collectFilesToUpdate();
    if (filesCount > 0) {
      boolean runWithProgress = !ApplicationManager.getApplication().isUnitTestMode() && filesCount > 5;
      if (runWithProgress) {
        Runnable process = new Runnable() {
          public void run() {
            synchronizer.execute();
          }
        };
        ProgressManager.getInstance()
          .runProcessWithProgressSynchronously(process, VfsBundle.message("file.update.modified.progress"), false, null);
      }
      else {
        synchronizer.execute();
      }
    }
  }
}
