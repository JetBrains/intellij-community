/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.VfsBundle;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.util.ConcurrencyUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class RefreshQueueImpl extends RefreshQueue {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.newvfs.RefreshQueueImpl");

  private final ExecutorService myQueue = ConcurrencyUtil.newSingleThreadExecutor("FS Synchronizer");
  private final ProgressIndicator myRefreshIndicator = new RefreshProgress(VfsBundle.message("file.synchronize.progress"));

  private final List<CacheUpdater> myRefreshParticipants = new ArrayList<CacheUpdater>();

  public void execute(final RefreshSessionImpl session) {
    if (session.isAsynchronous()) {
      queueSession(session, ModalityState.NON_MODAL);
    }
    else {
      final Application app = ApplicationManager.getApplication();
      boolean isEDT = app.isDispatchThread();
      if (isEDT) {
        session.scan();
        session.fireEvents();
      }
      else {
        if (app.isReadAccessAllowed()) {
          LOG.error("Do not call synchronous refresh from inside read action except for event dispatch thread. This will eventually cause deadlock if there are events to fire");
          return;
        }

        queueSession(session, ModalityState.defaultModalityState());
        session.waitFor();
      }
    }
  }

  private void queueSession(final RefreshSessionImpl session, final ModalityState modality) {
    myQueue.submit(new Runnable() {
      public void run() {
        try {
          myRefreshIndicator.start();

          try {
            session.scan();
          }
          finally {
            myRefreshIndicator.stop();
          }
        }
        finally {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                public void run() {
                  session.fireEvents();
                }
              });
            }
          }, modality);
        }
      }
    });
  }

  public RefreshSession createSession(final boolean async, boolean recursively, @Nullable final Runnable finishRunnable) {
    return new RefreshSessionImpl(async, recursively, finishRunnable, myRefreshParticipants);
  }

  public void processSingleEvent(VFileEvent event) {
    RefreshSessionImpl session = new RefreshSessionImpl(Collections.singletonList(event), myRefreshParticipants);
    execute(session);
  }

  public void registerRefreshUpdater(final CacheUpdater updater) {
    myRefreshParticipants.add(updater);
  }

  public void unregisterRefreshUpdater(final CacheUpdater updater) {
    final boolean removed = myRefreshParticipants.remove(updater);
    LOG.assertTrue(removed, "Removing updater, which haven't been added or already removed");
  }
}