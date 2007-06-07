/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.ide.startup.FileSystemSynchronizer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.StatusBarProgress;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.persistent.RefreshRequest;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.vfs.local.win32.FileWatcher;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class RefreshQueueImpl extends RefreshQueue {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.newvfs.RefreshQueueImpl");
  private final Object LOCK = new Object();

  private final ExecutorService myQueue = ConcurrencyUtil.newSingleThreadExecutor("FS Synchronizer");
  private final ProgressIndicator myRefreshIndicator = new StatusBarProgress();
  private final List<CacheUpdater> myRefreshParticipants = new ArrayList<CacheUpdater>();
  private final List<RefreshResult> myAsyncRefreshResultsQueue = new ArrayList<RefreshResult>();

  private int myActiveRefreshCount = 0;

  private static class RefreshResult {
    private List<VFileEvent> myEvents;
    private Runnable myFinishRunnable;

    public RefreshResult(final List<VFileEvent> events, final Runnable finishRunnable) {
      myEvents = events;
      myFinishRunnable = finishRunnable;
    }

    public List<VFileEvent> getEvents() {
      return myEvents;
    }

    public Runnable getFinishRunnable() {
      return myFinishRunnable;
    }
  }

  public void refresh(final boolean async, final boolean recursive, @Nullable final Runnable finishRunnable, final VirtualFile... files) {
    final List<RefreshResult> resultsQueue = async ? myAsyncRefreshResultsQueue : new ArrayList<RefreshResult>();

    if (async) {
      startIndicator();
    }

    final Runnable task = new Runnable() {
      public void run() {
        ((LocalFileSystemImpl)LocalFileSystem.getInstance()).markSuspicousFilesDirty(files);
        try {
          for (final VirtualFile file : files) {
            final NewVirtualFile nvf = (NewVirtualFile)file;
            if (!async && !recursive) { // We're unable to definitely refresh synchronously by means of file watcher.
              nvf.markDirty();
            }

            final RefreshRequest request = new RefreshRequest(file, recursive);
            request.scan();

            final List<VFileEvent> events = request.getEvents();
            if (events.size() > 0) {
              synchronized (resultsQueue) {
                resultsQueue.add(new RefreshResult(events, null));
              }
            }
          }

          synchronized (resultsQueue) {
            resultsQueue.add(new RefreshResult(Collections.<VFileEvent>emptyList(), finishRunnable));
          }
        }
        finally {
          if (async) {
            stopInidcator();
          }
        }
      }
    };

    if (async) {
      myQueue.submit(task);
    }
    else {
      task.run();

      flushResultsQueue(resultsQueue, false);
    }
  }

  public void processSingleEvent(Runnable finishRunnable, VFileEvent event) {
    RefreshResult result = new RefreshResult(Collections.singletonList(event), finishRunnable);
    List<RefreshResult> queue = new ArrayList<RefreshResult>();
    queue.add(result);
    flushResultsQueue(queue, false);
  }

  private void stopInidcator() {
    synchronized (LOCK) {
      if (--myActiveRefreshCount == 0) {
        myRefreshIndicator.stop();
        myRefreshIndicator.setText("");

        flushResultsQueue(myAsyncRefreshResultsQueue, true);
      }
    }
  }

  private void flushResultsQueue(final List<RefreshResult> queue, final boolean async) {
    final Runnable worker = new Runnable() {
      public void run() {
        synchronized (queue) {
          final VirtualFileManagerEx manager = (VirtualFileManagerEx)VirtualFileManager.getInstance();
          manager.fireBeforeRefreshStart(async);

          for (RefreshResult result : queue) {
            ManagingFS.getInstance().processEvents(result.getEvents());
          }

          manager.fireAfterRefreshFinish(async);

          for (RefreshResult result : queue) {
            final Runnable finishRunnable = result.getFinishRunnable();
            if (finishRunnable != null) {
              finishRunnable.run();
            }
          }

          notifyCacheUpdaters();
          queue.clear();
        }
      }
    };

    if (async) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              worker.run();
            }
          });
        }
      }, ModalityState.NON_MODAL);
    }
    else {
      worker.run();
    }
  }

  private void startIndicator() {
    synchronized (LOCK) {
      if (myActiveRefreshCount++ == 0) {
        myRefreshIndicator.start();
        myRefreshIndicator.setText(VfsBundle.message("file.synchronize.progress"));
      }
    }
  }

  public void registerRefreshUpdater(final CacheUpdater updater) {
    myRefreshParticipants.add(updater);
  }

  public void unregisterRefreshUpdater(final CacheUpdater updater) {
    final boolean removed = myRefreshParticipants.remove(updater);
    LOG.assertTrue(removed, "Removing updater, which haven't been added or already removed");
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

  private static boolean fileWatcherIsEnabledForFile(final VirtualFile file) {
    if (file == null) return false;
    
    final String url = file.getUrl();
    if (url.startsWith("file://")) {
      return FileWatcher.isAvailable() && FileWatcher.watches(file.getPath());
    }

    if (url.startsWith(JarFileSystem.PROTOCOL_PREFIX)) {
      return true;
    }

    return false;
  }

  private static VirtualFile findLocalJarForEntryFile(final VirtualFile file) {
    return JarFileSystem.getInstance().getVirtualFileForJar(file);
  }
}