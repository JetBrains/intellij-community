package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * ChangeListManager updates scheduler.
 * Tries to zip several update requests into one (if starts and see several requests in the queue)
 * own inner synchronization
 */
public class UpdateRequestsQueue {
  private final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.UpdateRequestsQueue");
  private final Project myProject;
  private final ScheduledExecutorService myExecutor;
  private final Consumer<Boolean> myAction;
  private final Object myLock;
  private volatile boolean myStarted;
  private volatile boolean myStopped;

  private final LinkedList<ScheduledData> mySentRequests;
  private final List<Runnable> myWaitingUpdateCompletionQueue;
  private final ProjectLevelVcsManager myPlVcsManager;
  private boolean myUpdateUnversionedRequested;

  public UpdateRequestsQueue(final Project project, final ScheduledExecutorService executor, final Consumer<Boolean> action) {
    myExecutor = executor;
    myAction = action;
    myProject = project;
    myPlVcsManager = ProjectLevelVcsManager.getInstance(myProject);
    myLock = new Object();
    mySentRequests = new LinkedList<ScheduledData>();
    myWaitingUpdateCompletionQueue = new ArrayList<Runnable>();
    // not initialized
    myStarted = false;
    myStopped = false;
    myUpdateUnversionedRequested = false;
  }

  public void initialized() {
    LOG.debug("Initialized for project: " + myProject.getName());
    myStarted = true;
  }

  public boolean isStopped() {
    return myStopped;
  }

  public void schedule(final boolean updateUnversionedFiles) {
    synchronized (myLock) {
      if (! myStarted && ApplicationManager.getApplication().isUnitTestMode()) return;

      if (! myStopped) {
        final MyRunnable runnable = new MyRunnable();
        final ScheduledFuture<?> future = myExecutor.schedule(runnable, 300, TimeUnit.MILLISECONDS);
        final ScheduledData data = new ScheduledData(runnable, future);
        LOG.debug("Scheduled for project: " + myProject.getName() + ", runnable: " + runnable.hashCode());
        mySentRequests.add(data);
        myUpdateUnversionedRequested = updateUnversionedFiles;
      }
    }
  }

  public void stop() {
    LOG.debug("Calling stop for project: " + myProject.getName());
    final List<Runnable> waiters = new ArrayList<Runnable>(myWaitingUpdateCompletionQueue.size());
    synchronized (myLock) {
      myStopped = true;
      for (ScheduledData sentRequest : mySentRequests) {
        sentRequest.cancel();
      }
      mySentRequests.clear();
      waiters.addAll(myWaitingUpdateCompletionQueue);
      myWaitingUpdateCompletionQueue.clear();
    }
    LOG.debug("Calling runnables in stop for project: " + myProject.getName());
    // do not run under lock
    for (Runnable runnable : waiters) {
      runnable.run();
    }
    LOG.debug("Stop finished for project: " + myProject.getName());
  }

  public void invokeAfterUpdate(final Runnable afterUpdate, final boolean cancellable, final boolean silently, final String title) {
    LOG.debug("invokeAfterUpdate for project: " + myProject.getName());
    final Runnable runnable = createCallbackWrapperRunnable(afterUpdate, cancellable, silently, title);
    synchronized (myLock) {
      if (! myStopped) {
        myWaitingUpdateCompletionQueue.add(runnable);
        schedule(true);
      }
    }
    // do not run under lock
    if (myStopped) {
      LOG.debug("invokeAfterUpdate: stopped, invoke right now for project: " + myProject.getName());
      runnable.run();
      return;
    }
    LOG.debug("invokeAfterUpdate: exit for project: " + myProject.getName());
  }

  private Runnable createCallbackWrapperRunnable(final Runnable afterUpdate, final boolean cancellable, final boolean silently, final String title) {
    if (silently) {
      return new Runnable() {
        public void run() {
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              LOG.debug("invokeAfterUpdate: silent wrapper called for project: " + myProject.getName());
              if (myProject.isDisposed()) return;
              afterUpdate.run();
              ChangesViewManager.getInstance(myProject).refreshView();
            }
          });
        }
      };
    } else {
      final FictiveBackgroundable fictiveBackgroundable = new FictiveBackgroundable(myProject, afterUpdate, cancellable, title);
      ProgressManager.getInstance().run(fictiveBackgroundable);
      return new Runnable() {
        public void run() {
          LOG.debug("invokeAfterUpdate: NOT silent wrapper called for project: " + myProject.getName());
          fictiveBackgroundable.done();
        }
      };
    }
  }

  // checks whether request is actual (was not deleted from requests queue);
  // if actual, removes duplicate requests
  @Nullable
  private ScheduledData takeMeRemoveDuplicates(final Runnable key) {
    LOG.debug("takeMeRemoveDuplicates: start for project: " + myProject.getName() + ", runnable: " + key.hashCode());
    synchronized (myLock) {
      for (ScheduledData sentRequest : mySentRequests) {
        if (sentRequest.isMe(key)) {
          LOG.debug("takeMeRemoveDuplicates: FOUND project: " + myProject.getName() + ", runnable: " + key.hashCode());
          // remove duplicates, left "me" - for cancel
          mySentRequests.retainAll(Collections.singletonList(sentRequest));
          return sentRequest;
        }
      }
      LOG.debug("takeMeRemoveDuplicates: not found, project: " + myProject.getName() + ", runnable: " + key.hashCode());
      return null;
    }
  }

  private class MyRunnable implements Runnable {
    public void run() {
      final List<Runnable> copy = new ArrayList<Runnable>(myWaitingUpdateCompletionQueue.size());
      ScheduledData me = null;
      try {
        boolean updateUnversioned;
        synchronized (myLock) {
          if ((! myStopped) && ((! myStarted) || myPlVcsManager.isBackgroundVcsOperationRunning())) {
            LOG.debug("MyRunnable: not started, not stopped, reschedule, project: " + myProject.getName() + ", runnable: " + hashCode());
            mySentRequests.clear();
            // try again after time
            schedule(myUpdateUnversionedRequested);
            return;
          }
          if (myStopped) {
            LOG.debug("MyRunnable: STOPPED, project: " + myProject.getName() + ", runnable: " + hashCode());
            mySentRequests.clear();
            return;
          }
          me = takeMeRemoveDuplicates(this);
          if (me == null) {
            LOG.debug("MyRunnable: me == null, project: " + myProject.getName() + ", runnable: " + hashCode());
            return;
          }
          copy.addAll(myWaitingUpdateCompletionQueue);

          // take it under lock
          updateUnversioned = myUpdateUnversionedRequested;
          // for concurrent schedules to tigger flag correctly
          myUpdateUnversionedRequested = false;
        }
        LOG.debug("MyRunnable: INVOKE, project: " + myProject.getName() + ", runnable: " + hashCode());
        myAction.consume(updateUnversioned);
        LOG.debug("MyRunnable: invokeD, project: " + myProject.getName() + ", runnable: " + hashCode());
      } finally {
        if (me != null) {
          synchronized (myLock) {
            LOG.debug("MyRunnable: delete executed, project: " + myProject.getName() + ", runnable: " + hashCode());
            // remove "me"
            mySentRequests.remove(me);
            if (! copy.isEmpty()) {
              myWaitingUpdateCompletionQueue.removeAll(copy);
            }
          }
          // do not run under lock
          for (Runnable runnable : copy) {
            runnable.run();
          }
          LOG.debug("MyRunnable: Runnables executed, project: " + myProject.getName() + ", runnable: " + hashCode());
        }
      }
    }
  }

  private static class ScheduledData {
    private final Runnable myKey;
    private final ScheduledFuture<?> myFuture;

    private ScheduledData(final Runnable key, final ScheduledFuture<?> future) {
      myKey = key;
      myFuture = future;
    }

    public boolean isMe(final Runnable key) {
      return key == myKey;
    }

    public void cancel() {
      myFuture.cancel(false);
    }
  }
}
